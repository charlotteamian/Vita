package com.vita.healthtracker.domain

/**
 * 今日洞察生成器: 把状态分背后的跨维度变化翻译成 1-2 条少而精的人话。
 *
 * 设计原则 (charlotte 要求: 不要套模板, 要真正结合短期 + 长期数据):
 *  - 短期: 每条洞察嵌入当日真实数值、与个人 7 日均值的偏离 (deltaText)、
 *    以及「连续 N 天偏离常态」(streakDays, 评分引擎按 0.5 SD 判定, 缺数据即中断)。
 *  - 长期: 接入近 30 天 vs 上个 30 天趋势 (monthTrend)。同一组指标先判断是
 *    「单日波动」还是「持续走弱」, 两种情况给出不同的结论与建议; 今天没短板
 *    但月度在走低时, 也会单独给出中期信号提醒。
 *  - 数值、连续天数、月度走向任一不同 → 文案不同; 只有底层数据完全相同时才会一致。
 *
 * 规则刻意做成「跨维度组合」而非单指标播报:
 *   · HRV↓ + 静息心率↑  → 自主神经欠恢复 / 可能在抗压或生病
 *   · 呼吸率↑ + 血氧↓    → 呼吸相关信号需要留意
 *   · 压力↑ + 身体电量低 → 当下负荷高、储备没充满
 *   · 睡眠明显偏弱        → 优先提醒休息
 *   · 整体高 + HRV 在线  → 适合安排高强度
 *   · 今天没短板 + 月度走低 → 中期信号 (长期数据兜底)
 */
object InsightGenerator {

    private const val MAX_INSIGHTS = 2
    private const val LOW = 55      // 子分低于此视作「拖后腿」
    private const val STRONG = 75   // 子分高于此视作「亮点」
    private const val SUSTAINED_DAYS = 3 // 连续偏离达到此天数视作「持续走弱」而非单日波动

