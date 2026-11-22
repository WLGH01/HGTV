# HGTV · 黄果解析（原生 Android / Android TV）

仓库包含两部分：

| 目录 | 内容 |
|---|---|
| `web/` | 修好后的黄果解析网页（原 `huangguo-web`），用于网页版/参考 |
| `android/` | **原生** Android 工程：手机和 Android TV 两套独立 Compose UI，不再使用 WebView 套壳 |

## 原生版特性

### 手机端（phone flavor）

- Material3 深色界面，2 列海报网格
- 短剧 / 漫剧 / 排行榜（热播、推荐、潜力）/ 吃瓜
- 搜索、分页加载、详情页、选集
- ExoPlayer 播放 HLS，自定义控制条：进度条、上一集/下一集、暂停
- **倍速：0.5x / 0.75x / 1.0x / 1.25x / 1.5x / 1.75x / 2.0x**

### TV 端（tv flavor）

- 独立设计的大屏界面：左侧导航栏 + 大幅背景 + 横向大海报卡片
- 遥控器方向键空间导航、焦点高亮/缩放、Enter 打开
- 详情页大封面 + 选集按钮，播放页独立控制层
- 倍速、上一集/下一集、±10 秒，全部可用遥控器操作

### 共用能力

- 源站 JSON API：分类、详情、榜单、专题、搜索、吃瓜
- 封面 AES-CBC 解密（与网页版同一套 key/iv）
- 播放地址走 Worker `/api/play`，原生实现 HMAC-SHA256 签名
- HLS 由 Media3 ExoPlayer 直接播放，支持 AES-128 分片
- 无 WebView、无内嵌网页资源

## 包名

| 版本 | applicationId |
|---|---|
| 手机 | `com.wlgh.hgtv` |
| TV | `com.wlgh.hgtv.tv` |

## 构建

需要 JDK 17 和 Android SDK 35：

```bash
cd android
./gradlew :app:assemblePhoneDebug :app:assembleTvDebug
```

产物：

```
android/app/build/outputs/apk/phone/debug/app-phone-debug.apk
android/app/build/outputs/apk/tv/debug/app-tv-debug.apk
```

## GitHub Actions

推送到 `main` 自动运行 `.github/workflows/android.yml`：

1. JDK 17 + Gradle 8.9（Android SDK 使用 runner 预装）
2. 构建 phone / tv 两个原生 APK
3. 上传 Actions Artifact
4. 更新滚动 Release `latest`，可直接下载：
   - [HGTV-phone-debug.apk](https://github.com/WLGH01/HGTV/releases/latest/download/HGTV-phone-debug.apk)
   - [HGTV-tv-debug.apk](https://github.com/WLGH01/HGTV/releases/latest/download/HGTV-tv-debug.apk)

## 说明

- APK 为 debug 签名，适合侧载；上架应用商店需要自行配置 release 签名。
- 内容来自第三方公开站点，本项目仅作技术演示。请遵守当地法律法规与版权规则。
