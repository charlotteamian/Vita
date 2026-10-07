# CLAUDE.md — Vita 健康追踪 App

> 此文件是 Claude 的工作记忆。每次新会话进入这个目录时会自动加载。

## 项目是什么

charlotte 的**个人健康/健身数据 Android app**,代号 **Vita** (拉丁语"生命")。
- Android 包名: `com.vita.healthtracker` (debug 自动加 `.debug` 后缀)
- 主题色: 深色背景 `#06080f` (和 Fortuna 同款)
- 定位: 把佳明 / 三星 / 小米等手表的健康数据**统一拉到本机**,做日/周/月/年统计,纯本地,不上云

## 当前进度

- ✅ 完整 Kotlin + Compose + Health Connect 脚手架已搭好,能直接 Android Studio Open + Sync
- ✅ 4 个 Tab UI (今日 / 统计 / 周期 / 设置) 基础功能就绪
- ✅ Room 数据库 8 张表, Health Connect 增量同步管线, JSON 全量导出/导入
- ✅ **完成第一轮 UI 抛光 (Premium 视觉升级)**:
  - 加入玻璃态 (Glassmorphism) 导航栏和填充式高亮图标
  - 引入 `VitaGradients` 和全局卡片背景渐变、微光/呼吸动画
  - MetricCard 数字加载动画与图标辉光、TodayScreen 卡片交错入场动画
  - SettingsScreen 加入脉冲指示灯，CycleScreen 加入预测倒数天数大字与经量强度圆点
- ✅ **图表与展示层**: StatsScreen 图表套用 Vita 主题色，按不同运动类型（映射自 `ExerciseTypeNames`）渲染
- ✅ **后台任务**: 完成 WorkManager 定时拉取任务 (`SyncWorker` 每 3 小时执行)，注册于 `VitaApplication`
- ✅ **2026-05 一轮 bug 修复 + 设置增强**:
  - 修复统计页「月/年」闪退: Vico beta 切区间时新旧模型列数不一致会在绘制时崩。改成 `remember(state.range)` 给每个区间一个全新空 producer + `key(state.range)` 重建图表子树 (StatsScreen)
  - 新增 `SettingsPreferences` (DataStore, 库已在依赖里): 持久化「一周开始日」「首页数据项可见性」
  - 「一周开始日」可选周一/周日 (默认周一)，StatsViewModel 周视图按偏好对齐到本周起始日 (`startOfWeek`)
  - 设置页新增「账号授权」入口 → `AccountAuthScreen` (`account_auth` 路由)，列出可连接的第三方账号；当前只放佳明可用，三星/小米/Fitbit 是「即将支持」占位。Garmin 登录逻辑从 SettingsViewModel 抽到 `AccountAuthViewModel`
  - 首页数据项可在设置里逐项开关 (`HomeMetric` 枚举为单一来源，TodayScreen 按 `enabledMetrics` 过滤)
  - 加固授权: HC 授权回调上报真实结果(`reportPermissionResult`) + 启动器 `runCatching`; Garmin `logout` 真正清 cookie/token、登录错误信息更友好
- ✅ **2026-05-23 同步/统计大修进展**:
  - **重要: Garmin 登录链路现在国区/国际区都能登录，认证相关代码请冻结，不要再改 SSO / cookie / OAuth / 登录流程。** 后续只允许动同步调度、数据解析、展示层。
  - `DailyHealthSnapshot` 扩展主流健康维度: HRV、压力、身体电量、血氧、呼吸率、体重；`ExerciseSession` 扩展佳明活动明细字段；新增 `BodyBatterySample` 动态采样表和 `GarminRawRecord` 原始接口归档表；`VitaDatabase` 当前 v6，并已补 1/2→3、3/4→5、5→6 迁移，避免升级清库。
  - Garmin API 同步现在额外拉睡眠/睡眠评分/HRV/压力/身体电量曲线/血氧/呼吸率/活动明细，并对有 Garmin 信号的日期归档更多接口原始 JSON：日汇总曲线、全天心率、压力、身体电量事件、睡眠、HRV、血氧、呼吸率、强度活动、爬楼、全天事件、饮水、体重/身体成分、生活记录、经期详情、训练准备度/状态/耐力/爬坡/健身年龄、饮食营养、运动详情/轨迹/分段/天气/心率区间/功率区间/训练组等。Health Connect 同步补体重。
  - 修复 totalCalories-only/BMR 幽灵数据: 只有总消耗、没有步数/活动/心率/睡眠等真实信号的行不再作为有效数据；Body Battery 动态值不再单独撑起日历/统计日期，避免卡路里整列相同、月/年图被拉回 2016。
  - 新增应用级 `SyncCoordinator`: 今日页和账号页同步都跑在 app scope，离开页面不断；支持停止同步；同步范围有增量/最近一周/最近一月/最近一年/全部。
  - 账号页维持国际区/国区两个独立卡片，每个区服可单独选择范围同步/停止同步；登录/退出逻辑保持原样。
  - 统计页增加数据日历、回到今日、按日视图下方显示昨日/过去三天/过去七天汇总；月/年图只显示有真实数据的月份/年份；睡眠按月/年多柱聚合。
  - 今日页增加回到今日、同步进度/停止按钮，并支持更多首页指标开关；同步入口改成紧凑下拉图标，上次同步只显示 `yyyy-MM-dd HH:mm`。
  - 身体电量卡片可进入当天曲线页；统计页活动明细可进入运动详情，显示距离/速度/配速/心率/踏频/海拔/卡路里/训练效果/汗液流失等能从 Garmin 摘要拿到的字段。
  - 统计页日历选中某天后可打开 `Garmin 全量数据` 页面，按区服和接口分类展示已归档 Garmin 数据，并把常见字段匹配成中文指标、单位和采样摘要；JSON 仍完整保存在本地和备份里。
- ✅ **2026-05-24 同步体验 + 展示修复**:
  - 同步 UI 不再展示「请求日汇总/睡眠/活动/经期/全量」这类内部阶段，只显示百分比和天数进度。
  - Garmin raw 接口请求改为单日内限速并发，并移除每日固定等待；历史日期若已归档 raw 数据会跳过重复拉取，今天和最近两天仍刷新，提升反复测试速度。
  - 用户手动同步会启动 `GarminSyncForegroundService` 前台服务和通知，切后台/锁屏时更可靠；WorkManager 周期同步现在网络可用时即跑，并且 Health Connect 不可用时也会继续尝试 Garmin。
  - 统计月/年图会用运动记录补齐步数/距离/消耗/时长/心率兜底，避免「下面有步行活动，上面步数图没有」的矛盾；运动明细行日期显示年份，跨年不再混淆。
  - `Garmin 全量数据` 页去掉接口路径和未适配英文键，只展示已识别、有意义的健康项目，并给每类数据加中文解释；未展示字段仍保存在本地 raw 表。
- ✅ **2026-05-24 显示修复 + 手机/Apple 数据源**:
  - 修两个显示问题: 设置页「系统权限」卡片改竖排(图标+标题一行、描述全宽、`管理授权`按钮在下方)，描述不再被按钮挤成多行/「限」字孤行；统计页今日心率行把平均心率列设为 `weight(1f)`、`静息 xx bpm` 加 `maxLines=1 + softWrap=false`，不再把 `bpm` 挤到第二行。
  - 手机做成独立数据源: `StepSensorManager` 用 SharedPreferences 持久化当天基线(`vita_step_sensor`)，app/手机重启不清零、识别重启计步器归零；按步数估算距离(~0.72m/步)与活跃卡路里(~0.04kcal/步)，`source="phone"`；仅当某天还没其它来源时由手机记，已有手表/HC/佳明数据不覆盖；写库限流(每+10 步)。统计页今日卡片加「数据来源 · xx」标签(`domain/HealthSource.kt` 的 `healthSourceLabel`)。
  - 新增 Apple 健康导入(Android 无法直连 HealthKit): `data/apple/AppleHealthImporter.kt` 流式 XmlPull 解析 iPhone 导出的 export.zip/export.xml(自动定位 zip 内 export.xml)，按天聚合步数/距离/卡路里/楼层/心率/静息/血氧/呼吸率/HRV/体重 + 按夜聚合睡眠，`source="apple_health"`，经 `HealthRepository.upsertDailyMerged` 合并(不覆盖手表)。账号授权页加「Apple 健康」卡片 + SAF 选文件，跑在 app scope(离开页面不中断)。`mergeDaily` 扩展为也合并压力/电量/血氧/呼吸/HRV(prefer-present，对 HC 同步无回归)。
  - 未动数据库结构(复用现有 `source`/字段，无需迁移)；未触碰 Garmin 认证链路。