    fun generate(
        overall: Int,
        band: ReadinessBand,
        contributions: List<MetricContribution>,
        confidence: Confidence,
        limits: List<ReadinessLimit>,
        monthTrend: TrendReport? = null,
    ): List<DailyInsight> {
        val byMetric = contributions.associateBy { it.metric }
        fun c(m: ScoreMetric): MetricContribution? = byMetric[m]
        fun sub(m: ScoreMetric): Int? = byMetric[m]?.subScore

        val candidates = mutableListOf<Ranked>()

        limits.minByOrNull { it.cap }?.let { limit ->
            candidates += Ranked(
                severity = 100,
                metrics = setOfNotNull(limit.metric),
                insight = DailyInsight(
                    tone = if (limit.cap < 55) InsightTone.ALERT else InsightTone.CAUTION,
                    title = limit.title,
                    body = "${limit.detail}${limit.action}",
                    detail = "今天先看这个：${limit.detail}\n\n可以这样做：${limit.action}\n\n说明：状态分会参考你的近期记录；如果出现缺觉、血氧偏低或多项指标一起走弱，分数会更保守。它适合用来安排今天的节奏，不是医疗结论。",
                ),
            )
        }

        val cHrv = c(ScoreMetric.HRV)
        val cRhr = c(ScoreMetric.RESTING_HR)
        val cResp = c(ScoreMetric.RESPIRATION)
        val cSpo2 = c(ScoreMetric.SPO2)
        val cSleep = c(ScoreMetric.SLEEP)
        val cStress = c(ScoreMetric.STRESS)
        val cBattery = c(ScoreMetric.BODY_BATTERY)

        val hrv = sub(ScoreMetric.HRV)
        val rhr = sub(ScoreMetric.RESTING_HR)
        val resp = sub(ScoreMetric.RESPIRATION)
        val spo2 = sub(ScoreMetric.SPO2)
        val sleep = sub(ScoreMetric.SLEEP)
        val stress = sub(ScoreMetric.STRESS)
        val battery = sub(ScoreMetric.BODY_BATTERY)

        // ① 自主神经欠恢复: HRV 偏低且静息心率偏高 (二者叠加 → 抗压/欠恢复/将病)
        if (hrv != null && rhr != null && hrv < LOW && rhr < LOW) {
            val streak = maxOf(cHrv?.streakDays ?: 0, cRhr?.streakDays ?: 0)
            val trend = monthNote(monthTrend, ScoreMetric.HRV, ScoreMetric.RESTING_HR)
            val sustained = streak >= SUSTAINED_DAYS || trend?.declined == true
            val persistence = when {
                streak >= SUSTAINED_DAYS -> "而且已经连续 $streak 天这样，不像单日波动"
                trend?.declined == true -> "而且${trend.text}，不是孤立的一天"
                else -> "目前看更像单日波动"
            }
            val advice = if (sustained) {
                "建议把今后两三天都按恢复期安排：降一档强度、补水、固定早睡，等这两项回到常态再加量。"
            } else {
                "今天先把强度降下来、多补水、早点休息，明早大多会回弹；如果明天还是这样再认真减量。"
            }
            candidates += Ranked(
                severity = 95,
                metrics = setOf(ScoreMetric.HRV, ScoreMetric.RESTING_HR),
                insight = DailyInsight(
                    tone = InsightTone.ALERT,
                    title = if (sustained) "恢复欠账在累积" else "今天恢复不太够",
                    body = "${factLine(cHrv, cRhr)}。两项一起走弱，常见于疲劳累积、压力偏高或睡眠恢复不够；$persistence。$advice",
                    detail = buildString {
                        append("看到的情况：${factsFull(cHrv, cRhr)}。HRV 比近期低，静息心率又比平时高，两个方向都提示恢复不充分；$persistence。\n\n")
                        append("可以理解为：身体可能还在消化最近的压力、训练、睡眠不足或轻微不适。")
                        append(if (sustained) "连续多天同向偏离时，更应该把恢复当成这几天的主线。" else "单看一天不必紧张，但它适合提醒你今天先别硬顶。")
                        append("\n\n可以这样做：$advice 低强度活动如散步、拉伸、轻松骑行不受影响；咖啡因尽量往前放。")
                        append("\n\n需要留意：如果接下来 2–3 天仍然这样，或伴随喉咙痛、酸痛、低热、明显乏力，请继续降低负荷，必要时就医确认。")
                    },
                ),
            )
        }

        // ② 呼吸相关信号: 呼吸率偏高 + 血氧偏低
        if (resp != null && resp < LOW && spo2 != null && spo2 < 60) {
            val streak = maxOf(cResp?.streakDays ?: 0, cSpo2?.streakDays ?: 0)
            val persistence = if (streak >= 2) "且已连续 $streak 天" else "偶发一次可以先复核佩戴和身体感受"
            candidates += Ranked(
                severity = 92,
                metrics = setOf(ScoreMetric.RESPIRATION, ScoreMetric.SPO2),
                insight = DailyInsight(
                    tone = InsightTone.ALERT,
                    title = "留意呼吸相关信号",
                    body = "${factLine(cResp, cSpo2)}。昨夜呼吸偏快、血氧偏低（$persistence），可能和鼻塞、睡眠环境、佩戴状态或身体不适有关。先多休息，今天不要叠加强度。",
                    detail = "看到的情况：${factsFull(cResp, cSpo2)}。昨夜呼吸率比近期高，同时血氧读数偏低，$persistence。\n\n可以理解为：这类组合可能来自鼻塞、空气干燥、仰睡打鼾、手表佩戴松紧变化，也可能和着凉或身体不适有关。\n\n可以这样做：今天注意休息和保暖，保持房间通风但别直吹；睡前把卧室湿度调舒服一点，尽量侧睡；强度训练先缓一缓。\n\n需要留意：如果同时出现持续咳嗽、发热、明显乏力、胸闷，或血氧连续多晚偏低，请及时就医确认。",
                ),
            )
        }

        // ③ 睡眠是最大短板
        if (sleep != null && sleep < LOW && contributions.minByOrNull { it.subScore }?.metric == ScoreMetric.SLEEP) {
            val streak = cSleep?.streakDays ?: 0
            val trend = monthNote(monthTrend, ScoreMetric.SLEEP)
            val sustained = streak >= SUSTAINED_DAYS || trend?.declined == true
            val persistence = when {
                streak >= 2 -> "已连续 $streak 晚低于你的常态"
                trend?.declined == true -> trend.text
                else -> "昨晚是近期里偏弱的一晚"
            }
            val advice = if (sustained) {
                "这周把固定上床时间当作第一优先级，比某一晚刻意补觉更有效；白天可以小睡 20 分钟应急。"
            } else {
                "今晚提前 30–60 分钟上床、减少咖啡因和睡前屏幕，大概率一晚就能补回来。"
            }
            candidates += Ranked(
                severity = 80,
                metrics = setOf(ScoreMetric.SLEEP),
                insight = DailyInsight(
                    tone = InsightTone.CAUTION,
                    title = if (sustained) "睡眠欠账在累积" else "睡眠在拖累今天的状态",
                    body = "${factLine(cSleep)}，是几项里最弱的一环，$persistence。恢复主要发生在睡眠中，今天尽量避免高强度安排。$advice",
                    detail = "看到的情况：${factsFull(cSleep)}，在今天参与评分的几项里偏弱，对整体状态影响最大；$persistence。\n\n可以理解为：睡眠是恢复的主要入口。睡得少、醒得多或睡眠结构不稳，都会让今天更容易疲惫、注意力下降，也更难承受高强度安排。\n\n可以这样做：今天把训练或高难度脑力任务往后排，别叠强度。$advice\n\n长期方向：尽量固定入睡和起床时间。稳定的节奏通常比偶尔睡很久更有帮助。",
                ),
            )
        }

        // ④ 压力高 + 身体电量没充满
        if (stress != null && stress < LOW && battery != null && battery < 50) {
            val streak = cStress?.streakDays ?: 0
            val persistence = if (streak >= 2) "压力读数已连续 $streak 天高于常态，" else ""
            candidates += Ranked(
                severity = 72,
                metrics = setOf(ScoreMetric.STRESS, ScoreMetric.BODY_BATTERY),
                insight = DailyInsight(
                    tone = InsightTone.CAUTION,
                    title = "压力偏高、储备没回满",
                    body = "${factLine(cStress, cBattery)}。${persistence}加上身体电量没充满，说明今天余量不算多。穿插几次 5 分钟慢呼吸或散步，午后小憩会更友好。",
                    detail = "看到的情况：${factsFull(cStress, cBattery)}。${persistence}同时身体电量没有充满。\n\n可以理解为：今天的消耗可能比回充更快。你不一定需要完全停下来，但需要给自己留一点缓冲，避免一路硬撑到晚上。\n\n可以这样做：每隔几小时做 3–5 分钟慢呼吸，或起身散步 5 分钟；午后如果困，可以小憩 15–20 分钟；尽量把高压任务之间留出间隔。\n\n长期方向：让一天里出现几段真正放松的时间，比晚上集中补救更有效。",
                ),
            )
        }

        // ⑤ 恢复充分, 适合高强度 (正向); 月度若在走低会附带提醒, 在改善则顺势确认
        if (limits.isEmpty() && overall >= 80 && (hrv == null || hrv >= STRONG)) {
            val recovery = recoverySummary(contributions)
            val trend = monthNote(monthTrend, ScoreMetric.HRV, ScoreMetric.RESTING_HR, ScoreMetric.SLEEP)
            val trendLine = when {
                trend?.declined == true -> "不过${trend.text}，加量之后记得把恢复也跟上。"
                trend != null -> "而且${trend.text}，这个好状态是有底子的。"
                else -> "今天可以安排一次有质量的训练或挑战性任务，训练后照常补给和早睡。"
            }
            candidates += Ranked(
                severity = 70,
                isOverview = true,
                insight = DailyInsight(
                    tone = InsightTone.POSITIVE,
                    title = "恢复充分，可以加量",
                    body = "状态分 $overall。$recovery，恢复余量比较充足。$trendLine",
                    detail = "看到的情况：状态分 $overall，处在「${band.label}」区间。$recovery。${trend?.let { "拉长到 30 天看：${it.text}。" } ?: ""}\n\n可以理解为：今天没有出现需要明显降档的信号，适合把好状态用在一个明确重点上。\n\n可以这样安排：训练可以选间歇、长距离或大重量中的一种；工作则把最需要专注的任务放在状态高点。结束后补够水分、蛋白和碳水，今晚照常保证睡眠。",
                ),
            )
        }

        // ⑥ 单一最弱维度 (在没有触发更高优先规则、且确有一项明显偏低时)
        val weakest = contributions.minByOrNull { it.subScore }
        if (weakest != null && weakest.subScore < LOW) {
            val trend = monthNote(monthTrend, weakest.metric)
            val sustained = weakest.streakDays >= SUSTAINED_DAYS || trend?.declined == true
            val persistence = when {
                weakest.streakDays >= 2 -> "已连续 ${weakest.streakDays} 天偏离常态"
                trend?.declined == true -> trend.text
                else -> "更像单日波动，先观察一天"
            }
            candidates += Ranked(
                severity = 50,
                metrics = setOf(weakest.metric),
                insight = DailyInsight(
                    tone = if (sustained) InsightTone.CAUTION else InsightTone.NEUTRAL,
                    title = "${weakest.label}是今天的薄弱项",
                    body = "${factLine(weakest)}，$persistence。${weakDriverAdvice(weakest.metric)}",
                    detail = "看到的情况：${factsFull(weakest)}，是今天最需要留意的一项；$persistence。${trend?.let { "\n\n月度视角：${it.text}。" } ?: ""}\n\n${weakDriverDetail(weakest.metric)}",
                ),
            )
        }

        // ⑦ 今天没短板, 但月度有指标在走低 → 中期信号 (真正用长期数据说话的一条)
        val monthDeclines = monthTrend?.metrics?.filter { it.direction == TrendDirection.DECLINED }.orEmpty()
        if (overall in 55..79 && contributions.none { it.subScore < LOW } && monthDeclines.isNotEmpty()) {
            val lead = monthDeclines.first()
            val names = monthDeclines.take(2).joinToString("、") { it.label }
            candidates += Ranked(
                severity = 48,
                insight = DailyInsight(
                    tone = InsightTone.NEUTRAL,
                    title = "今天还行，但有个中期信号",
                    body = "今天各项没有明显短板（状态分 $overall），不过近 30 天${names}整体比上月走低（${lead.label} ${lead.deltaText}）。单日好坏会波动，月度走向更值得花一点心思。",
                    detail = "看到的情况：今天参与评分的指标都在常态范围（${topFacts(contributions, 3)}），但拉长到 30 天对比上个 30 天，${monthDeclines.joinToString("；") { "${it.label} ${it.deltaText}" }}。\n\n可以理解为：${lead.note}\n\n可以这样做：不需要为今天改变安排；以「周」为单位回看作息和负荷，比如固定睡眠时间、每周留 1–2 天低强度日，两三周后再看月度对比是否回稳。",
                ),
            )
        }

        // ⑧ 整体良好且月度平稳 (正向兜底, 低优先)
        if (limits.isEmpty() && overall in 70..79 && contributions.none { it.subScore < LOW }) {
            val strong = contributions.maxByOrNull { it.subScore }
            val recovery = recoverySummary(contributions)
            val trend = monthNote(monthTrend, ScoreMetric.HRV, ScoreMetric.RESTING_HR, ScoreMetric.SLEEP)
            candidates += Ranked(
                severity = 35,
                isOverview = true,
                insight = DailyInsight(
                    tone = InsightTone.POSITIVE,
                    title = "状态在线，保持节奏",
                    body = "状态分 $overall。$recovery，整体波动不大。${if (trend?.declined == false) "${trend.text}，" else ""}按原计划推进即可，不必额外加量。",
                    detail = buildString {
                        append("看到的情况：整体状态分 $overall，处在「良好」区间，没有哪一项明显拖后腿。")
                        topFacts(contributions, 3).takeIf { it.isNotBlank() }?.let { append("今天的关键读数：$it。") }
                        trend?.let { append("月度视角：${it.text}。") }
                        append("\n\n可以理解为：今天整体比较平稳，身体把近期负荷消化得还可以。\n\n")
                        append("可以这样安排：照常推进训练和工作计划即可，不必刻意加量或大幅减量；继续保持规律睡眠、补水和饮食。")
                        if (strong != null && strong.subScore >= STRONG) {
                            append("\n\n今天比较稳的一项：${factsFull(strong)}。")
                        }
                    },
                ),
            )
        }

        // ⑨ 数据不足提示 (低优先, 仅在确实样本少时)
        if (confidence == Confidence.LOW) {
            candidates += Ranked(
                severity = 30,
                isOverview = true,
                insight = DailyInsight(
                    tone = InsightTone.NEUTRAL,
                    title = "再多同步几天，洞察更准",
                    body = "目前记录还不多，状态分会先用通用参考来估一估。继续佩戴并同步，一到两周后会更贴近你的节奏。",
                    detail = "看到的情况：当前可用的历史记录还不多，状态分只是初步参考。\n\n可以理解为：HRV、静息心率、呼吸率等指标每个人差异都很大，Vita 需要先熟悉你的近期节奏。\n\n可以这样做：尽量每天佩戴手表、规律同步，尤其别漏掉夜间睡眠数据。记录多起来后，提示会更贴合你。",
                ),
            )
        }

        // 兜底: 一条都没有时, 给一句带当日真实读数的中性总览 (确保不同数据的日子文案不同)。
        if (candidates.isEmpty()) {
            val facts = topFacts(contributions, 3)
            candidates += Ranked(
                severity = 10,
                isOverview = true,
                insight = DailyInsight(
                    tone = InsightTone.NEUTRAL,
                    title = "${band.label} · ${band.tagline}",
                    body = "状态分 $overall。今天没有明显需要特别留意的变化${if (facts.isNotBlank()) "（$facts）" else ""}，按自己的节奏安排即可。",
                    detail = "看到的情况：今天状态分 $overall，各项指标大体平稳。${facts.takeIf { it.isNotBlank() }?.let { "关键读数：$it。" } ?: ""}\n\n可以理解为：今天没有出现需要特别提醒的组合变化。\n\n可以这样安排：按原本计划推进即可，不需要刻意调整。继续保持规律作息和佩戴同步，后续提示会更贴合你。",
                ),
            )
        }

        // 有具体关联结论时不再叠加「状态在线」类总览；两条结论之间也不能重复消费同一组指标。
        val ranked = candidates
            .sortedByDescending { it.severity }
            .distinctBy { it.insight.title }
        val pool = ranked.filterNot { it.isOverview }.takeIf { it.isNotEmpty() } ?: ranked
        val selected = mutableListOf<Ranked>()
        pool.forEach { candidate ->
            val overlaps = candidate.metrics.isNotEmpty() && selected.any { selectedItem ->
                selectedItem.metrics.any(candidate.metrics::contains)
            }
            if (!overlaps && selected.size < MAX_INSIGHTS) selected += candidate
        }
        return selected.map { it.insight }
    }

