# Vita

Vita 是一个 Android 个人健康记录应用。健康、运动、睡眠、周期、情绪与习惯数据保存在手机本地，支持 Garmin、Health Connect 和 Apple 健康导出文件。

代码公开在 [charlotteamian/Vita](https://github.com/charlotteamian/Vita)，安装包在 [GitHub Releases](https://github.com/charlotteamian/Vita/releases/latest)。仓库包含应用源码、数据库迁移、测试与可选的 Mac AI 服务，不包含个人健康数据、账号凭据或签名私钥。

## 安装与更新

第一次从 GitHub 更新时，在 Releases 下载 `Vita-版本.apk`，直接覆盖安装已有 Vita。**不要先卸载，也不要清除应用数据。** 建议先在旧应用设置页导出一份 JSON 备份；该备份包含健康记录，不包含登录凭据和应用设置。

安装这个版本后，可在 **设置 → 检查更新** 获取后续 GitHub 版本，由应用下载、校验安装包，再交给 Android 系统确认安装。首次安装可能需要允许当前安装来源安装应用。

发布包使用 `personal` 构建类型，保持已有安装的身份：

| 项目 | 固定值 |
|---|---|
| 应用包名 | `com.vita.healthtracker.debug` |
| 可调试 | `false` |
| 签名证书 SHA-256 | `71b7801a44bd4ee9227e4c0e97e40c5b4b9432db68622a18c0aa58e65c7bb048` |

`.debug` 是既有应用的包名组成部分，发布包仍保持这个包名和原证书，才能原地更新并保留数据。普通 `debug` 构建仅供开发检查，不用于发布或覆盖已有手机应用。

## 直接在 GitHub 修改和发布

1. 在 GitHub 网页编辑代码并提交到主分支。
2. 编辑根目录 `version.properties`，同时递增 `versionCode` 和 `versionName`。当前为 `2` / `0.2.0`。
3. 等待 **Android CI** 通过，再进入 **Actions → Publish Android update → Run workflow**。也可推送与版本一致的 `v版本` 标签。
4. 发布流程检查签名和版本后，生成 APK、`release-manifest.json`、`SHA256SUMS`，上传到 Releases。手机即可检查到新版本。

完整操作、签名恢复和验证边界见 [发布说明](docs/releases.md)。

## 本地开发

使用 Android Studio 打开项目，或使用 JDK 17、Android SDK 35 和项目自带 Gradle wrapper：

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

构建可覆盖现有安装的包，需要原签名私钥：

```bash
./gradlew :app:testPersonalUnitTest :app:lintPersonal :app:assemblePersonal
```

本机默认从 `~/.android/debug.keystore` 读取既有密钥；换电脑必须恢复原密钥，也可用 `VITA_SIGNING_KEYSTORE_PATH` 指定位置。构建会拒绝证书不一致的密钥。

## 数据与 AI

常规健康记录与统计在手机本地处理。启用并请求 AI 分析时，会把健康摘要和情绪备注发送到设置中选定的 AI 服务；Mac 服务也会把请求交给其 CLI 后端对应的模型服务。服务端数据处理、费用与额度取决于所选服务和账号。详情见 [Mac AI 服务说明](ai-server/README.md)。

当前 Room 数据库版本为 16，升级必须提供迁移，禁止通过清库兜底。构建与自动检查不能替代真机覆盖安装及数据保留验证。