- ✅ **2026-05-30 今日状态分 + 今日洞察 (跨维度恢复评分)**:
  - 调研 2025 主流可穿戴恢复评分范式 (Oura Readiness / WHOOP Recovery / Garmin·Firstbeat Body Battery)，三家收敛到「**个人基线归一 + HRV 主导加权**」: 每个生理指标都和用户自己最近 ~60 天常态比 z 分数，而非人群参考值；HRV 权重最高，静息心率次之，呼吸率/血氧主要当生病/过劳偏离信号。
  - 新增纯 domain 评分引擎 `domain/ReadinessEngine.kt`: 维度满配权重 HRV .28 / 静息 .18 / 睡眠 .25 / 压力 .10 / 身体电量 .08 / 呼吸 .06 / 血氧 .05，缺维度自动退出并**重新归一化权重**；基线 z→子分映射 (基线处 78 分，每 ±1 SD ±14 分，MIN_CV=0.04 防过敏)；基线不足 (<5 天) 时回退到有绝对意义的指标 (睡眠评分/压力/身体电量/血氧/静息心率的人群参考曲线)，HRV 无基线则不计入。输出 0-100 总分 + 各维度子分 + 档位 (优秀/良好/一般/偏低/需休息) + 置信度。
  - 新增 `domain/InsightGenerator.kt`: 按带优先级的**跨维度关联规则**产出候选，取最高优先级 1-3 条 (少而精)。规则示例: HRV↓+静息↑→欠恢复(ALERT)、呼吸率↑+血氧↓→呼吸/感染预警(ALERT)、睡眠为最弱项→拖累恢复、压力↑+电量低→负荷重、整体高+HRV在线→适合加量(正向)。
  - `TodayViewModel` 增加 60 天基线数据流 (日快照+睡眠)，在 combine 里算出 `StatusScore` 注入 `TodayUiState.readiness`；`TodayScreen` LazyColumn 顶部新增 `StatusScoreCard` (评分环动画 + 维度分解条 + 洞察卡)，套 Vita 深色主题与档位配色。未动数据库/认证链路。
  - **验证**: 评分核心逻辑已用等价 Python 复刻跑 4 个典型场景 (冷启动73/恢复良好95/欠恢复44/生病46)，分数与洞察触发均符合预期。Kotlin/Compose 语法靠人工审查，**完整 assembleDebug 仍需 charlotte 在 Android Studio 里过一次** (本机沙箱无 Android SDK)。
- ✅ **2026-05-31 显示修复 + 身体趋势 (中长期对比)**:
  - 亮点/短板分两行显示 (StatusScoreCard, maxLines=1 + 各自一行)；成就系统与情绪轨迹卡片对齐 (LifeScreen 把 MoodGlyph 包进 78dp 槽, 与成就 AchievementCrest 同宽, 文本列左缘对齐)。
  - 徽章背面寄语回归: `HabitBadge3DCard` 轻点徽章 (detectTapGestures, 与拖动旋转共存) 翻出 `badge.description` 半透明覆盖层, 再点收起。
  - **新增 `domain/TrendAnalyzer.kt`**: 在「今日状态分对比近日」之外, 加月度 (近30天 vs 上个30天) / 年度 (近90天 vs 去年同期90天) 对比。HRV/静息/睡眠/电量/压力/呼吸/血氧/体重逐项算两窗口均值, 按 HIGHER/LOWER/NEUTRAL 判改善/下降/平稳 (相对变化<3%、体重<1.5% 视作持平), 每维度给通俗解读; 样本不足该维度跳过, 两侧都不足返回 null。
  - `TodayViewModel` 把原 60 天基线流扩成 470 天历史流 (一次查询同时供评分 60 天基线 + 趋势), 算出 `monthTrend`/`yearTrend` 注入 state; `TodayScreen` 状态分卡下方新增 `BodyTrendCard` (月度/年度分段切换 + 总览句 + 各维度走向)。未动数据库/认证链路。
  - **月统计睡眠图只到2024 (真正原因+修复)**: 月/年睡眠的查询与分组逻辑确实对称, 数据没问题; 真正原因是**图表滚动**——月视图月份列很多 (从最早睡眠月一直到2026), Vico `CartesianChartHost` 默认停在最左 (最旧) 位置, 2026 在最右需手动滑到底才看得到; 年视图列少全部铺满屏幕所以 2026 直接可见。之前只设 `initialScroll=End` 无效, 因为数据是异步灌进 producer 的, 首帧 producer 还空 (位置0), 之后再也不会自动滚到末尾。**修复**: 三个图 (步数/睡眠/心率) 的 `scrollState` 改用 `rememberVicoScrollState(initialScroll=End, autoScroll=End, autoScrollCondition=OnModelSizeIncreased)` (内联在 `key(state.range)` 内, 切区间会重置), 模型从空→N 增长时自动滚到末尾, 月视图打开即落在最新 2026 数据。
  - **「昨天/今天第一个卡片一模一样」排查结论**: `StatusScoreCard` (状态分) 完全按所选日期算——`today` 来自 `repo.dailyRange(date, date)` 精确单日查询, 评分纯由该日快照派生, 不存在跨日期复用的代码路径。两天卡片相同只可能是底层快照本身相同: 大概率是**今天 (2026-05-31) 还没同步到真实数据**, 库里今天那行缺失/与昨天数值相同, 引擎对两天打出同分。需 charlotte 先同步一次今天, 再对比两张卡上的 HRV/静息/睡眠原始数值是否真的不同来确认。
- ✅ **2026-05-31 运动明细区间 + 佳明会话保活**:
  - **运动明细列表区间错乱**: `StatsScreen` 的「运动明细」直接用了图表为铺历史而拉的宽窗口 (`from`): 选「日」是 `today.minusDays(6)` (显示最近一周), 选「月/年」是 1900 起 (显示全部历史), 与所选粒度不符。修复: `StatsViewModel` 新增 `detailExercises`, 按当期窗口过滤 (日=今天 / 周=本周 startOfWeek / 月=本月1号 / 年=今年1月1号 / 全部=全部), state.exercises 改用它; 图表用的 `aggDaily`/`aggSleeps`/`exerciseItemsForGrid` 不受影响。
  - **佳明登录一段时间自动退出 (charlotte 授权「最小改动」)**: 根因在 `GarminSyncManager.kt` 同步循环——只要任一接口返回 401/403 就无条件 `authClient.logout()` 清掉整个会话 (含未过期 refresh token), 单个接口瞬时 401 也会被踢下线。修复 (不碰 SSO/cookie/OAuth/登录流程本身): `GarminAuthClient` 新增**只读** `canRefreshSession()` (判断 DI refresh / OAuth1→2 refresh token 是否仍可续期); 同步层 401/403 改为「仅当 `!canRefreshSession()` 才真正 logout」, 否则保留登录态 (stoppedByAuth=false + 友好提示), 下次同步由 `ensureAccessToken` 用 refresh token 自动换新 access token。**认证冻结约束仍生效——本次只读 + 同步层 401 处理, 未改登录链路。**
  - **关联**: 上面「昨天/今天卡片一样」+「洞察与感受不符」大概率正是被此自动退出连累——今天 (5/31) 没同步到真实数据, 引擎拿陈旧/缺失数据打同分。修好保活后, charlotte 同步一次今天再看两天 HRV/静息/睡眠原始值是否真的不同。