    /** 一条简短的「指标 + 当日值」, 例如「HRV 38 ms」「睡眠 6h10m」。 */
    private fun factLine(c: MetricContribution?): String =
        c?.let { "${it.label} ${it.valueText}" } ?: ""

    /** 多个指标的简短拼接, 自动跳过缺失项。 */
    private fun factLine(vararg cs: MetricContribution?): String =
        cs.filterNotNull().joinToString("、") { "${it.label} ${it.valueText}" }

    /** 完整读数: 数值 + 7 日偏离 + 连续偏离天数, 有什么拼什么。 */
    private fun factsFull(vararg cs: MetricContribution?): String =
        cs.filterNotNull().joinToString("、") { c ->
            val extras = listOfNotNull(
                c.deltaText?.takeIf { it.isNotBlank() },
                c.streakDays.takeIf { it >= 2 }?.let { "已连续 $it 天偏离常态" },
            )
            if (extras.isEmpty()) "${c.label} ${c.valueText}"
            else "${c.label} ${c.valueText}（${extras.joinToString("，")}）"
        }

    /** 取子分最高的前 n 个维度拼成读数串 (用于正向/总览洞察)。 */
    private fun topFacts(contributions: List<MetricContribution>, n: Int): String =
        contributions.sortedByDescending { it.subScore }
            .take(n)
            .joinToString("、") { "${it.label} ${it.valueText}" }

