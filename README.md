# YesPlayMusic Car (Native Android)

纯原生 Android 车机音乐播放器（Kotlin + Media3），当前已接入 `api-enhanced` 的搜索与播放解析。

## 运行环境
- Android Studio（建议最新稳定版）
- Android SDK 34
- JDK 17

## 如何运行
1. 使用 Android Studio 打开项目根目录。
2. 等待 Gradle Sync 完成。
3. 选择设备并点击 Run。

## API Base URL
默认使用：
`https://api-enhanced-sable.vercel.app`

如需更换，修改：
`app/src/main/java/com/yesplaymusic/car/data/ApiEnhancedProvider.kt`

## 现有功能
- 搜索歌曲
- 解析播放地址并播放
- 前台服务 + 通知栏媒体控制
- 基础播放控制（播放/暂停/上一首/下一首/进度）
- 首页推荐歌单 / 新专辑 / For You（api-enhanced）

## 字体
已使用 Barlow 字体（`app/src/main/res/font/`）。