- ✅ **2026-05-31 洞察去模板化 + 月睡眠15h修复 + 全量页瘦身 (charlotte 三问)**:
  - **今日洞察套模板 (分数相近就一字不差)**: 根因是 `InsightGenerator.kt` 每条规则的文案是写死的常量, 只按档位/阈值切换, 不含当日真实数值——两天只要触发同一规则, 输出就完全一样。重写 (公开签名不变): 新增 `factLine`/`factsFull`/`topFacts` 把当日真实读数 (HRV xx ms、静息 xx bpm、睡眠 xhxm、压力、电量、总分) 及与 7 日均值的偏离 `deltaText` 嵌进每条「现象」开头, 所以底层数值不同→文案就不同; 「原理/建议」保留为稳定科普。8 条跨维度规则 + 兜底全部改成带数据。**注意修了一个 `$factsFull(...)` 漏花括号的插值 bug (规则①), 已确认其余处都是 `${...}`。**
  - **月度睡眠某月显示 15h (不可能值)**: 根因在 `StatsViewModel.aggregateSleep`——把一个月内所有 `SleepSession` 的 `totalMinutes` 全部累加再除以「条数」, 同一晚若有多条记录 (多数据源/小睡/碎片) 就会抬高甚至失真。修复: ① 先过滤单条 ≤0 或 >16h(960min) 的脏记录; ② 按 `sleepDisplayDate` 分组, 每晚只取最长的一条主睡眠; ③ 再对各晚主睡眠求平均, 物理上不再可能 >16h。`buildRangeSummary` 的 `sleepAvg` 同类逻辑一并修正。
  - **「按月睡眠/心率图只到2024」**: 经查 `StatsScreen` 三个图各自独立 `mapNotNull` 过滤零/空、各用自己过滤后的序列建 formatter, 且已用 `rememberVicoScrollState(initialScroll/autoScroll=End)`——不是渲染 bug, 是**数据覆盖**问题: 较近月份缺手表睡眠/心率信号 (大概率被同日修掉的佳明自动退出连累), 只有手机/HC 步数在续。需 charlotte 在保活修复后重新同步近几个月, 图自然补齐。未改这部分代码 (不造数据)。
  - **全量数据页重复 + 技术语言 + 老版全量 dump**: `GarminDataDetailScreen` 之前同时渲染 `structuredBlocks` (干净结构化卡: 日汇总/生理期/睡眠/运动/全天心率/身体电量, 来自 Room 正式表) 和 `displayRecords` (老版本对 `garmin_raw_record` 逐接口 `GarminRawDisplay.describe` 的原始 dump), 后者就是重复 + 技术化的来源。修复: 删掉 `displayRecords` 计算与其 `items(...)`/`GarminRawPayloadCard` 渲染、空判简化为只看 `structuredBlocks`、`FullDataHeader` 去掉 `garminReadableCount` 参数, 文案改「N 类健康数据」。raw JSON 仍完整存本机/备份, 只是不再在 UI 堆原始项。**`GarminRawPayloadCard`/`GarminRawChart`/`smoothPath`/`accentFor`/`titleFor`/`sourceLabel` 现成孤立死代码 (仅警告不报错), 暂保留未删, 避免无 SDK 误删引入编译错。**
- ✅ **2026-05-31 统计周/月/年改「窗口内逐期展开 + 翻页」(charlotte: 单月列出没意义)**:
  - 旧逻辑把所有月份/年份铺成一条无限时间轴 (要手动滑到最右才看到最新), charlotte 觉得「单月列出, 数据没意义」。改为**固定窗口 + 翻页**: 周=所看周 7 天日柱 / 月=所看月按【天】展开 (28-31 根日柱) / 年=所看年按【月】展开 (12 根月柱)。
  - `StatsViewModel`: 新增 `_anchor` (周期锚点) 进 combine (现 5 参); 用 `periodStart/periodEnd` 算当期窗口, 图表只查该窗口 (`repo.dailyRange(from, periodEnd)` 等) 而非一路查到今天; `aggDaily`/`aggSleeps`/`exerciseItemsForGrid` 的 Month 改精确按天匹配、Year 改 `withDayOfMonth(1)` 按月匹配; xLabels Month=日号、Year=「N月」; 新增 `periodLabel`/`canGoNextPeriod` 注入 state; 新增 `previousPeriod`/`nextPeriod`(不越过今天)/`jumpPeriodToToday`; `setRange` 切粒度时把 `_anchor` 拉回今天。`aggregateDaily` 的 `sumTotals=Month||Year` 保留 (Month 网格现逐天 1 项, sum==value 无害; buildRangeSummary 仍依赖 Month=求和)。
  - `StatsScreen`: 图表区 (`key(state.range)`) 顶部新增 `PeriodNavigator` (‹ 周期标题 ›, 下一期到顶禁用 + 「今日」chip), 接 `vm::previousPeriod/nextPeriod/jumpPeriodToToday`。运动明细/日历不受影响。
- ✅ **2026-06-10 算法升级 + 周期数据丢失修复 (charlotte: 三项优化)**:
  - **修真 bug·HC 周期同步覆盖手动记录**: `CycleRepository.syncFromHealthConnect` 之前直接 `dao.upsertAll`，每次打开生活页都会把用户手动记录的经量/症状/起始日标记冲掉 (且 periodRecords 分支漏打 source，被误标成 manual)。改走 `upsertExternalPreservingManual` + 正确打 `source="health_connect"`。顺手删了 CycleRepository 死代码 `averageCycleLength`/`predictNextPeriodStart`/`getAll` 和 `CycleDao.periodStarts`。
  - **周期分段更宽容**: `CycleLogLogic.groupIntoPeriods` 容忍漏记 1 天 (gap≤2 仍算同段)；`CyclePeriod.days` 改按日历跨度算，漏记中间一天不再把一段经期拆成两段/算短。
  - **贝叶斯预测加时间衰减**: `BayesianCycleModel.updatePosterior` 只取最近 12 个周期、按 0.9 指数加权，用有效样本量 n_eff=(Σw)²/Σw² 代入共轭更新 (等权时退化为原公式)——节奏变化后几年前的旧周期不再与上个月等权。`predict`/`getDayStatus` 纯函数化 (`today` 注入参数)。已用 Python 复刻验证: 稳定 28 天→28.0；近期变长场景加权 29.96 vs 旧等权 29.31，更跟手。
  - **LifeViewModel 周期数据流简化**: 预测/今日状态/经期第几天改为在 combine 里直接由 entries 派生 (删掉 `_prediction`/`_todayStatus`/`_todayPeriodDay` 三个 MutableStateFlow、init 里的重复 collect 和各处手动 `refreshPrediction()`)，entries 与 prediction 不再有不一致窗口。
  - **状态分修基线污染**: `ReadinessEngine` 睡眠基线之前直接拿 60 天内所有 SleepSession 平均 (含小睡/碎片/重复来源/当晚自身)。新增 `primaryNightMinutes`: 每晚只取最长主睡眠、剔除 >16h 脏数据、排除评估当晚。evaluate 新增 `zone`/`monthTrend` 可选参数。
  - **洞察真正结合短期+长期**: 引擎新增逐指标 `streakDays` (与基线偏差 >0.5 SD 的连续天数，缺数据即断，宁可低估)；`InsightGenerator` 重写——接收 30 天趋势 (monthTrend)，每条洞察先判「单日波动 vs 持续走弱」(streak≥3 或月度同指标走低)，两种情况标题/结论/建议都不同；新增规则「今天没短板但月度走低→中期信号」(overall 55-79 时替代泛泛的『保持节奏』)；正向洞察会引用月度改善/走低做确认或提醒。`factsFull` 自动拼「当日值 + 比近7天 ±x + 已连续 N 天偏离」。删掉 generate 里从未使用的 `hrvBase`/`today` 参数。**注意: Kotlin 字符串模板 `$var` 后面直接跟汉字会把汉字并进标识符 (CJK 是合法标识符字符)，必须写 `${var}汉字`——本次踩过一次。**
  - **轨迹评分口径统一**: `DailyBriefAnalyzer` 近 7 天轨迹点之前用全量多年历史当基线、今天却用 60 天，曲线不可比；改为每个轨迹点各自往前 60 天窗口。
  - **验证**: 本机命令行 `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug` 编译通过；加权后验数学用 Python 复刻 5 个场景符合预期。
