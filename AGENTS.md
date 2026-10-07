# AGENTS.md — Vita 健康追踪 App

> 此文件是 Codex 的工作记忆。每次新会话进入这个目录时会自动加载。

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
  - 用户手动同步会启动 `GarminSyncForegroundService` 前台服务和通知，并在同步期间持有 `PARTIAL_WAKE_LOCK`，降低切后台/锁屏后同步协程被系统休眠打断的概率；WorkManager 周期同步现在网络可用时即跑，并且 Health Connect 不可用时也会继续尝试 Garmin。
  - 统计月/年图会用运动记录补齐步数/距离/消耗/时长/心率兜底，避免「下面有步行活动，上面步数图没有」的矛盾；运动明细行日期显示年份，跨年不再混淆。
  - `Garmin 全量数据` 页去掉接口路径和未适配英文键，只展示已识别、有意义的健康项目，并给每类数据加中文解释；未展示字段仍保存在本地 raw 表。
  - 今日页睡眠改为按醒来日期归属，避免前一晚/前一天睡眠串到「今天」；顶部日期去掉星期，避免小屏截断。
  - 新增 `ExerciseClassifier`: 自动步行和强度健身分开归类，心率/卡路里/坡度较高的 treadmill/步行归入「强度健身」，低强度半夜短步行从统计展示中剔除。
  - Garmin 动态指标详情优先画曲线（压力/身体电量/心率/血氧/呼吸），隐藏 start/end/time/date/采样数量等技术字段；健身年龄这类仅有裸数值且语义不足的数据不展示。
  - Garmin raw 曲线横轴改为 0-24 小时；raw 活动页只保留每个活动的一张卡片，隐藏运动分段/轨迹/全天事件等暂不可读内容，卡片副标题只显示数据来源。
  - `ExerciseSession` 升级到本地可编辑：新增自定义标题、备注、分类、删除隐藏标记；同步 upsert 会保留这些本地编辑，统计页运动明细显示具体标题，运动详情可编辑标题/分类/备注或删除记录。数据库当前 v8，已加 6→7、7→8 迁移。
  - 删除「零散活动」分类，短步行仍归入「步行」；Garmin 活动若返回 description/comment/note 等备注字段，会同步保存到运动备注。Garmin raw 图表轴标签增加异常值保护，避免明细页因坐标值异常闪退。
  - Garmin 全量数据页的动态图表改为 Compose Canvas 自绘 24 小时曲线，不再在长列表内创建 Vico 图表，规避继续下拉时的图表实例闪退；曲线会过滤 Garmin 数组里的小数字伪时间点，低于 6 个有效采样点不强行画图，跨 1.5 小时以上的采样断开连线，避免一条斜线/竖线糊在右边。
  - 今日页「运动记录」卡片现在可点：只有 1 条运动时直接进入运动详情；多条运动时弹出当天运动列表，选择后进入对应详情页。
- ✅ **2026-05-24 统计/习惯记录补丁**:
  - 修复统计页「全部历史」三张累计卡在窄屏下第三张 `kcal` 被挤成竖排的问题：累计卡改为两列 + 单列自适应排布，数值/单位强制单行保护。
  - 统计页日视图的「过去7天」改成 Garmin 风格列表：按运动类型汇总次数与距离/时长，再显示睡眠、平均步数、静息心率、热量和压力。
  - 生活页新增「习惯记录」板块：本地自定义习惯、当天待打卡 ✓/×、删除归档；点进详情页可切换 1天/7天/4周/12周周期，日历随周期变化并支持补打卡。新增 Room 表 `habit_definition` / `habit_check_in`，数据库当前 v9，已加 8→9 迁移。
  - 生活页最上方新增独立「徽章」板块，显示已获得徽章数量、当前/最长连续打卡天数；点进徽章墙可查看 8 枚徽章、达成条件和进度。徽章图按 Open Design 提供的「奇迹体能徽章组」用 Compose Canvas 复刻；当打卡/补打卡让连续天数跨过 1/3/7/14/30/90/180/365 天条件时，会弹出获得徽章庆祝弹窗。
- ⚠️ **仍需真机回归**: Garmin API 字段名在不同账号/区服可能有差异，已做多候选字段兜底；需要 charlotte 用真实账号确认压力/血氧/HRV 等是否返回。命令行已可用 Android Studio JBR 编译。

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

## Imported Claude Cowork project instructions

## 当前 GitHub 发布约定（2026-10-07）

以下为当前发布身份与数据保护约定，涉及发布和数据库状态时以本节及当前代码为准：

- 公开仓库为 `https://github.com/charlotteamian/Vita`。源码和测试可以公开；个人健康数据、备份、账号凭据、AI Key 与签名私钥永不提交。
- 正式更新使用 `personal` 构建类型：包名固定 `com.vita.healthtracker.debug`、`isDebuggable=false`，沿用原 Mac 签名，证书 SHA-256 固定 `71b7801a44bd4ee9227e4c0e97e40c5b4b9432db68622a18c0aa58e65c7bb048`。不得改变包名、证书或建议卸载旧应用。
- 版本集中在根目录 `version.properties`；发布新版必须同时增加 `versionCode` 与 `versionName`，且 code 高于最新 Release。公开分发身份集中在 `distribution.properties`，不可为通过校验而更换指纹。
- `.github/workflows/android-ci.yml` 的 `Android CI` 执行 debug 单测/Lint/编译，不发布普通 debug APK。`.github/workflows/android-release.yml` 的 `Publish Android update` 支持手动运行或推送匹配版本的 `v版本` 标签；执行 personal 单测/Lint/编译和 `scripts/verify_apk.py` 检查，生成 APK、`release-manifest.json`、`SHA256SUMS`，拒绝重复标签及版本倒退。
- 签名只从四个 GitHub Actions Secrets 读取：`VITA_SIGNING_KEYSTORE_B64`、`VITA_SIGNING_STORE_PASSWORD`、`VITA_SIGNING_KEY_ALIAS`、`VITA_SIGNING_KEY_PASSWORD`。本地默认使用既有 `~/.android/debug.keystore`，换电脑必须恢复原密钥；可用 `VITA_SIGNING_KEYSTORE_PATH` 指定文件。签名备份放在仓库外。
- 首版从 Releases 手动覆盖安装，后续通过设置页 GitHub 检查更新，下载校验后交由系统安装。更新前可导出 JSON 健康备份，但备份不包含凭据与设置。
- Room 当前版本为 16，无破坏性迁移兜底。每次数据库变更必须增加版本并补迁移；历史 v4 schema 缺失，真机未连接时不得宣称全历史迁移、覆盖安装和数据保留均已验证。
- 常规数据保存在手机本地；用户请求 AI 分析时会发送摘要与情绪备注到所选模型服务。文档不得把可选 AI 分析描述为完全不上云。本次 GitHub 发布工作保持 Garmin SSO/cookie/OAuth/登录流程冻结。
- 网页修改、发布、签名恢复与验证边界见 `docs/releases.md`。
