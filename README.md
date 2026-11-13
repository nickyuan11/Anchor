# Anchor（定海神针）

免 root 的 Android 开屏广告跳过工具：通过无障碍服务自动点击开屏广告的「跳过」按钮，抢在「摇一摇」被误触发之前关掉广告。

- 完整设计：[docs/design.md](docs/design.md)
- 安装和使用说明（发给使用者）：[docs/install-guide.md](docs/install-guide.md)
- 内置规则及其依据：[docs/builtin-rules.md](docs/builtin-rules.md)

## 功能

- 按规则跳过开屏广告（规则格式见 design.md 第 6 节），内置腾讯视频、爱奇艺的规则
- 没有专属规则的 App 用通用关键词兜底，可关闭
- 应用白名单、跳过记录
- 页面快照：一键导出当前页面的控件结构、截图和服务诊断日志，用来编写新规则和排查问题
- 开启引导、按品牌的保活引导、服务运行状态

## 环境

- Android Studio（当前稳定版），自带 JDK
- Android SDK Platform 37、Build-Tools 36
- 依赖从 Google Maven 和 Maven Central 下载，需要能访问这两个仓库

## 常用命令

在项目根目录执行（Windows 用 `gradlew.bat`，macOS / Linux 用 `./gradlew`）：

| 命令 | 作用 |
| --- | --- |
| `gradlew.bat assembleDebug` | 打 debug 包，输出在 `app/build/outputs/apk/debug/` |
| `gradlew.bat testDebugUnitTest` | 运行单元测试 |
| `gradlew.bat lintDebug` | 运行 Lint 检查 |
| `gradlew.bat assembleRelease` | 打正式包，输出在 `app/build/outputs/apk/release/` |

debug 包比正式包多出：桌面上的「广告测试」图标（模拟开屏广告）、主页的调试面板、一条给模拟广告用的规则。

## 打正式包

正式包需要用自己的钥匙（keystore）签名。系统靠签名确认新旧版本出自同一个开发者，**签名一致才能覆盖安装**。

### 第一次：生成钥匙

1. Android Studio 菜单 **Build → Generate Signed App Bundle or APK…**，选 **APK**，点 Next。
2. 点 **Create new…**：
   - **Key store path**：选一个**项目目录之外**的位置，例如 `D:\develop\keys\anchor-release.jks`
   - **Password**：钥匙库密码
   - **Alias**：填 `anchor`
   - **Key Password**：钥匙密码（可以和上面相同）
   - **Validity**：保持默认的 25 年
   - **Certificate** 里至少填一项，例如 First and Last Name
3. 点 OK 创建。之后可以直接关掉这个向导，后面用命令打包。

**务必备份这个 `.jks` 文件并记住密码。** 丢了就再也打不出相同签名的包，已安装的用户只能卸载重装。不要把它提交到仓库或发给别人。

### 配置

把 `keystore.properties.example` 复制一份，改名为 `keystore.properties`（放在项目根目录），填入钥匙的路径、密码和别名。路径用正斜杠 `/`。这个文件已被 `.gitignore` 排除。

### 打包

```
gradlew.bat assembleRelease
```

得到 `app/build/outputs/apk/release/anchor-<版本号>-release.apk`。如果文件名是 `…-release-unsigned.apk`，说明没有读到 `keystore.properties`。

### 发新版本

修改 `app/build.gradle.kts` 里的 `appVersionName`，并把 `versionCode` 加 1，再打包。

## 添加内置规则

1. 让使用者在广告页面抓一份快照（见安装说明第五节），解压后看 `snapshot.json` 里的包名、页面名和控件。
2. 在 `app/src/main/assets/rules/builtin.json` 里添加规则，并在 `docs/builtin-rules.md` 里记下依据。
3. 运行单元测试。`RuleParserTest` 会检查规则文件的格式和每条规则是否合法。

## 代码结构

```
app/src/main/java/com/nickyuan/anchor/
├── service/    无障碍服务入口、点击与手势、保活窗口
├── engine/     规则引擎（纯 Kotlin，可单元测试）：规则匹配、通用兜底、防抖、前台 App 跟踪
├── rule/       规则模型、解析、校验与加载
├── snapshot/   页面快照抓取与导出
├── guide/      按品牌的保活步骤
├── data/       Room 数据库、DataStore 设置
├── ui/         Compose 页面：home / guide / logs / settings / whitelist / snapshots
└── util/       系统设置跳转、诊断日志等
app/src/debug/  仅 debug 包：模拟广告测试页、调试面板、测试规则
```