- ✅ **2026-06-10 二轮 (charlotte 反馈「变化不明显」三连)**:
  - **经期逐日编辑** (此前完全不可编辑, charlotte 最不满的点): LifeScreen 历史经期与点滴出血合并成按时间倒序的统一「出血记录」时间线 (按年分组, >4 条折叠)。点经期行打开 `PeriodEditorSheet` (ModalBottomSheet): 列出该段前一天~后一天每一天, 逐天五档经量 (无=删除该天, 含补录中间漏记天/前后扩一天)、逐天症状 chips、任意一天「设为起始日」。即点即存, 数据流刷新自动跟上; 编辑器用 `editingPeriodAnchor` (原起始日 ±2 天容差) 在刷新后重新匹配段落, 段删光自动关。LifeViewModel 新增 `setDayFlow` (会清掉「经间出血」note, 该天并入经期; 编辑过即 source=manual 不再被外部覆盖)/`setDaySymptoms`/`setPeriodStartDay`。预测起始日改为**每段各取显式起始日否则首日**, 不再「只要有一段标了起始日就把没标的段整段排除」。
  - **今日页扁平化** (charlotte: 不要一层套一层、不关心分数怎么算的): StatusScoreCard 展开区删掉「如果只看各项读数约 X 分…按 Y 分安排」和免责声明段, 只留: 需要留意的短板 (limits 一行一条) → 各维度条 → 洞察卡; 洞察的「原理/就医」从底部弹层改为**卡内原地展开** (删 InsightDetailSheet/「更多说明」标题/「展开说明›」); 按钮文案「看看为什么」→「展开身体状态」。**月度/年度对比卡 (BodyTrendCard) 从今日页整体移除并删文件**; `TrendAnalyzer.TrendWindow.YEAR` (近90天 vs 去年同期) 按 charlotte 要求整个删除, TrendWindow 只剩 MONTH (仅作洞察引擎输入, 不再单独展示); TodayUiState 删 yearTrend。长期变化统一归趋势页 (StatsScreen `LongTermTrendCard`)。
  - **长期变化加「对我意味着什么」**: `LongTermTrendAnalyzer` 每项变化新增 `meaningText` (按指标×方向定制: 睡眠缩短换算成全年少恢复 N 小时、静息心率抬高列常见原因并提示对照同期步数/睡眠、步数下降提示回看是哪年开始/找回日常移动比新开训练容易等) + 报告级 `synthesis` 跨指标互证 (睡眠↓+静息↑=恢复输入少负荷高、步数↑+静息↓=心肺实质变强、步数↓+体重↑=同一生活变化两面、睡眠↓+压力↑=循环提醒)。StatsScreen 渲染 meaning + synthesis。
  - **修 bug·睡眠日期归属** (charlotte 发现 6月6 显示两条、其一实为 6月7 的): `SleepDetailScreen` 之前按**入睡时间**标日期, 与全 app「跨夜睡眠归醒来那天」口径不一致——6日晚睡到7日早的觉被标成 6日, 和 6日小睡挤在一起。改为 endEpochMs>startEpochMs 时按醒来日期显示。
  - 验证: assembleDebug 命令行编译通过。
- ✅ **2026-06-10 三轮 (charlotte: 今日页还是套来套去) → 今日页零开关**:
  - 上一轮只是减少了层级, charlotte 仍觉得「很多层次」。这轮把今日页做成**零开关线性滚动**: 任何内容都不需要点开。
  - `StatusScoreCard` 拍平成单卡固定阅读顺序: 分数环+一句判断(`brief.headline`+较昨日) → 各维度条(常驻) → 1-2 条洞察全文(title+body, **不可点**, 删 detail 展开/「原理与建议›」) → 近7天迷你轨迹(并入本卡) → 一行参考天数。删「展开身体状态」按钮、`brief.observation`/`action` 渲染 (与洞察重复报数, 数字只在维度条出现一遍、叙述只在洞察出现一遍; DailyBrief 模型字段保留未删)。
  - **`LifeTrajectoryCard` 删除** (文件已删): 7天曲线+discovery 并入 StatusScoreCard 底部 (`TrajectoryStrip`), 不再单独成卡占一个标题。
  - **「查看全部今日数据」开关删除**: MetricCard 列表常驻直接滚动可见 (显示哪些项仍由设置页 `enabledMetrics` 控制)。
  - 今日页最终结构: 状态卡(单张) → [异常事件卡, 仅有事件时] → 数据卡片 → 运动记录。全页无任何 toggle。
  - 验证: assembleDebug 通过。**经验: charlotte 对「层级」的容忍度是零开关——「能不能再简单明了」=直接全部铺开, 靠编辑内容控制长度, 不靠折叠。**
- ✅ **2026-06-10 四轮 (charlotte: 判断只报数没意义 + 删过头了 + 关联分析再挖掘)**:
  - **「零开关≠把内容删光」——charlotte 明确说『我只说了不要嵌套, 我没说把什么都删了』**。三轮砍掉的 observation/action 渲染恢复; 同时把文案全部改成「讲意义」: `DailyBriefAnalyzer.headline` 重写为有结论的判断 (按档位+主要短板, **不含数字**, 如「昨晚睡眠没补够, 今天适合减量」); `observation` 重写为状态叙述 (driverPhrase/statePhrase 人话点名消耗与亮点 + 跨维度结论, 不堆数字——数字只在维度条); 删掉只为报数服务的 `reasons`/`BriefReason`/`contributionContext`/`reasonPriority`/`hasMeaningfulDelta`。
  - **近 7 天走向一句话常驻**: DailyBrief 新增 `trajectorySummary` (升/降≥8 分讲恢复大于消耗/消耗大于恢复, 否则「X–Y 之间节奏平稳」), 显示在 7 天曲线上方; `discovery` 删掉与之重复的 7 天分差分支, 只保留睡眠规律/月度趋势/连续睡够等长线发现。状态卡最终顺序: 环+判断+较昨日 → 叙述+建议 → 维度条 → 洞察 → 近7天一句话+曲线 → 参考天数。
  - **「可能有关的事」(LocalAssociationAnalyzer) 升级**: 新增两个高价值话题——`bedtimeAndSleep` (以个人入睡时刻中位数分早睡/晚睡两组比时长, 入睡时刻以中午12点为日界折算避免凌晨入睡算「早」) 和 `exerciseAndNextDayRecovery` (运动量高低两组比次日恢复指数); 既有发现的 note 全部改成条件式「对我意味着什么」(如运动助眠→『状态低迷时动一动比躺着解乏』/ 运动后睡得少→『留意练得太晚或强度过头』)。**UI 重排 (StatsScreen)**: 有结论的话题放前面成卡 (note 升为正文色), 没攒够数据的压成「还在观察的话题」一行小字, 不再 8 个等大空盒子。
  - 验证: assembleDebug 通过 (中途修了 observation 双句号拼接)。
- ✅ **2026-06-10 五轮·信息架构重组 (charlotte: 页面功能归属乱, 讨论后拍板)**:
  - **底部 tab 按用户动作划分: 今日(看今天) / 记录(记一笔) / 数据(查数据) / 趋势(读解读)**, 设置移出 tab 栏、从今日页右上角齿轮进入 (路由仍是 `settings`, 系统返回键退出)。`TopDestination` 现为 Today/Record("life")/Data("stats")/Trends("trends") 四项, 路由字符串沿用旧值避免破坏 restoreState。strings.xml 删 nav_stats/nav_life, 新增 nav_record/nav_data/nav_trends。
  - **StatsScreen 拆分**: 「你的长期变化」+「可能有关的事」两张解读卡连同渲染逻辑迁到新 `ui/screens/trends/TrendsScreen.kt` (+`TrendsViewModel`: 全量历史 + lifestyle 流, 算 LongTermTrendAnalyzer/LocalAssociationAnalyzer); StatsScreen 只剩数据日历、周期翻页图表、汇总、运动明细, StatsViewModel 删掉 lifestyleFlow 和 habit/mood/cycle/weather 四个依赖 (构造参数只剩 healthRepo+prefs, ViewModelFactory 已同步)。睡眠图卡底部加「每晚睡眠明细 ›」入口 (sleep_detail)。
  - **LifeScreen 变纯记录页**: 顺序=习惯打卡 → 成就一行入口 (`AchievementEntryRow`: 「成就 N/M · 最长连续 X 天 ›」替代整张 HabitBadgeSummaryCard, 庆祝弹窗不受影响) → 情绪 → 生理期。**删除生活概览卡** (LifeOverviewCard/LifeSignalPill/lifeOverviewSentence, 与今日页重复)、**删除近期睡眠区块** (归数据页); LifeUiState 删 recentSleeps, LifeViewModel 删 sleepRange 流。
  - 验证: assembleDebug 通过。HabitBadgeSummaryCard (HabitBadgeScreens.kt) 暂成无调用方的公开组件, 保留未删。
