# thyids_music

> 一个与 AI 协作完成的免费听歌软件 🎵

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://www.android.com/)

thyids_music 是一个**免费、无广告**的安卓音乐播放器。它聚合了多个音乐平台的搜索与播放能力，界面简洁、打开即听，没有会员墙，没有 30 秒广告。

本项目的代码与 UI 由作者 **thyids** 与 AI 协作完成。

---

## ✨ 功能特性

- **聚合搜索** —— 一个入口搜索多个音乐平台，自动并发查询
- **智能兜底** —— 当前音源解析失败时，自动降级到其它音源
- **玻璃拟态 UI** —— 自研 `LiquidGlass` 视觉效果，半透明磨砂 + 光晕
- **远程音箱** —— 同一 Wi-Fi 下，把另一台手机变成你的音箱
- **后台播放** —— 前台服务 + 通知栏控制，锁屏也能切歌
- **本地歌单** —— 收藏、管理你喜欢的歌曲
- **播放缓存** —— 缓存已解析的地址，减少重复请求

---

## 🎧 支持的音源

| 平台 | 标识 | 状态 |
| --- | --- | --- |
| 咪咕音乐 | Migu | ✅ |
| 网易云音乐 | Netease | ✅ |
| 酷我音乐 | Kuwo | ✅ |
| QQ 音乐 | Qq | ✅ |
| B 站 | Bilibili | ✅ |
| 太合音乐 | Taihe | ✅ |

> 所有音源都实现自统一接口 `BackupMusicSource`，新增平台只需实现 `search()` 与 `getPlayUrl()` 两个方法。

---

## 🛠 技术栈

- **语言**：Kotlin 2.2
- **UI**：Jetpack Compose（Material 3）
- **播放内核**：AndroidX Media3 / ExoPlayer
- **媒体会话**：AndroidX Media3 Session
- **网络**：OkHttp 4 + Jsoup
- **构建**：Gradle Kotlin DSL（AGP 9.2.1）

---

## 📥 下载安装

仓库内已附带编译好的 APK：

- `app/release/app-release.apk`

你也可以直接安装本目录下（NAS `test/`）的 `app-release.apk`。

> 安装未知来源应用时，请在系统设置中允许对应文件管理器 / 浏览器的"安装未知应用"权限。

---

## 🚀 从源码构建

```bash
# 克隆仓库
git clone https://github.com/thyids/thyids_music.git
cd thyids_music

# 构建 Debug 版本
./gradlew assembleDebug

# 构建 Release 版本
./gradlew assembleRelease
```

构建产物位于：

```
app/build/outputs/apk/
```

### 环境要求

- Android Studio（建议最新稳定版）
- JDK 17 以上
- Android SDK：与 `app/build.gradle.kts` 中 `compileSdk` 保持一致

---

## 📖 项目结构

```
app/src/main/java/com/thyids/free_music/
├── MainActivity.kt              # 主界面（Compose）
├── data/
│   ├── Song.kt                  # 歌曲数据模型
│   ├── MusicSources.kt          # 音源实现（咪咕 / 网易云等）
│   ├── MoreMusicSources.kt      # 更多音源实现
│   ├── MusicRepository.kt       # 聚合搜索与播放地址解析
│   ├── CacheManager.kt          # 播放地址缓存
│   ├── PlaylistRepository.kt    # 本地歌单
│   ├── Playlist.kt
│   └── RemoteSpeakerManager.kt  # 远程音箱（UDP 发现 + TCP 传输）
├── service/
│   ├── PlaybackService.kt       # 播放前台服务
│   └── RemoteSpeakerService.kt  # 远程音箱服务
├── presentation/
│   └── MusicViewModel.kt        # 状态管理
└── ui/theme/
    ├── LiquidGlass.kt           # 玻璃拟态视觉
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

---

## 🔈 远程音箱使用说明

1. 确保两台手机连接到**同一个 Wi-Fi**
2. 在设备 A 打开"远程音箱"，选择「作为发送端」
3. 在设备 B 选择「作为接收端」
4. 设备 A 会自动发现同一网络下的设备 B，点击投送即可播放

> 通信机制：UDP 广播用于设备发现，TCP 用于稳定的音频数据传输，心跳保活防止连接掉线。

---

## 📌 说明

- 本项目仅供学习与技术交流使用。
- 所有音乐内容版权归各自平台及版权方所有，App 本身不存储任何音频文件。
- 音源接口可能随平台策略变化而失效，若遇到问题欢迎提 Issue。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request。

1. Fork 本项目
2. 创建特性分支：`git checkout -b feature/xxx`
3. 提交改动：`git commit -m 'feat: 添加 xxx'`
4. 推送分支：`git push origin feature/xxx`
5. 创建 Pull Request

---

## 📄 许可证

本项目基于 [Apache-2.0](LICENSE) 许可证开源。

---

<p align="center">Made with ❤️ and 🤖 AI by <a href="https://github.com/thyids">thyids</a></p>
