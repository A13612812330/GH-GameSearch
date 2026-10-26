# GH-GameSearch

GH-GameSearch 是一个面向内部使用的 Android 游戏信息查询工具。它通过 WebView 完成后台登录，再使用当前登录账号已有的权限读取游戏信息、详情和镜像数据，并在手机端以更适合查看和复制的界面展示。

本项目不是新的后台系统，也不会绕过后台权限。账号能看到什么，取决于后台账号本身的权限。

## 已实现功能

- 后台账号密码登录，支持检测已有登录状态。
- 登录后进入后台“游戏管理 -> 游戏信息”页面。
- 按游戏名称或游戏 ID 搜索。
- 游戏类型支持多选筛选，默认不筛选。
- 候选结果展示游戏 ICON、名称、副标题、游戏 ID、游戏类型、标签、7 天下载量、合作平台、合作方式。
- 搜索结果按 7 天下载量从高到低排列。
- 展示游戏屏蔽、游戏镜像状态。
- 展示镜像开关、生效规则、省份、城市、备注、APK 包和对应链接。
- 游戏详情使用固定窗口展示，内容支持长按复制。
- 适配手机窗口、刘海区域和竖屏查看。
- 登录失败、搜索失败、接口异常会在前端给出提示。

## 成品 APK

已验证的 APK 位于：

`dist/GH-GameSearch.apk`

SHA-256：

`D3C0BAD5728A00A21FBD7D8C2F038D5BDB6C54D804ABBC5180ECD3C0E8AFD6E7`

安装方式：将 APK 传到 Android 手机后打开安装。首次安装时需要允许来自当前文件管理器或浏览器的安装权限。

## 构建环境

- Windows 10/11
- Android Studio
- JDK 17
- Android SDK Platform 35
- Gradle Wrapper
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21

建议将项目放在纯英文路径，例如：

`E:\新建文件夹\Codex-GPT\GH-GameSearch`

Android Gradle Plugin 在 Windows 下对非 ASCII 项目路径兼容性较差，不建议直接放在包含中文的更深层项目目录中。

## 构建

在项目根目录执行：

```powershell
.\gradlew.bat --gradle-user-home .gradle-user-home assembleDebug
```

生成文件：

`app\build\outputs\apk\debug\app-debug.apk`

也可以用 Android Studio 打开项目，等待 Gradle Sync 完成后执行 `app > Tasks > build > assembleDebug`。

## 使用流程

1. 打开 App。
2. 输入后台账号和密码并点击登录。
3. App 在 WebView 中完成后台登录和权限检测。
4. 登录成功后自动进入“游戏管理 -> 游戏信息”。
5. 输入游戏名称或游戏 ID，选择可选的游戏类型后搜索。
6. 点击候选游戏查看游戏详情和镜像信息。

如果手机中已经保留了有效的 WebView 登录 Cookie，App 会优先检测已有登录状态，不会重复要求输入账号密码。后台登录失效后，需要重新登录。

## 后台依赖

当前版本依赖项目已有后台：

- 后台登录页：`https://and-admin.ghzs.com/#/welcome/`
- 游戏信息页：`https://and-admin.ghzs.com/#/games-manage/games-info?href=games_info_menu`
- 数据请求使用后台当前登录会话、Cookie 和 Token。

项目没有独立数据库，也没有公开 API 服务。接口路径和字段解析逻辑位于 `app/src/main/java/com/ghzs/gameinfo/MainActivity.kt`。

## 安全说明

- 不要把账号、密码、Cookie、Token、截图或调试日志提交到 GitHub。
- 不要提交 `local.properties`、签名文件或个人 Android Studio 配置。
- 本仓库建议保持为 Private，因为它连接内部后台。
- App 只使用当前后台账号已有权限，不提供越权能力。
- APK 下载链接、镜像链接和后台数据可能包含内部信息，请勿公开转发。
- iOS 书签、中转服务和浏览器实验文件未放入本仓库，避免把本机地址或登录态传输逻辑发布出去。

## 项目结构

```text
GH-GameSearch/
├─ app/
│  └─ src/main/
│     ├─ java/com/ghzs/gameinfo/MainActivity.kt
│     ├─ res/
│     └─ AndroidManifest.xml
├─ dist/
│  └─ GH-GameSearch.apk
├─ gradle/wrapper/
├─ .github/workflows/android.yml
├─ build.gradle
├─ gradle.properties
├─ gradlew
├─ gradlew.bat
├─ settings.gradle
├─ CHANGELOG.md
└─ SECURITY.md
```

## 常见问题

### Gradle 提示路径含非 ASCII 字符

把项目移动到纯英文路径后重新打开。也可以确认 `gradle.properties` 中存在：

```properties
android.overridePathCheck=true
```

### Gradle 缓存锁定或下载失败

关闭正在运行的 Android Studio 构建任务，然后使用项目内独立缓存重试：

```powershell
.\gradlew.bat --stop
.\gradlew.bat --gradle-user-home .gradle-user-home assembleDebug
```

### 登录后没有进入游戏信息页

先在后台网页中确认账号能手动进入“游戏管理 -> 游戏信息”。如果后台账号没有菜单权限，App 也无法显示该页面。登录过期时清除 App 数据后重新登录。

### 搜索没有结果或提示接口失败

确认手机能访问后台域名，并确认账号仍有游戏信息查询权限。后台页面结构或字段改动时，需要同步调整 `MainActivity.kt` 的元素解析和请求参数。

## 版本说明

当前仓库中的 `dist/GH-GameSearch.apk` 是基于已验证源码于 2026-10-26 重新构建的稳定成品。后续新增功能建议修改版本号、更新 `CHANGELOG.md`，并重新生成 `dist/GH-GameSearch.apk`。