    /** 评分维度在 TrendAnalyzer 月度报告里对应的指标名。 */
    private fun ScoreMetric.trendLabel(): String = when (this) {
        ScoreMetric.HRV -> "心率变异性"
        ScoreMetric.RESTING_HR -> "静息心率"
        ScoreMetric.RESPIRATION -> "呼吸率"
        ScoreMetric.SPO2 -> "血氧"
        ScoreMetric.SLEEP -> "睡眠时长"
        ScoreMetric.STRESS -> "压力"
        ScoreMetric.BODY_BATTERY -> "身体电量"
    }

    /** 从月度趋势中取出与给定维度相关、且有明确走向的一条, 转成可拼接的短句。 */
    private fun monthNote(monthTrend: TrendReport?, vararg metrics: ScoreMetric): TrendNote? {
        if (monthTrend == null) return null
        val byLabel = monthTrend.metrics.associateBy { it.label }
        val hits = metrics.mapNotNull { byLabel[it.trendLabel()] }
        hits.firstOrNull { it.direction == TrendDirection.DECLINED }?.let {
            return TrendNote("近 30 天${it.label}整体比上月走低（${it.deltaText}）", declined = true)
        }
        hits.firstOrNull { it.direction == TrendDirection.IMPROVED }?.let {
            return TrendNote("近 30 天${it.label}整体比上月在改善（${it.deltaText}）", declined = false)
        }
        return null
    }

