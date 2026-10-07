# GitHub 更新与签名维护

## 已有手机应用的发布身份

Vita 公开仓库为 [charlotteamian/Vita](https://github.com/charlotteamian/Vita)。更新发布使用 `personal` 构建类型，固定包名 `com.vita.healthtracker.debug`、`isDebuggable=false`，沿用原 Mac 于 2026-05-09 创建的签名证书。

证书 SHA-256：

```text
71b7801a44bd4ee9227e4c0e97e40c5b4b9432db68622a18c0aa58e65c7bb048
```

原签名、同一包名和更高的 `versionCode` 共同保证更新使用既有应用身份。**不要卸载旧应用或清除数据。** 如果安装器提示签名不兼容，应保留旧应用，检查 APK 与签名，不要通过卸载解决。

根目录 `distribution.properties` 保存公开的包名、仓库和证书指纹；这些值必须保持不变。私钥永远不提交到 Git，也不随 APK、Release 或日志公开。

## 在 GitHub 网页更新代码

1. 打开仓库，在主分支编辑需要修改的源码并提交。较大修改可先在新分支操作，再提交 Pull Request。
2. 编辑根目录 `version.properties`，同时增加两个值。例如，当前 `versionCode=2` / `versionName=0.2.0`，下一版可设为 `versionCode=3` / `versionName=0.2.1`。
3. 在 Actions 确认 **Android CI** 成功。工作流文件为 `.github/workflows/android-ci.yml`，执行 debug 单测、Lint 和编译，不发布普通 debug APK。
4. 进入 **Actions → Publish Android update → Run workflow**，选择包含本次修改的主分支并运行。工作流文件为 `.github/workflows/android-release.yml`。
5. 等发布成功后，打开 [Releases](https://github.com/charlotteamian/Vita/releases)，检查版本及附件。

也可以推送版本标签触发发布。标签必须与 `version.properties` 中的 `versionName` 一致，例如 `v0.2.1`。不要一边手动运行、一边为同一版本重复触发发布。

发布流程拒绝已有标签和不高于最新发布版的 `versionCode`。修改已有 Release 的附件或覆盖旧标签会破坏更新校验；修正已发布问题时应增加版本并发布新版本。

## 发布流程产物与检查

`Publish Android update` 使用原签名执行 personal 单测、Lint 和 APK 编译，再调用 `scripts/verify_apk.py` 校验包名、版本、可调试标记和固定证书指纹。

每个 Release 应包含：

| 附件 | 用途 |
|---|---|
| `Vita-版本.apk` | 手机覆盖安装包 |
| `release-manifest.json` | 应用检查更新使用的版本和安装包校验信息 |
| `SHA256SUMS` | 发布附件的 SHA-256 校验值 |

设置页从指定 GitHub 仓库检查新版，下载并校验安装包，再启动 Android 系统安装器。安装仍需要用户在系统界面确认。

## 首次切换与后续手机更新

旧版本没有 GitHub 更新入口时，先从 Releases 手动下载并覆盖安装第一版。安装前建议在旧应用设置页导出 JSON 备份，保存到仓库外的个人位置。备份涵盖健康记录，不包含 Garmin 登录凭据、AI API Key 或应用偏好设置。

之后在 **设置 → 检查更新** 更新即可。若 Android 提示需要允许安装未知来源应用，按系统提示为当前安装来源开启权限后继续。

包名或签名不一致的 APK 无法用于已有安装的原地更新。版本号降低也会被正常安装流程拒绝。请保留旧应用和数据，检查发布产物。

## GitHub 签名 Secrets

在仓库 **Settings → Secrets and variables → Actions → Repository secrets** 配置：

| Secret 名称 | 内容 |
|---|---|
| `VITA_SIGNING_KEYSTORE_B64` | 原 keystore 的 Base64 内容 |
| `VITA_SIGNING_STORE_PASSWORD` | 原 keystore 密码 |
| `VITA_SIGNING_KEY_ALIAS` | 原私钥 alias |
| `VITA_SIGNING_KEY_PASSWORD` | 原私钥密码 |

只恢复既有密钥。**不要生成新 keystore 替换原密钥。** 发布时会与固定证书指纹比较，配置错误应修复 Secrets 后重新运行，不修改指纹来绕过校验。

本地保留一份仓库外的签名备份，例如项目旁的 `Vita-signing-backup/`，并另存一份安全的离线副本。该目录包含私钥，不能作为仓库源码、云端公开文件或 Release 附件上传。

## 换电脑或本地构建

换电脑后，从签名备份恢复原 keystore。恢复到 `~/.android/debug.keystore`，或指定独立文件：

```bash
export VITA_SIGNING_KEYSTORE_PATH="/安全位置/原签名.keystore"
```

其他对应环境变量为 `VITA_SIGNING_STORE_PASSWORD`、`VITA_SIGNING_KEY_ALIAS`、`VITA_SIGNING_KEY_PASSWORD`。在本机私密环境中配置，不写进仓库文件。GitHub 使用的 `VITA_SIGNING_KEYSTORE_B64` 由发布流程解码；本地 Gradle 使用文件路径。

在 macOS 上可使用 Android Studio 自带 JBR：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:testPersonalUnitTest :app:lintPersonal :app:assemblePersonal
python3 scripts/verify_apk.py --apk app/build/outputs/apk/personal/app-personal.apk --output-dir build/distribution
```

原始 APK 在 `app/build/outputs/apk/personal/`，校验后的发布附件在 `build/distribution/`。普通 `assembleDebug` 会使用当前电脑的开发签名；新电脑自动生成的开发密钥不能用于覆盖原安装。

## 数据迁移与验证边界

当前 Room schema 版本为 16，没有破坏性迁移兜底。数据库变化必须增加版本、补充迁移并更新 schema，不允许通过删除数据库让升级成功。

仓库缺少历史 v4 schema，因此不能把所有历史版本迁移描述为已通过自动验证。CI、签名检查和 APK 静态检查能够验证构建产物；真机覆盖安装、健康记录保留、授权与同步仍需在有数据的实际设备上确认。本次建立发布流程时真机未连接，不能据此宣称这些检查已完成。
