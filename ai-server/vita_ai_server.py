#!/usr/bin/env python3
"""
Vita AI 分析服务 (跑在 charlotte 的 Mac 上)。

- 接收 Vita app 通过家庭 Wi-Fi 发来的聚合健康数据 (POST /analyze)
- 调用本机已登录的 CLI 做跨维度深度分析, 双后端任选其一:
    * claude (Claude Code, `claude -p`) → 走 Claude 订阅的 Agent SDK 月度额度
    * codex  (OpenAI Codex CLI, `codex exec`) → 走 ChatGPT 订阅的 Codex 用量
  两边都不需要 API key、不额外花钱; app 端不感知后端是谁。
- 返回结构化 JSON 洞察 {overall, longTermFindings, recentPatterns, actions, watch}
- 默认只监听本机; 如需手机同 Wi-Fi 访问, 必须显式开启局域网监听并设置口令

依赖: 仅 Python3 标准库 + 本机 claude 或 codex 命令。运行: python3 vita_ai_server.py
环境变量:
  VITA_AI_BACKEND  auto(默认: 有 claude 用 claude, 否则 codex) / claude / codex
  VITA_AI_PORT     默认 8787
  VITA_AI_ALLOW_LAN 设为 1/true/on 时监听局域网; 不设则只监听 127.0.0.1
  VITA_AI_TOKEN    局域网访问口令; 开启 VITA_AI_ALLOW_LAN 时必填
  VITA_AI_MODEL    claude 后端的 --model (默认用 claude 自身默认)
  VITA_CODEX_MODEL codex 后端的 -m (默认用 codex 自身默认)
  VITA_CLAUDE_BIN / VITA_CODEX_BIN  命令完整路径 (默认自动查找)
"""

import hmac
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("VITA_AI_PORT", "8787"))
BACKEND = os.environ.get("VITA_AI_BACKEND", "auto").strip().lower()
CLAUDE_MODEL = os.environ.get("VITA_AI_MODEL", "").strip()
CODEX_MODEL = os.environ.get("VITA_CODEX_MODEL", "").strip()
ALLOW_LAN = os.environ.get("VITA_AI_ALLOW_LAN", "").strip().lower() in {"1", "true", "yes", "on"}
AUTH_TOKEN = os.environ.get("VITA_AI_TOKEN", "").strip()
CLI_TIMEOUT_SECONDS = 540

FIELD_GUIDE = (
    "longTerm=近十年压缩摘要(from/to/trackedDays, years逐年聚合, shifts相邻年份最大变化段; "
    "year字段: y年份, days有效日, sleepNights主睡眠晚数, sleepMin平均睡眠分钟, steps日均步数, "
    "activeMin日均活跃分钟, rhr静息心率, hrv心率变异性, stress压力, bbHigh身体电量峰值, "
    "spo2血氧, weight体重, exerciseCount/Min/Km运动次数/时长/距离, cycleStarts周期起始次数); "
    "daily=每日聚合指标(steps步数, km距离公里, kcal活动消耗, avgHr平均心率, rhr静息心率, "
    "hrv心率变异性ms, stress全天平均压力0-100, bbHigh/bbLow身体电量高低点, spo2血氧%, "
    "resp呼吸率, weight体重kg); "
    "sleeps=每晚主睡眠(d醒来日期, start入睡时刻, min总分钟, deep/light/rem/awake各阶段分钟, score睡眠评分0-100); "
    "exercises=运动会话(type类型, min时长, km距离, kcal消耗, avgHr平均心率, te训练效果); "
    "cycle=月经记录(flow经量0=无1=点滴2=轻3=中4=重, start是否周期起始日, symptoms症状); "
    "moods=每日情绪(mood当天主导情绪名, valence当天平均情绪价1-5越低整体越低落, "
    "moments当天各时刻[t时刻/mood情绪名/note用户备注], 一天可多条, note是用户亲手写下的处境或缘由); "
    "habits=习惯及其打卡日期; "
    "windowDays=用户本次选择的近期明细窗口天数"
)