- ✅ **2026-06-13 AI 洞察模块 (局域网桥接 Mac + Claude 订阅额度, 零额外花费)**:
  - **计费结论 (查证过)**: 2026-06-15 起 Pro/Max 订阅自带每月 Agent SDK 额度 (Pro $20 / Max5x $100 / Max20x $200), 官方覆盖自有项目 Agent SDK、`claude -p`、订阅认证的第三方 app; 额度用完**默认停止、不自动扣费**。把订阅 OAuth token 抠出来当 API key 嵌 app 违反条款; 纯 API key 要另充钱 (charlotte 红线: 不为 AI 额外花钱)。charlotte 还否决了手动导出导入、用不了 Tailscale → 最终形态: **app (同 Wi-Fi) → Mac 本机服务 → `claude -p`**。6/15 后需在 Claude 账户里领取一次额度。
  - **Mac 端** `ai-server/vita_ai_server.py` (纯 Python 标准库): POST /analyze 收数据 → 拼中文健康分析提示词 (按 charlotte UX 原则: 讲身体不讲算法、少报数、给可执行建议、不诊断) → `claude -p --output-format json` → 容错抠出 JSON 返回 `{overall, findings, actions, watch}`; GET /ping 探活; **安全边界改为默认只监听 127.0.0.1, 不做自动发现广播; 手机同 Wi-Fi 访问必须显式 `VITA_AI_ALLOW_LAN=1` 且设置 `VITA_AI_TOKEN`**; 附 launchd plist (开机自启) 和 README。`VITA_AI_PORT/VITA_AI_MODEL/VITA_CLAUDE_BIN/VITA_AI_TOKEN` 可调。
  - **App 端** `data/ai/`: `AiInsightModels` (短字段名 wire DTO 省 token); **`AiInsightProvider` 接口——为将来普世化预留的关键抽象** (换自有后端 / Sign in with Claude / 用户自带 key / 端侧模型时只换实现, 打包/schema/UI 不动); `LanBridgeInsightProvider` (只读设置页手填地址和口令, 不走 NSD 自动发现; OkHttp, 读超时 10 分钟, 请求带 `Authorization: Bearer <token>`); `AiInsightManager` (跑 app scope 离开页面不断; 打包近 365 天聚合日指标 + 每晚主睡眠(同晚取最长归醒来日) + 180 天运动 + 730 天周期 + 情绪 + 习惯打卡; **不含原始曲线/Garmin raw/自由文本备注**——数据范围是 charlotte 拍板的「聚合+关键明细」; 结果 JSON 存 DataStore, 离线/出门可回看)。
  - **UI**: 趋势页顶部新增「AI 洞察」卡 (VitaActive 琥珀): 分析时间+覆盖天数 → 总评 → findings 块 → 可以试试 → 继续观察 → 分析/重新分析按钮 (分析中可离开) + 免责声明, 零折叠全铺开; 设置页·通用设置卡新增「AI 分析服务器」地址和口令输入, 明确自动发现已关闭。`SettingsPreferences` 新增 `ai_server_address` / `ai_server_token` / `ai_last_insight`。
  - **普世化结论** (charlotte 问「推广出去这模式普世吗」): 不普世——我的 Mac/我的额度/同 Wi-Fi 都是个人脚手架。普世路径: ① 自有后端+开发者付费 (主流消费产品模式) ② Sign in with Claude (用户用自己订阅的 Agent SDK 额度, 需薄后端, 用户得有订阅, 国内用户难) ③ BYOK 多供应商 (可接国内便宜 API, 极客向) ④ 端侧小模型 (守住纯本地, 质量弱)。已靠 Provider 接口隔离, 届时 payload/洞察 schema/UI 全部复用。
  - **双后端 (charlotte 拍板: 不确定长期订 Claude 还是 ChatGPT, 看价格和体验)**: `vita_ai_server.py` 支持 `VITA_AI_BACKEND=auto/claude/codex`——claude 走 `claude -p` (Claude 订阅额度), codex 走 `codex exec --skip-git-repo-check -o <tmp> -` (stdin 进提示词, ChatGPT Plus/Pro 订阅自带 Codex 用量)。auto 按 claude→codex 顺序尝试且**失败自动切换下一个** (应对停订但命令还装着/额度用完; 超时除外, 不重试)。切换只在 Mac 端, **app 零感知零改动**。codex 路径未实测 (charlotte 暂无 codex 登录), 用法已对官方文档核对。
  - 验证: assembleDebug 通过, server 过 py_compile; 2026-06-13 安全补丁新增 `ai-server/test_vita_ai_server.py` 覆盖无自动发现广播、LAN 必须有 token、客户端不走 NSD 且带 Bearer token。**待真机**: 手填地址+口令后端到端跑一次真分析。
- ✅ **2026-06-13 情绪改时刻制 (charlotte: 情绪/天气一天会变好几次, 又常忘记记)·Phase 1**:
  - **问题与方向**: 情绪/天气原本「每天一格覆盖」(PK=date), 与全 app「按时间统一」原则不符, 也丢掉一天内的起落; 忘记记的日子又是空缺。讨论后 charlotte 拍板: 情绪改**时刻制 + 自动聚合**, 天气改 **Open-Meteo 自动取**(Phase 2)。本轮先做情绪 (自包含, 不加新权限)。
  - **数据层**: `MoodEntry` 主键 `date`→`id`(String, 仿 HabitDefinition, 仓库生成 UUID), 加 `recordedAtEpochMs`, 一天可多条; DB v11→**v12**, `MIGRATION_11_12` 重建表 (老每日记录各转一条时刻: id=旧date, 时刻=原 updatedAtEpochMs), 无损。`MoodDao` upsert-by-date 改 insert/`updateContent`/`deleteById`/`deleteByDate`; `MoodRepository` 改 `addMoment`/`updateMoment`/`deleteMoment`/`clearDay`。`MoodEntry.id`/`recordedAtEpochMs` 给默认值仅为旧备份反序列化兼容, `BackupManager` 导入时补 id/时刻; 正常写入一律由仓库给真实值。
  - **聚合**: 新增 `domain/MoodAggregator.kt` (`MoodMoment`/`MoodDaily`): 当天多条→均值价 / **当日波动度(max-min, 时刻制独有的新信号)** / 主导情绪(次数最多, 并列取较晚) / 末次情绪。情绪「价 1..5」和正负集合**中心化到 `MoodCatalog.valence`/`POSITIVE_MOODS`/`NEGATIVE_MOODS`**(原先 LocalAssociationAnalyzer/AnomalyEventEngine 各硬编码一份, 已统一)。
  - **分析升级**: `LocalAssociationAnalyzer` 既有「情绪×恢复/睡眠/天气」改用 `MoodDaily` 聚合 (多条不再被 associateBy 吞成一条; 天气改用 avgValence); **新增两条**: `moodVolatilityAndStress`(只看一天≥2 次的天, 按波动中位数分组比压力) + `timeOfDayMood`(上午<12点 vs 傍晚≥18点 比均值价)。`AnomalyEventEngine.moodStreak` 改取每天主导情绪。`AiInsightManager` 情绪改按天聚合发主导情绪 (省 token)。
  - **UI**: `MoodJournalScreen` 今天做成时刻时间线 (时间·glyph·标签·备注, 点条目改/删) +「＋ 记此刻」, 过去某天点开 `MoodDayEditorSheet`(ModalBottomSheet, 仿 PeriodEditorSheet) 补记/编辑/清空, 记录可带「随手记一句」备注; 过去某天默认落当天中午、今天落此刻。`LifeScreen` 情绪卡改「今天 N 次·主导 X」+ 7 天主导迷你条。天气本轮**完全不动** (仍手动, 留 WeatherPickerDialog), Phase 2 再改自动。`LifeUiState.moods:Map<String,MoodEntry>`→`moodDaily:Map<String,MoodDaily>`。
  - **验证**: assembleDebug 通过 (Room v12 schema 生成 OK)。**待真机**: 迁移后旧情绪是否各转成一条、一天多记几次后聚合/波动/早晚分析是否如期。
- ✅ **2026-06-13 天气 Open-Meteo 自动取·Phase 2**:
  - **方向**: charlotte 拍板天气改自动 (手动太稀疏、攒不够样本)。停止手动选, 用大致位置 + Open-Meteo (免费无 key 无注册) 自动取。
  - **数据**: `WeatherEntry` 加 `source`(manual/auto)/`tempMaxC`/`tempMinC`/`weatherCode`; DB v12→**v13**, `MIGRATION_12_13` 加列 (已有行默认 source='manual')。`WeatherDao` 加 `manualDates()`; `WeatherRepository.upsertAuto` **跳过手动记录过的日期**, 不覆盖历史手动天气。
  - **取数**: `data/location/LocationProvider`(只读系统最后已知位置, 不主动定位、不引 Play 服务) + `data/weather/OpenMeteoClient`(OkHttp + org.json, 取 `daily=weather_code/temp_max/temp_min&past_days=7`) + `domain/WeatherCodeMapper`(WMO code→WeatherCatalog id) + `data/weather/WeatherSyncManager`(app scope; 自检开关+权限→拉近 7 天→upsertAuto)。`VitaApplication.onCreate` 触发一次, 设置里开启时再触发一次。
  - **权限/UI**: Manifest 加 `ACCESS_COARSE_LOCATION`; 设置页·通用卡新增「自动获取天气」开关 (开启时用 Compose `RequestPermission` launcher 申请位置, **默认关闭, 不在未同意时联网/取位置**)。`MoodJournalScreen` 去掉手动天气选择器 (WeatherPickerDialog/WeatherPickItem 删除), WeatherChip 改只读展示, `LifeViewModel.setWeather/clearWeather` 删除。`SettingsPreferences.weatherAutoEnabled`。
  - **待真机**: 授权位置后能否取到天气 (依赖系统有缓存位置, 全新设备 lastKnown 可能为 null→当次不填); WMO code 映射是否合理。
