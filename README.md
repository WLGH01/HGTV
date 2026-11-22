# HGTV · 黄果解析（网页 + Android / Android TV）

这个仓库包含两部分：

| 目录 | 内容 |
|---|---|
| `web/` | 修好后的黄果解析静态站（原 `huangguo-web`） |
| `android/` | WebView 壳工程，一次构建出 **手机 APK** 和 **Android TV APK** |

## 网页修复内容

- **选集/集数**：详情接口 `/api/videos/{id}` 自带 `episodes`，之前被 `fromItem()` 丢掉，播放页只能看到第 1 集；现在完整保留 `episodes`，按集数兜底，并修复了集数元信息。
- **排行榜 400**：榜单条目的 id 字段是 `video_id`，之前取 `id` 得到 `undefined`，点进详情就是 `HTTP 400`；现在优先取 `video_id`。
- **专辑专题**：列表项改用 `topic_{slug}`，专题内视频走 `/api/topics/{slug}`，不再把专题 id 当视频 id 去解析。
- **黄果吃瓜 410**：吃瓜是图文文章，不是视频。卡片改为进入站内文章详情，不再请求 `/api/videos/{postId}`。
- **工作台兜底**：Worker 有反爬签名校验，直连失败回落 Worker 时补上 `X-HG-Ts` / `X-HG-Sig`。
- **分类/排序**：源站已下线 AI换脸、AI魔改，且只支持 `latest` / `hot` 排序，前端同步收窄，避免 410/400。
- **分页**：列表响应补齐 `pagecount`，首页/榜单/专题可以继续滚动加载。

## Android 构建

### 本地构建

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

- 手机版：`com.wlgh.hgtv`，普通桌面图标
- TV 版：`com.wlgh.hgtv.tv`，带 `LEANBACK_LAUNCHER` 和 TV banner，可用遥控器操作

默认加载 **APK 内置**的网页：构建时 `web/` 会自动同步到 `android/app/src/main/assets/web/`，通过 `WebViewAssetLoader` 以 `https://appassets.androidplatform.net/...` 提供，因此是安全上下文，也不依赖线上 Pages 是否已更新。

需要调试线上版本时，可以覆盖加载地址：

```bash
./gradlew :app:assemblePhoneDebug -Phgtv.siteUrl=https://drdrdrrddrdr.github.io/huangguo-web/
```

### GitHub Actions

推送到 `main` 后自动运行 `.github/workflows/android.yml`：

1. 安装 JDK 17 / Gradle 8.9（Android SDK 使用 runner 预装版本）
2. 构建 `phone` 与 `tv` 两个 flavor 的 debug APK
3. 上传为 Actions Artifact：`HGTV-APK-<run_number>`
4. 同时发布一个滚动 Release `latest`，可直接下载：
   - [HGTV-phone-debug.apk](https://github.com/WLGH01/HGTV/releases/latest/download/HGTV-phone-debug.apk)
   - [HGTV-tv-debug.apk](https://github.com/WLGH01/HGTV/releases/latest/download/HGTV-tv-debug.apk)

### TV 遥控器

TV flavor 会在页面加载完成后注入 `tv-nav.js`：给 `.card` 补 `tabindex`，
方向键按屏幕位置做空间导航，Enter 打开卡片，焦点有黄色高亮。
手机 flavor 不注入，桌面浏览器行为不受影响。

## 说明

内容来自第三方公开站点，本项目仅作技术演示。请遵守当地法律法规与版权规则。