PROMPT_TEMPLATE = """你是一位私人健康数据分析师。<data> 标签里是一位女性用户的可穿戴设备与生活记录 (JSON): longTerm 是多年压缩摘要, daily/sleeps/exercises/cycle/moods/habits 是近期细节。字段说明: {field_guide}。

请做跨维度的深度分析, 找出真正稳定的模式, 而不是罗列数字。请同时看两层:
- 多年层: 用 longTerm 判断横跨数年的长期方向、明显拐点、近一年在多年里的相对位置; 不要把单年样本少的年份说成确定结论
- 近期层: 用 daily/sleeps/exercises/cycle/moods/habits 解释近期窗口 (windowDays 天) 内发生了什么, 以及它与多年趋势是否一致

区间规则 (重要): 用户可以自选分析区间。如果数据里没有 longTerm 字段, 说明用户只想分析近期窗口——此时 longTermFindings 必须返回空数组 [], 把全部分析放在 recentPatterns/actions/watch, 规律紧扣 windowDays 天内的记录, 不要臆测更早的历史。

分析目标:
- longTermFindings 写 2 到 3 条 (仅当有 longTerm 时), 讲多年变化、拐点、近一年在多年里的位置
- recentPatterns 写 6 到 8 条 (windowDays ≤ 120 的短窗口写 4 到 6 条, 证据不足不要硬凑), 讲近期窗口内反复出现的身体规律或关联
- longTermFindings + recentPatterns 合计控制在 8 到 11 条左右 (短窗口 4 到 6 条)
- 不必覆盖每一种字段, 也不要为了凑类别硬写; 选择数据里最有证据、最能解释用户身体运作方式的规律
- 务必区分长期趋势、短期波动、样本不足的猜测

写作要求:
- 简体中文, 直接对用户说「你」
- 讲身体发生了什么、对她意味着什么, 不讲算法和统计术语
- 数字只在支撑结论时少量引用
- 建议必须具体可执行, 贴合她的实际记录
- 每条规律都要说明「现象 + 对你意味着什么」, 不只描述数据
- 情绪里的 note 是用户亲手写下的处境, 分析情绪 / 压力相关规律时要结合这些原话, 不要只看情绪标签
- 不诊断疾病; 如有持续异常, 建议就医确认

只输出一个 JSON 对象, 不要任何其它文字、解释或代码围栏, 格式:
{{"overall": "3-5句总评", "longTermFindings": [{{"title": "短标题", "body": "2-4句"}}, ... 2到3条], "recentPatterns": [{{"title": "短标题", "body": "2-4句"}}, ... 6到8条], "actions": [{{"title": "短标题", "body": "1-3句"}}, ... 2到4条], "watch": ["一句话观察项", ... 0到3条]}}

<data>
{data_json}
</data>"""

# 「今日状态」快速分析 (payload.focus == "today", 来自今日页):
# 结合昨晚睡眠讲今天 + 近 3-7 天短期趋势。与 app 端 AiAnalysisPrompt.buildToday 保持一致。
TODAY_PROMPT_TEMPLATE = """你是一位私人健康数据分析师。<data> 标签里是一位女性用户最近两周的可穿戴设备与生活记录 (JSON), focus="today" 表示这是一次「今日状态」快速分析, generatedAt 是今天的日期。字段说明: {field_guide}。

请只聚焦两件事:
- 今日状态: 以昨晚睡眠为核心 (sleeps 里 d 等于今天的那条: 入睡时刻、总时长、深睡/REM、评分), 结合今天已有的静息心率/HRV/压力/身体电量和经期位置, 判断她今天身体处在什么状态、适合怎么安排强度
- 短期趋势: 对比近 3 天和近 7 天, 指出正在变化的走向 (睡眠节奏、恢复、运动负荷、情绪、经期影响), 分清单日波动和连续几天的走向; 更早的数据只作参照

写作要求:
- 简体中文, 直接对用户说「你」
- 讲身体发生了什么、对她今天意味着什么, 不讲算法和统计术语
- 数字只在支撑结论时少量引用
- 建议必须今天就能做、贴合她的实际记录
- 情绪 note 是用户自己写下的处境, 讲情绪时结合原话
- 不诊断疾病; 如有持续异常, 建议就医确认

内容规则:
- overall 用 2-3 句先给今天的结论: 今天状态如何、最该注意什么
- recentPatterns 写 2 到 4 条, 只讲近 3-7 天里真实出现的变化或规律; 证据不足不要硬凑
- actions 写 1 到 3 条今天就能执行的安排
- watch 0 到 2 条
- longTermFindings 必须返回空数组 []

只输出一个 JSON 对象, 不要任何其它文字、解释或代码围栏, 格式:
{{"overall": "2-3句今日结论", "longTermFindings": [], "recentPatterns": [{{"title": "短标题", "body": "1-3句"}}, ... 2到4条], "actions": [{{"title": "短标题", "body": "1-2句"}}, ... 1到3条], "watch": ["一句话观察项", ... 0到2条]}}

<data>
{data_json}
</data>"""


