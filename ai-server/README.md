# Vita AI 分析服务 (Mac 端)

Vita app「趋势页 → AI 洞察」的本机分析服务。app 把聚合健康数据经家庭 Wi-Fi 发过来，
这里调用本机已登录的 CLI 做深度分析，支持两种后端。此服务不要求另填 API key；实际可用额度和费用取决于 CLI 登录账号及服务商规则：

| 后端 | 命令 | 计费 |
|---|---|---|
| `claude` (默认优先) | `claude -p` | 使用已登录 Claude 账号的可用额度 |
| `codex` | `codex exec` | 使用已登录 Codex 账号的可用额度 |

默认 `auto`：按 claude → codex 顺序尝试，**前一个跑失败（比如订阅停了、额度用完了）会自动换下一个**。
哪天停订 Claude、改订 ChatGPT，只需 `codex login` 登录一次，其余什么都不用动——**app 端零改动**。
想固定用某一家就显式设 `VITA_AI_BACKEND=claude` 或 `codex`。

## 手动运行 (先这样验证)

默认只监听本机, 不会被手机或同一 Wi-Fi 的其它设备访问:

```bash
python3 ai-server/vita_ai_server.py
```

看到 `服务已启动: http://127.0.0.1:8787` 即可。验证服务通不通:

```bash
curl http://localhost:8787/ping
```

如果要让手机在同一 Wi-Fi 下访问, 必须显式开启局域网监听并设置长口令:

```bash
TOKEN="$(python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(32))
PY
)"
VITA_AI_ALLOW_LAN=1 VITA_AI_TOKEN="$TOKEN" python3 ai-server/vita_ai_server.py
```

然后在 Vita 设置页 → 通用设置 → AI 分析服务器填写:

- 地址: `Mac的局域网IP:8787`
- 口令: 上面生成的 `$TOKEN`

带口令验证:

```bash
curl -H "Authorization: Bearer $TOKEN" http://Mac的局域网IP:8787/ping
```

## 开机自启 (验证 OK 后)

```bash
# 1. 把 plist 里的 python3 / 脚本路径核对一遍 (默认已按本机路径写好)
#    如需手机访问, 还要在 EnvironmentVariables 里加 VITA_AI_ALLOW_LAN=1 和 VITA_AI_TOKEN=<长随机口令>
# 2. 安装并启动:
cp ai-server/com.vita.aiserver.plist ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/com.vita.aiserver.plist

# 停用:
launchctl unload ~/Library/LaunchAgents/com.vita.aiserver.plist
```

日志在 `/tmp/vita-ai-server.log`。

## 可调环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `VITA_AI_BACKEND` | `auto` | `auto` / `claude` / `codex` |
| `VITA_AI_PORT` | `8787` | 监听端口 |
| `VITA_AI_ALLOW_LAN` | 空 | 设为 `1` / `true` / `on` 时允许同 Wi-Fi 手机访问 |
| `VITA_AI_TOKEN` | 空 | 局域网访问口令; 开启 `VITA_AI_ALLOW_LAN` 时必填 |
| `VITA_AI_MODEL` | claude 默认 | 传给 `claude --model`，如 `claude-sonnet-4-6` (更省额度) |
| `VITA_CODEX_MODEL` | codex 默认 | 传给 `codex exec -m` |
| `VITA_CLAUDE_BIN` / `VITA_CODEX_BIN` | 自动查找 | 命令完整路径 (launchd 下 PATH 短时有用) |

## 注意

- 安全边界: 不使用自动发现广播; 默认只监听 `127.0.0.1`; 局域网模式必须带 Bearer 口令。
- 数据路径：手机 → 家庭 Wi-Fi → 本机 → 所选 CLI 后端对应的模型服务。Mac 是请求中转和 CLI 执行端，分析请求仍会离开本机；数据处理规则以所选服务与账号配置为准。
- 发送内容包含聚合日指标、睡眠/运动/周期/习惯等关键明细，以及情绪记录的各个时刻和用户填写的情绪备注。不会发送原始心率/压力曲线、Garmin raw JSON 或运动等其他自由文本备注。
- App 常规记录和统计保存在手机本地；请求 AI 分析时才会发送上述分析数据。若在 App 设置中选择 API 直连，数据会由手机直接发送到用户配置的 API 服务，不经过这台 Mac。
- 不要把 AI 服务口令、API Key、个人分析请求或健康备份提交到公开仓库。