- ✅ **2026-06-13 记录提醒·Phase 3 (解决「一整天想不起来记」)**:
  - `data/reminder/`: `ReminderScheduler`(AlarmManager 非精确每日重复闹钟, 无需精确闹钟特殊权限; 建通知渠道 + 弹「现在心情怎么样?」通知, 点开进 app; 没通知权限静默跳过) + `MoodReminderReceiver`(到点弹通知) + `BootReceiver`(重启重建闹钟, BOOT_COMPLETED)。最多 4 个时刻。
  - **权限/UI**: Manifest 加 `RECEIVE_BOOT_COMPLETED` + 注册两个 receiver (`POST_NOTIFICATIONS` 已有)。设置页新增「记录提醒」卡: 总开关 (开启时 13+ 申请通知权限) + 4 个预设时刻 FilterChip (9/13/18/21 点, 至少留一个)。`SettingsPreferences.reminderEnabled`/`reminderTimes`(默认 13:00,21:00)。开关/时刻变动即 `reminderScheduler.reschedule()`。
  - **验证**: 三阶段 assembleDebug 全通过。**待真机统一验证**: 提醒到点是否弹、点开是否进 app、重启后是否还在; 天气授权后是否填充。
  - **接线**: `AppContainer` 注册 MIGRATION_12_13 + `weatherSyncManager`/`reminderScheduler`; `SettingsViewModel`/`ViewModelFactory` 加两个依赖。未触碰 Garmin 认证链路。
- ✅ **2026-07-01 AI 服务运维 (「连接不上」三连排查)**: 手机报「连接不上」实为三层问题逐一剥开: ① Mac 局域网 IP/口令与 app 里存的不一致 (老口令混 `I/l`/`O/0` 难手打, 已换好打的新口令); ② 服务此前是 `launchctl submit` 临时任务, 重启即失 → 已固化为持久 LaunchAgent `~/Library/LaunchAgents/com.vita.aiserver.plist` (RunAtLoad+KeepAlive, 含口令, **不进 git**); ③ 默认后端 Opus 跑 230 天数据超出 app 10 分钟读超时, 而读超时和连接失败在 `LanBridgeInsightProvider` 里是同一句「连不上」文案 → LaunchAgent 加 `VITA_AI_MODEL=claude-sonnet-4-6`, 全年量级 (330 天 95KB payload) 实测 194s 完成。排查口诀: 手机浏览器开 `http://IP:8787/ping`, 回「未授权」= 网络通只是口令/地址错, 打不开 = 网络/代理问题 (charlotte 手机有 Clash 类代理, 全局模式会吞局域网请求)。
- ✅ **2026-07-02 AI 洞察双通道: 新增 API 直连 (charlotte: 回传电脑太麻烦, 常因连接失败; 原「零额外花费」红线由她本人解除)**:
  - 设置页「AI 分析」两种方式 FilterChip 切换: **Mac 桥接** (原局域网服务, 订阅额度) / **API 直连** (手机直接调大模型 API, 自带 Key, 出门/Mac 关机也能用)。`ModeRoutingInsightProvider` 每次分析现读 `prefs.aiMode` 路由, 切换不用重启; AiInsightManager 零改动。
  - `data/ai/DirectApiInsightProvider.kt` 双协议: OpenAI Chat Completions (DeepSeek/Kimi/通义/智谱/Gemini 兼容端点/OpenRouter/自定义通吃) + Anthropic Messages (Claude 官方)。`AiApiPresets` 9 预设, 选中自动定 base URL/协议/默认模型, 三者皆可覆盖; 切预设清 base/model 覆盖但保留 Key。OpenAI 路径刻意不发 temperature/max_tokens (各家参数名不统一, 省略最兼容), Anthropic 发 max_tokens=8192。错误体按 `{"error":{"message"}}` / `{"error":"…"}` / `{"message"}` 三形状抠人话。`testConnection()` 发最小请求, 供设置页「测试连接」按钮 (先存后测, 结果行内绿/红)。
  - `data/ai/AiAnalysisPrompt.kt`: Mac 端 PROMPT_TEMPLATE+FIELD_GUIDE 的 Kotlin 移植——**提示词现有两份, 改一份必须同步另一份** (LAN 路径仍由服务端拼)。
  - SettingsPreferences 新增 `ai_mode`/`ai_api_preset`/`ai_api_base_url`/`ai_api_key`/`ai_api_model` (Key 明文存本机 DataStore)。
  - **「分析用改过的数据」已核实**: 两条通道共用 `AiInsightManager.buildPayload()`, 只读 Room (dailyRange/sleepRange/exerciseRange/observeRange)——经期手动编辑 (source=manual 防外部覆盖)、运动删除 (`!isDeleted`)/自定义分类、情绪修改天然生效; Garmin raw JSON 从不参与分析。
  - 验证: assembleDebug 通过。**待真机**: 填真实 Key 点「测试连接」+ 跑一次完整分析 (请求形状按各家文档写, 未实测; 预设默认模型名会过时, 输入框可随时覆盖)。
- ✅ **2026-07-03 自定义情绪 + AI 分析区间 + 睡眠分期图 (charlotte 三连)**:
  - **自定义情绪**: 新表 `custom_mood` (id/label/colorHex/shape/valence/isArchived), DB v13→**v14** (`MIGRATION_13_14`)。`MoodCatalog` 改为「内置 8 种 + 运行时注册表」: MoodRepository 在 app scope 观察 custom_mood 持续灌入, `valence()`/`POSITIVE_MOODS`/`NEGATIVE_MOODS` 按价 (≥4 正 / ≤2 负) 自动覆盖自定义, 各分析器零改动; **UI 选择器走 LifeUiState.customMoods (状态流) 保证重组**, 不要依赖 object 变化触发重组。情绪选择弹窗末尾加「＋新情绪」格 → 编辑器 (名字≤6字/五档倾向/12色/7造型, MoodGlyph 实时预览); 长按自定义项修改/删除。**删除=归档**: 历史记录仍能解析名字/颜色/价。备份 envelope 加 `customMoods` (旧备份 ignoreUnknownKeys 兼容)。AI payload 的 moods 从发 id 改发**情绪名** (自定义 id 是 UUID 无语义)。
  - **AI 分析区间可选 + 按钮显眼 + 结果重排** (charlotte: 每次都是一大堆不变的长期内容, 短期在最下面): 趋势页 AI 卡顶部常驻「近三月/近一年/多年全景」FilterChip + 全宽琥珀主按钮 (不再是结果末尾的小 TextButton)。区间存 `SettingsPreferences.aiAnalysisRange` (**默认近一年**); 近三月=90 天明细、近一年=365 天明细、两者都不带 longTerm, 只有多年全景才带多年摘要——不选就不会重复老结论, 且不带 longTerm 时历史查询只查窗口内 (省全库扫描)。payload 加 `windowDays`、AiInsight 加 `rangeKey` (老缓存 JSON 兼容)。结果顺序改为: 总评 → **这段时间的规律 (前)** → 可以试试 → 多年变化 (最后) → 继续观察。**两份提示词已同步改** (AiAnalysisPrompt.kt + vita_ai_server.py): 加区间规则「没有 longTerm 时 longTermFindings 必须返回 []」; server 7 项测试通过。
  - **睡眠分期图 (催眠图)**: 今日页睡眠/睡眠评分卡可点 → 新路由 `sleep_day/{date}` (`SleepDayDetailScreen`+VM, 支持前后天翻页); 数据页「每晚睡眠明细」每行也可点入。新 `domain/SleepStageParser.kt` 从 `garmin_raw_record`(categoryKey="sleep") 的 dailySleepData JSON 解析 `sleepLevels` (GMT 时间, activityLevel 0=深/1=浅/2=REM/3=醒), 排序+合并相邻同分期; **不动数据库**。页面: 主睡眠汇总 (入睡/醒来/总时长/评分) → 整晚四行分期 Canvas 图 (清醒/REM/浅睡/深睡, 阶梯连接线+时间轴) → 各阶段占比条 → 当天小睡列表; 无分期数据 (HC/三星等来源) 退化为汇总+一行说明。
  - 验证: assembleDebug 通过。**待真机**: 佳明账号确认 sleepLevels 真实返回并渲染; 自定义情绪建/改/删 + 迁移后旧数据无恙; 三档区间各跑一次分析看结果贴不贴。
  - **二轮 (charlotte 反馈)**: ① **按区间分开缓存**——`saveAiInsight(range, json)` 存 `ai_last_insight_<range>`, 展示流 `prefs.aiAnalysisRange.flatMapLatest { manager.lastInsightFor(it) }` 跟随所选区间, 切区间显示各自上次结果; 老单份缓存 (`ai_last_insight`) 只作回退 (rangeKey 缺失按「全部」算), 不再写入。② 「多年变化」区块只在 rangeKey=all (或老缓存 null) 时渲染, 短区间即使模型多写也不显示。③ 区间 chip 改等宽 weight(1f) + maxLines=1, 第三档文案「多年全景」→「全部」, 不再换行。④ 短窗口提示词让模型少写 (windowDays≤120 时 recentPatterns 4-6 条, 两份提示词同步)——**分析耗时主要在模型生成端**, payload 大小影响不大, 换更快的模型 (Mac 端 VITA_AI_MODEL / API 直连选轻量模型) 才是数量级提速。