def find_bin(env_var: str, name: str) -> str | None:
    explicit = os.environ.get(env_var, "").strip()
    if explicit:
        return explicit if os.path.exists(explicit) else None
    found = shutil.which(name)
    if found:
        return found
    for candidate in (
        os.path.expanduser(f"~/.local/bin/{name}"),
        f"/opt/homebrew/bin/{name}",
        f"/usr/local/bin/{name}",
        os.path.expanduser(f"~/.claude/local/{name}"),
    ):
        if os.path.exists(candidate):
            return candidate
    return None


CLAUDE_BIN = find_bin("VITA_CLAUDE_BIN", "claude")
CODEX_BIN = find_bin("VITA_CODEX_BIN", "codex")


def bind_host() -> str:
    """默认不暴露到局域网; 手机访问必须显式开启。"""
    return "0.0.0.0" if ALLOW_LAN else "127.0.0.1"


def validate_security_config() -> None:
    if ALLOW_LAN and not AUTH_TOKEN:
        raise RuntimeError("VITA_AI_ALLOW_LAN=1 时必须同时设置 VITA_AI_TOKEN")
    if AUTH_TOKEN and len(AUTH_TOKEN) < 16:
        raise RuntimeError("VITA_AI_TOKEN 至少需要 16 个字符")


def is_authorized(headers) -> bool:
    if not AUTH_TOKEN:
        return True
    auth = headers.get("Authorization", "").strip()
    prefix = "Bearer "
    if not auth.startswith(prefix):
        return False
    return hmac.compare_digest(auth[len(prefix) :].strip(), AUTH_TOKEN)


def backend_candidates() -> list[tuple[str, str]]:
    """按尝试顺序返回 [(后端名, 命令路径)]。
    显式指定只用那一个; auto 按 claude→codex 顺序, 前一个失败自动换下一个
    (应对「停订了 Claude 但 claude 命令还装着」这类情况, charlotte 不确定长期订哪家)。"""
    if BACKEND == "claude":
        if not CLAUDE_BIN:
            raise RuntimeError("VITA_AI_BACKEND=claude 但找不到 claude 命令")
        return [("claude", CLAUDE_BIN)]
    if BACKEND == "codex":
        if not CODEX_BIN:
            raise RuntimeError("VITA_AI_BACKEND=codex 但找不到 codex 命令")
        return [("codex", CODEX_BIN)]
    candidates = []
    if CLAUDE_BIN:
        candidates.append(("claude", CLAUDE_BIN))
    if CODEX_BIN:
        candidates.append(("codex", CODEX_BIN))
    if not candidates:
        raise RuntimeError("本机既没有 claude 也没有 codex, 装一个并登录后再试")
    return candidates


def extract_json_object(text: str) -> dict:
    """从模型输出里抠出 JSON 对象 (容忍代码围栏和前后杂质)。"""
    text = text.strip()
    text = re.sub(r"^```[a-zA-Z]*\s*", "", text)
    text = re.sub(r"\s*```$", "", text)
    start, end = text.find("{"), text.rfind("}")
    if start == -1 or end <= start:
        raise ValueError("输出里没有 JSON 对象")
    return json.loads(text[start : end + 1])


def run_claude(binary: str, prompt: str) -> tuple[str, str | None]:
    cmd = [binary, "-p", "--output-format", "json"]
    if CLAUDE_MODEL:
        cmd += ["--model", CLAUDE_MODEL]
    proc = subprocess.run(cmd, input=prompt, capture_output=True, text=True, timeout=CLI_TIMEOUT_SECONDS)
    if proc.returncode != 0:
        raise RuntimeError(f"claude 退出码 {proc.returncode}: {proc.stderr.strip()[:500]}")
    envelope = json.loads(proc.stdout)
    if envelope.get("is_error"):
        raise RuntimeError(f"claude 返回错误: {str(envelope.get('result'))[:500]}")
    return str(envelope.get("result", "")), CLAUDE_MODEL or envelope.get("model")