    /** 优先说清楚两项指标之间的关系，避免每天只换几个数字、其余文案完全相同。 */
    private fun recoverySummary(contributions: List<MetricContribution>): String {
        val byMetric = contributions.associateBy { it.metric }
        fun c(metric: ScoreMetric) = byMetric[metric]
        val battery = c(ScoreMetric.BODY_BATTERY)
        val stress = c(ScoreMetric.STRESS)
        val hrv = c(ScoreMetric.HRV)
        val resting = c(ScoreMetric.RESTING_HR)
        val sleep = c(ScoreMetric.SLEEP)
        return when {
            battery != null && stress != null ->
                "${factLine(battery, stress)}，说明能量储备和当下负荷配合得比较稳"
            hrv != null && resting != null ->
                "${factLine(hrv, resting)}，自主神经恢复和基础心率方向一致"
            sleep != null && resting != null ->
                "${factLine(sleep, resting)}，昨夜恢复输入和今天的基础负荷相互印证"
            else -> topFacts(contributions, 3).ifBlank { "已记录指标都在可用范围内" }
        }
    }

    private fun weakDriverAdvice(metric: ScoreMetric): String = when (metric) {
        ScoreMetric.HRV -> "HRV 低于你的常态，多半反映疲劳或压力累积。今天降低强度，优先放松与睡眠。"
        ScoreMetric.RESTING_HR -> "静息心率高于常态，常和睡眠不足、脱水或将病有关。多喝水、避免剧烈运动。"
        ScoreMetric.RESPIRATION -> "呼吸率高于常态，可能是压力或身体不适。留意休息，做几次慢呼吸。"
        ScoreMetric.SPO2 -> "血氧低于常态，注意通风与休息；若持续偏低请多观察。"
        ScoreMetric.SLEEP -> "睡眠偏少或偏浅，今晚尽量提前入睡、规律作息。"
        ScoreMetric.STRESS -> "白天压力偏高，安排几次短暂放松，避免连续高压。"
        ScoreMetric.BODY_BATTERY -> "身体电量储备偏低，注意节奏、适当补觉把电量充回来。"
    }