- ✅ **2026-07-05 睡眠可手动修正 + 今日页 AI 解读 (charlotte 两问)**:
  - **跨时区睡眠错乱可修正** (charlotte 发现旅行期间睡眠没换算当地时间, AI 把「凌晨4-6点睡」当真实作息): `SleepSession` 加 `isEdited`/`isDeleted`(软删墓碑)/`originalStartEpochMs`/`originalEndEpochMs` 四列, DB v14→**v15** (`MIGRATION_14_15`)。修正入口在 `sleep_day/{date}` 页 (数据页睡眠明细行/今日页睡眠卡都能进): 主睡眠卡编辑铅笔、小睡行直接点 → `SleepEditSheet`——「整体平移」±1时/±10分 (修时区就靠它)、入睡/醒来各自微调、实时预览、删除 (二次确认)、已修正的可「恢复原始时间」。
  - **防覆盖是关键**: 所有外部睡眠写入都走 `HealthRepository.saveSleepSessions`, 现在先查 `protectedSessions()` (isEdited/isDeleted), 按 **id + 修正前原始时间窗** (`SessionDeduplicator.isSameSleepWindow`, 原私有判定已公开) 双重拦截外部同一条记录——佳明/HC 重新同步不会覆盖修正、不会带回删除; 修正过的记录也不进展示层合并池 (免得被外部字段盖掉)。分期图时刻来自原始 JSON, 主睡眠修正后按同样偏移整体平移 (SleepDayDetailViewModel)。totalMinutes 按窗口增减调整 (纯平移不变)。备份 dao.all() 原样带上标记, 旧备份反序列化兼容 (新字段有默认值)。
  - **今日页 AI 解读** (charlotte: 今日页分析也改成 AI 的, 结合睡眠讲今天 + 近三天/一周短期趋势; 趋势页只管长期): payload 加 `focus="today"` (近 14 天明细 + 90 天周期, 不带 longTerm; 缓存槽 `AI_RANGE_TODAY="today"` 复用按区间存储), **两份提示词同步加今日模板** (`AiAnalysisPrompt.buildToday` + server `TODAY_PROMPT_TEMPLATE`, 按 focus 路由): overall=2-3句今日结论 (以昨晚睡眠为核心+经期位置), recentPatterns=2-4条近3-7天走向, actions=今天就能做的, longTermFindings 必须 []。`AiInsightManager` 新增独立 `todayStatus`/`requestTodayAnalysis()` (与趋势页分析互不阻塞, 前台服务改引用计数)。
  - **UI**: 今日页状态卡下方新增 `AiTodayCard` (仅看「今天」时显示): 无结果/过期 → 一句说明+全宽琥珀按钮「AI 分析今天」; 今天的结果 → 时间行+总评+近几天走向+今天可以+留意, 顶部小字「重新分析」。**今天已有 AI 解读时 `StatusScoreCard(aiTakeover=true)` 收起本地模板叙述/洞察** (分数环/一句判断/维度条/7天轨迹常驻), AI 结果过期后本地叙述自动回来——同一件事不用两种口径讲两遍, 也回应她「洞察套模板」的老抱怨。
  - 验证: assembleDebug 通过 (schema 15.json 已生成); server 测试 9/9 (新增今日模板转义 + focus 路由两条); **Mac LaunchAgent 已 kickstart 重启, 新模板已生效** (ping 返回「未授权」= 服务正常在跑)。**待真机**: 改一晚旅行期睡眠, 看统计/评分/AI payload 跟着变、重新同步确认不被盖回; 今日页跑一次 AI 分析看文案贴不贴。
- ✅ **2026-07-06 删「可能有关的事」+ 天气随情绪时刻记 (charlotte 两点)**:
  - **趋势页删除「可能有关的事」卡** (charlotte: 不要了): `TrendsScreen` 删掉 `LocalAssociationCard` 渲染和空态文案, 趋势页现在只剩「AI 洞察」一张卡。`TrendsViewModel` 大幅瘦身——删掉 `state`/`localAssociations`/`History`/`Lifestyle` 及 5 个仓库依赖 (repo/habit/mood/cycle/weather), 构造参数只剩 `aiManager`+`prefs` (ViewModelFactory 同步)。**`domain/LocalAssociationAnalyzer.kt` 已删** (含 `LocalAssociationReport`, 全项目再无引用)。
  - **天气改成随情绪时刻记、可修改** (charlotte: 天气要能改, 记情绪时顺便加, 一天天气也多变): 天气从「每天一条只读自动」变成**挂在情绪时刻上**——`MoodEntry` 加 `weatherId: String?`, DB v15→**v16** (`MIGRATION_15_16` 加列)。`MoodMoment`/`MoodAggregator`/`MoodRepository.addMoment`/`updateMoment`/`MoodDao.updateContent`/`LifeViewModel.logMoodMoment`/`updateMoodMoment` 全部透传 weatherId。
  - **UI** (`MoodJournalScreen`): 情绪时刻弹层 (`MoodMomentDialog`) 加「这会儿的天气（可选）」FilterChip 行 (全 18 种 WeatherCatalog, 再点选中的取消); 新记一刻时天气**默认沿用当天已有天气** (最近一条带天气的时刻, 否则自动获取的日天气——`representativeWeatherId` 派生), 可再改。`MomentRow` 每条时刻显示自己的天气短标签; 卡片/过去某天/日编辑器顶部的日天气 chip 改用 `dayWeatherKind` 派生 (moments 优先, 回退自动)。
  - **自动天气不动**: Open-Meteo 自动获取 (WeatherSyncManager) 仍照跑写日 `WeatherEntry`, 现在只作**新时刻天气的默认种子** + 日天气兜底展示, 不再是唯一只读来源。`WeatherRepository.setWeather/clearWeather` 保留未删 (暂无调用方, 天气现由情绪时刻承载)。AI payload 仍不含天气 (无改动)。
  - 验证: assembleDebug 通过, schema 16.json 已生成 (weatherId 列在)。**待真机**: 记情绪时选/改天气、一天记多次不同天气看时间线与日 chip 派生; 迁移后旧情绪 weatherId 为空正常。
- ✅ **2026-07-14 情绪分析修「只看最后一条」+ 备注纳入 AI (charlotte 三点)**:
  - **根因**: `MoodDaily.dominantMoodId` 之前是「次数最多, 并列取**较晚**」——她一天记多条多为不同情绪 (各 1 次), 并列就退化成**当天最后一条**。这一个值同时喂给: 今日页「情绪整体偏低」洞察、全 app「主导」标签、AI payload。所以「只分析最后一条」三处症状同源。
  - **主导改「最能代表这天」** (`MoodAggregator.buildDaily`): 次数最多者胜; 并列取「价」最接近当天均值的 (thenByDescending abs(价−均值)), 仍并列取当天最早出现的。彻底不碰「最后一条」。修 #3 (主导), 同时改善所有展示主导的地方 (LifeScreen 今日情绪/7天条、MoodJournalScreen 周/今日/过去某天)。
  - **洞察改看整天平均** (`AnomalyEventEngine.moodStreak`, 改用 `MoodAggregator.daily` + 传 `zone`): 从「主导/最后一条是负向」改为「**当天所有时刻平均价 ≤ 2.0**」才算这天偏低——大半天正向、只末尾一条负向不再误报。删掉本地 `NEGATIVE_MOODS` 私有集。修 #1。
  - **备注 + 全部时刻进 AI** (`AiInsightModels.AiMoodDay` 扩为 mood+valence+moments[t/mood/note], 新增 `AiMoodMoment`; `AiInsightManager` 发每条时刻含 `note`): 之前只发每天一个主导情绪名、无备注 (原注释写「不含自由文本备注」)。现按 charlotte 要求把情绪备注纳入; **两份提示词同步改** (AiAnalysisPrompt.kt + vita_ai_server.py 的 FIELD_GUIDE moods 说明 + 写作要求加「结合 note 原话」)。修 #2。
  - 验证: assembleDebug 通过; server py_compile + 9 测试通过; 主导并列方向与平均阈值用 Python 复刻 7 场景确认 (她的「大半天好+末尾负」场景主导=平静、不再误报; 真·低落日仍触发)。**待真机**: 一天多记几条 (含备注) 看主导是否合理、洞察不再误报、跑一次 AI 分析看是否引用了备注。