def run_codex(binary: str, prompt: str) -> tuple[str, str | None]:
    # codex exec: "-" 从 stdin 读提示词; -o 把最终回答写到文件 (stdout 也只含最终回答, 留作兜底)
    with tempfile.NamedTemporaryFile(mode="r", suffix=".md", delete=False) as tmp:
        out_path = tmp.name
    try:
        cmd = [binary, "exec", "--skip-git-repo-check", "--output-last-message", out_path]
        if CODEX_MODEL:
            cmd += ["-m", CODEX_MODEL]
        cmd += ["-"]
        proc = subprocess.run(cmd, input=prompt, capture_output=True, text=True, timeout=CLI_TIMEOUT_SECONDS)
        if proc.returncode != 0:
            raise RuntimeError(f"codex 退出码 {proc.returncode}: {proc.stderr.strip()[-500:]}")
        text = ""
        if os.path.exists(out_path):
            with open(out_path, encoding="utf-8") as f:
                text = f.read().strip()
        if not text:
            text = proc.stdout.strip()
        if not text:
            raise RuntimeError("codex 没有产生输出")
        return text, CODEX_MODEL or "codex"
    finally:
        try:
            os.unlink(out_path)
        except OSError:
            pass


def run_analysis(payload: dict) -> dict:
    data_json = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    template = TODAY_PROMPT_TEMPLATE if payload.get("focus") == "today" else PROMPT_TEMPLATE
    prompt = template.format(field_guide=FIELD_GUIDE, data_json=data_json)

    errors = []
    for backend, binary in backend_candidates():
        print(f"[vita-ai] 尝试后端: {backend} ({binary})", flush=True)
        try:
            if backend == "claude":
                result_text, model_label = run_claude(binary, prompt)
            else:
                result_text, model_label = run_codex(binary, prompt)
            insight = extract_json_object(result_text)
            if not insight.get("overall"):
                raise RuntimeError("分析结果缺少 overall 字段")
            insight.setdefault("model", model_label)
            return insight
        except subprocess.TimeoutExpired:
            raise  # 超时不换后端: 再跑一个会超过 app 的读超时, 直接报超时
        except Exception as e:  # noqa: BLE001
            print(f"[vita-ai] {backend} 失败: {e}", flush=True)
            errors.append(f"{backend}: {str(e)[:160]}")
    raise RuntimeError("; ".join(errors))


class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, body: dict) -> None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _authorized(self) -> bool:
        if is_authorized(self.headers):
            return True
        self._send(401, {"error": "未授权: 请在 Vita 设置里填写正确的 AI 分析口令"})
        return False

    def do_GET(self):  # noqa: N802
        if not self._authorized():
            return
        if self.path == "/ping":
            self._send(200, {"service": "vita-ai", "ok": True})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):  # noqa: N802
        if not self._authorized():
            return
        if self.path != "/analyze":
            self._send(404, {"error": "not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            days = len(payload.get("daily", []))
            print(f"[vita-ai] 收到分析请求: {days} 个有记录日", flush=True)
            insight = run_analysis(payload)
            print("[vita-ai] 分析完成", flush=True)
            self._send(200, insight)
        except subprocess.TimeoutExpired:
            self._send(504, {"error": "分析超时, 可换个时间再试"})
        except Exception as e:  # noqa: BLE001
            print(f"[vita-ai] 分析失败: {e}", flush=True)
            self._send(500, {"error": str(e)[:300]})

    def log_message(self, fmt, *args):  # 安静一点
        pass


def main():
    available = [n for n, b in (("claude", CLAUDE_BIN), ("codex", CODEX_BIN)) if b]
    if not available:
        sys.exit("本机既没有 claude 也没有 codex 命令。装一个并登录, 或用 VITA_CLAUDE_BIN/VITA_CODEX_BIN 指路。")
    try:
        validate_security_config()
    except RuntimeError as e:
        sys.exit(str(e))
    host = bind_host()
    server = ThreadingHTTPServer((host, PORT), Handler)
    exposure = "局域网访问已开启, 需要口令" if ALLOW_LAN else "仅本机可访问"
    print(
        f"[vita-ai] 服务已启动: http://{host}:{PORT}  {exposure}  后端={BACKEND}  可用: {', '.join(available)}",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
