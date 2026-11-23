# HGTV · 黄果解析（原生 Android / Android TV）

仓库包含两部分：

| 目录 | 内容 |
|---|---|
| `web/` | 修好后的黄果解析网页（原 `huangguo-web`），用于网页版/参考 |
| `android/` | **原生** Android 工程：手机和 Android TV 两套独立 Compose UI，不再使用 WebView 套壳 |

## 原生版特性

### 手机端（phone flavor）

- 首页分区：精选推荐 / 最近上新 / 黄果原创 / 热播短剧 / 热门标签 / 专题推荐
- 短剧 / 漫剧 / 原创 / 排行榜 / 专题 / 吃瓜 / 博客 / 我的
- 标签筛选、搜索、分页、详情页、选集、猜你喜欢
- 本地收藏与观看历史
- ExoPlayer 播放 HLS；控制层自动隐藏
- 手势：左右滑动调进度、长按 2 倍速、点击显示/隐藏控制层
- **倍速：0.5x / 0.75x / 1.0x / 1.25x / 1.5x / 1.75x / 2.0x**（播放器底部「倍速」）
- **横屏/竖屏切换**：播放器右上角按钮，横屏时自动隐藏系统栏

### TV 端（tv flavor）

- 独立设计的大屏界面：左侧图标导航 + 横向海报卡片，无大幅 Banner
- 短剧 / 漫剧 / 原创 / 排行榜均为**上下滚动的海报网格**；最近上新与热播在栏目标题处切换
- 详情页完整信息（评分、年份、作者、热度、标签、简介、选集、猜你喜欢）
- 遥控器方向键空间导航、焦点高亮/缩放、自动滚动到可视区域
- 播放器默认只显示画面：左右键快进/快退 10 秒，底部仅显示细进度条与时间
- **按中键暂停后才弹出设置面板**：继续播放 / 快退快进 30 秒 / 上一集下一集 / 倍速（收起状态，按需展开）/ 选集
- **设置里可调整整体 UI 比例：60%–120%**，默认 100%（基准已按大屏适配缩小），保存到本机
- TV 版不包含吃瓜/博客

### 网页版（web/）

- 新增 `home.html` 首页分区、`original.html` 黄果原创、`tag.html` 标签筛选、`blog.html` 博客
- 播放页新增「猜你喜欢」
- 导航统一为：首页 / 全部剧集 / 排行榜 / 专题 / 黄果原创 / 吃瓜 / 博客 / 接口

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