- ⚠️ **仍需真机回归**: Garmin API 字段名在不同账号/区服可能有差异，已做多候选字段兜底；需要 charlotte 用真实账号确认压力/血氧/HRV 等是否返回。命令行已可用 Android Studio JBR 编译 (`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`)。

## 技术栈

- **语言**: Kotlin 2.0.21,JVM target 17
- **UI**: Jetpack Compose (BOM 2024.12.01) + Material 3
- **导航**: Navigation Compose
- **数据源**: Android Health Connect 1.1.0-rc03 (统一桥接佳明 Connect / 三星 Health / 小米运动健康 / Fitbit / …)
- **本地存储**: Room 2.6.1 (KSP)
- **图表**: Vico 2.0.0-beta.3 (Compose 原生)
- **序列化**: kotlinx.serialization JSON (用于备份)
- **后台任务**: WorkManager (已加依赖,还没用,后续可做定时同步)
- **构建**: AGP 8.7.3, Gradle 8.9, KSP 2.0.21-1.0.28
- **SDK**: minSdk=28 (Android 9), targetSdk/compileSdk=35

## 目录结构

```
Vita/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml          # Health Connect 权限 + queries + 权限理由页
│       ├── res/                         # 主题/字符串/colors/adaptive icon
│       └── java/com/vita/healthtracker/
│           ├── VitaApplication.kt       # 持 AppContainer
│           ├── MainActivity.kt          # Compose 入口 + 底部导航
│           ├── data/
│           │   ├── AppContainer.kt      # 手摇 DI (避免 Hilt 复杂度)
│           │   ├── local/
│           │   │   ├── VitaDatabase.kt
│           │   │   ├── entity/ (6 张表)
│           │   │   └── dao/
│           │   ├── healthconnect/
│           │   │   └── HealthConnectManager.kt  # 包了一层,集中处理权限和读取
│           │   ├── repository/          # HealthRepository, CycleRepository
│           │   └── backup/              # BackupEnvelope, BackupManager
│           └── ui/
│               ├── ViewModelFactory.kt  # 手摇 VM 工厂
│               ├── theme/
│               ├── navigation/
│               ├── components/          # MetricCard, RangeSelector
│               └── screens/
│                   ├── today/           # 今日总览 + 同步按钮
│                   ├── stats/           # 日/周/月/年图表
│                   ├── cycle/           # 月经周期记录 + 推算下次
│                   └── settings/        # Health Connect 授权 + 导入导出
├── gradle/
│   ├── libs.versions.toml               # 版本目录
│   └── wrapper/ (jar 是从 Fortuna 复制过来的)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── local.properties                     # sdk.dir 指向 ~/Library/Android/sdk
└── .gitignore
```

## 数据流

```
佳明手表 / 三星手表 / 小米手环
        ↓ (各家自己的 app: Garmin Connect / Samsung Health / Mi Fitness)
   Android Health Connect (系统级数据中枢, Android 14+ 内置)
        ↓ HealthConnectManager (我们封装的 SDK 客户端)
   HealthRepository.syncFromHealthConnect()  ← 增量,基于 SyncMarker
        ↓ Room (vita.db)
   ViewModel.state (Flow + stateIn)
        ↓ Compose UI
```

## 常用命令

```bash
# 在 Android Studio 里直接 Open this directory, Sync, Run

# 命令行 (需先设 ANDROID_HOME)
./gradlew assembleDebug                # 编译 debug APK
./gradlew installDebug                 # 安装到连接的设备
./gradlew :app:lintDebug               # Lint
./gradlew :app:testDebugUnitTest       # 单测 (暂无)
```

> 本机没装独立 Gradle / JDK,Android Studio 用自带 JBR 即可。
> SDK 在 `/Users/restartday/Library/Android/sdk`,已在 local.properties 配置。

## Health Connect 关键设计

1. **权限模型**: 在 AndroidManifest 里 `<uses-permission android:name="android.permission.health.READ_xxx" />`,运行时通过 `PermissionController.createRequestPermissionResultContract()` 拿 launcher,在 Settings 页用户点击\"授权数据读取\"触发系统弹窗。
2. **可用性检测**: `HealthConnectClient.getSdkStatus()` 区分四种状态。Android 13- 用户需手动从 Play 装 Health Connect (Settings 页提供跳转)。
3. **权限理由页**: 同时声明了 `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (Android 13-) 和 `android.intent.action.VIEW_PERMISSION_USAGE` + `HEALTH_PERMISSIONS` category (Android 14+),都路由到 MainActivity。
4. **增量同步**: `SyncMarker` 表记录每个数据类型上次同步终点,默认首次从 90 天前开始拉。
5. **Garmin 原始归档**: `garmin_raw_record` 以 `date + domain + categoryKey` 去重保存原始 JSON,备份导入导出会带上。UI 用 `GarminRawDisplay` 做中文字段/单位适配；结构化统计仍用 `DailyHealthSnapshot` / `SleepSession` / `ExerciseSession` 等表，避免 raw 数据直接污染月/年图。
6. **数据源透传**: 每条记录都保留 `source` = 原始 app 包名,后续可以做\"哪条数据来自哪只手表\"的展示。

## 月经周期

- 本地表 `cycle_entry`,主键是日期
- `isPeriodStart=true` 的记录用来推算平均周期长度和下一次预计日
- 用户手动记录的 (`source=manual`) 会**同步写回 Health Connect** (写权限只开了月经流量),保证其它 app 也能看到
- 从 HC 读回的 (`source=health_connect`) 不重复写,避免循环

## 数据备份

- 走 SAF (Storage Access Framework),用户自己选 JSON 保存位置 / 来源
- `BackupEnvelope` 是版本化的,目前 v1。未来加字段记得在 `BackupManager` 反序列化时保留 `ignoreUnknownKeys = true` 兼容
- 系统自动备份 (`backup_rules.xml`) 已**关闭数据库**,完全靠用户自己导出

## 工作约定

- Kotlin 严格模式;新代码尽量函数式 + 不可变 data class
- UI 文案过 `strings.xml`,不要硬编码
- 所有读 Health Connect 的调用都套 `runCatching`,设备无 HC 时不能崩
- 数据库表新增字段 → 必须升 `VitaDatabase` 的 version,加 Migration (现在用 `fallbackToDestructiveMigration` 是开发期临时手段)
- 永远深色主题,charlotte 偏好 `#06080f`
- 不要引入 Hilt,手摇 `AppContainer` + `VitaViewModelFactory` 足够这个规模

## 关联项目

- **Fortuna**: `/Users/restartday/Documents/Fortuna` — 同人的资产管理 app,共用深色风格和 charlotte 单人开发节奏
- 两个 app 是平行关系,数据不互通

## 下一步建议 (待 charlotte 决定优先级)

1. **真机验证**: Android Studio Open → Sync → Run。由于命令行缺 Java 无法编译，需要你在 Android Studio 里跑。先在 Settings 页授权,再回 Today 同步一次,看是否能读到手表数据、以及动画/渐变是否符合预期
2. **图标和启动画面**: 当前 ic_launcher 是占位 V 字,可让 charlotte 给个原图,用 `@drawable` + adaptive-icon 替换;启动动画用 SplashScreen API
3. **图表交互细节**: 目前 Stats 页套上了颜色，可进一步加 tooltip、滑动手势、按日期标 x 轴
4. **运动会话详情页**: 点 ExerciseSession 卡片进二级页,显示距离/卡路里/平均心率/GPS 轨迹 (HC 也带 ExerciseRouteResult)
5. **数据回填到 Fortuna?** 如果未来想把"健康消费"(健身房月卡 / 运动装备) 自动关联,可考虑跨 app share Intent
