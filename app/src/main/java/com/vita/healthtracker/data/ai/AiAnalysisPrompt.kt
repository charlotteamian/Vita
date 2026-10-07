package com.vita.healthtracker.data.ai

/**
 * API 直连时 app 端自己拼的分析提示词。
 * 内容与 Mac 端 ai-server/vita_ai_server.py 的 PROMPT_TEMPLATE + FIELD_GUIDE 保持一致;
 * 改其中一份时记得同步另一份 (LAN 桥接路径仍由服务端拼提示词)。
 */
object AiAnalysisPrompt {

    private const val FIELD_GUIDE =
        "longTerm=近十年压缩摘要(from/to/trackedDays, years逐年聚合, shifts相邻年份最大变化段; " +
            "year字段: y年份, days有效日, sleepNights主睡眠晚数, sleepMin平均睡眠分钟, steps日均步数, " +
            "activeMin日均活跃分钟, rhr静息心率, hrv心率变异性, stress压力, bbHigh身体电量峰值, " +
            "spo2血氧, weight体重, exerciseCount/Min/Km运动次数/时长/距离, cycleStarts周期起始次数); " +
            "daily=每日聚合指标(steps步数, km距离公里, kcal活动消耗, avgHr平均心率, rhr静息心率, " +
            "hrv心率变异性ms, stress全天平均压力0-100, bbHigh/bbLow身体电量高低点, spo2血氧%, " +
            "resp呼吸率, weight体重kg); " +
            "sleeps=每晚主睡眠(d醒来日期, start入睡时刻, min总分钟, deep/light/rem/awake各阶段分钟, score睡眠评分0-100); " +
            "exercises=运动会话(type类型, min时长, km距离, kcal消耗, avgHr平均心率, te训练效果); " +
            "cycle=月经记录(flow经量0=无1=点滴2=轻3=中4=重, start是否周期起始日, symptoms症状); " +
            "moods=每日情绪(mood当天主导情绪名, valence当天平均情绪价1-5越低整体越低落, " +
            "moments当天各时刻[t时刻/mood情绪名/note用户备注], 一天可多条, note是用户亲手写下的处境或缘由); " +
            "habits=习惯及其打卡日期; " +
            "windowDays=用户本次选择的近期明细窗口天数"

    fun build(dataJson: String): String = """你是一位私人健康数据分析师。<data> 标签里是一位女性用户的可穿戴设备与生活记录 (JSON): longTerm 是多年压缩摘要, daily/sleeps/exercises/cycle/moods/habits 是近期细节。字段说明: $FIELD_GUIDE。

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
{"overall": "3-5句总评", "longTermFindings": [{"title": "短标题", "body": "2-4句"}, ... 2到3条], "recentPatterns": [{"title": "短标题", "body": "2-4句"}, ... 6到8条], "actions": [{"title": "短标题", "body": "1-3句"}, ... 2到4条], "watch": ["一句话观察项", ... 0到3条]}

<data>
$dataJson
</data>"""

    /**
     * 「今日状态」快速分析 (今日页, payload.focus="today"): 结合昨晚睡眠讲今天,
     * 加近 3-7 天短期趋势——长期规律归趋势页, 这里只看眼前。
     */
    fun buildToday(dataJson: String): String = """你是一位私人健康数据分析师。<data> 标签里是一位女性用户最近两周的可穿戴设备与生活记录 (JSON), focus="today" 表示这是一次「今日状态」快速分析, generatedAt 是今天的日期。字段说明: $FIELD_GUIDE。

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
{"overall": "2-3句今日结论", "longTermFindings": [], "recentPatterns": [{"title": "短标题", "body": "1-3句"}, ... 2到4条], "actions": [{"title": "短标题", "body": "1-2句"}, ... 1到3条], "watch": ["一句话观察项", ... 0到2条]}

<data>
$dataJson
</data>"""
}