    private fun weakDriverDetail(metric: ScoreMetric): String = when (metric) {
        ScoreMetric.HRV ->
            "可以理解为：HRV 是恢复和压力状态的参考之一。它单独走低时，常见于疲劳累积、压力偏高、睡眠质量下降或身体不太舒服。\n\n可以这样做：今天降低训练强度，优先放松、拉伸、散步和睡眠；过午减少咖啡因和酒精。若连续几天偏低，或伴随明显不适，请继续减量并多观察。"
        ScoreMetric.RESTING_HR ->
            "可以理解为：静息心率比平时高，常和睡眠不足、脱水、压力偏高或身体不适有关。\n\n可以这样做：全天多补水，避免剧烈运动和大量咖啡因，今晚保证睡眠。若同时出现喉咙不适、酸痛或低热，请优先休息，必要时就医。"
        ScoreMetric.RESPIRATION ->
            "可以理解为：呼吸率升高可能来自压力、焦虑、鼻塞、着凉，或睡眠中呼吸不够顺畅。\n\n可以这样做：今天留意休息和保暖，白天穿插几次慢呼吸；如果伴随鼻塞、咳嗽或发热，请多观察身体感受。"
        ScoreMetric.SPO2 ->
            "可以理解为：睡眠中血氧偏低可能和鼻塞、空气干燥、海拔、佩戴状态或睡眠呼吸不顺有关。偶发一次先复核环境和佩戴。\n\n可以这样做：保持卧室通风、湿度适宜，尝试侧睡，适量补水。如果持续多夜偏低，或伴随白天乏力、头痛，请就医确认。"
        ScoreMetric.SLEEP ->
            "可以理解为：睡眠是恢复的主要入口。时长不足、醒得多或睡眠结构不稳，都会让第二天更难恢复。\n\n可以这样做：今晚尽量提前入睡、固定起床时间，睡前一小时调暗灯光、减少屏幕，过午减少咖啡因；午后可小睡 20 分钟补一点。规律比单纯睡久更重要。"
        ScoreMetric.STRESS ->
            "可以理解为：压力读数持续偏高，通常表示身体长时间处在紧绷状态，真正放松下来的时间不够。\n\n可以这样做：在一天里安排几次短暂放松，3–5 分钟慢呼吸、起身散步或午后小憩都可以；尽量在高压任务之间留出缓冲。"
        ScoreMetric.BODY_BATTERY ->
            "可以理解为：身体电量是精力储备的参考。它偏低时，说明近期消耗可能比回充更快。\n\n可以这样做：注意一天的节奏，穿插短暂休息和午后小憩；今晚保证睡眠，避免在低储备时再叠高强度负荷。"
    }

    private data class TrendNote(val text: String, val declined: Boolean)

    private data class Ranked(
        val severity: Int,
        val metrics: Set<ScoreMetric> = emptySet(),
        val isOverview: Boolean = false,
        val insight: DailyInsight,
    )
}
