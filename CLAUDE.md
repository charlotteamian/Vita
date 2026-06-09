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
- ⚠️ **仍需真机回归**: Garmin API 字段名在不同账号/区服可能有差异，已做多候选字段兜底；需要 charlotte 用真实账号确认压力/血氧/HRV 等是否返回。命令行已可用 Android Studio JBR 编译。**本批改动 (InsightGenerator 重写为数据驱动; StatsViewModel.aggregateSleep + buildRangeSummary 按晚取最长去重; GarminDataDetailScreen 移除 raw dump 渲染; StatsViewModel/StatsScreen 周期窗口+翻页改造) 需在 Android Studio 过一次 assembleDebug (本机沙箱无 Android SDK)。**

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
