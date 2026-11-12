# FNTV · 飞牛影视 TV / 手机客户端（在线扩展 + 弹幕）

> 基于 [rgcaafe/fnos_tv_danmu](https://github.com/rgcaafe/fnos_tv_danmu) 的二次开发版本：
> 修复在线资源播放、重做 TV 端界面、补齐日志与自动更新。
> 本仓库的所有下载与更新都指向 **tianlmm/fnos_tv_danmu** 自己的 Releases。

## 下载

从 [Releases](https://github.com/tianlmm/fnos_tv_danmu/releases/latest) 页面下载最新版本的 APK 安装包，在 Android 电视 / 手机上安装即可使用。

- 最新版本：**v1.3.5**
- 直链：`https://github.com/tianlmm/fnos_tv_danmu/releases/download/v1.3.5/FNTV_1.3.5.apk`
- App 内「设置 → 检查更新」也会从本仓库 `update.json` 自动检测新版本

## 主要特性

- **兼容「影视在线扩展」fnmedia-ext**：扩展注入的热门电影 / 热门剧集 / 动漫新番 / 热门综艺、海量四库、小雅系列都会出现在首页，点开即播；已修复在线资源起播黑屏（HLS 识别）。
- **弹幕**：对接 Danmu API（默认 `http://<NAS>:9321`），按片名自动匹配 B 站 / 腾讯 / 爱奇艺等弹幕源，匹配不到可手动搜索；支持密度、速度、透明度、屏蔽词。
- **播放**：ExoPlayer，硬解 / 软解切换，HLS / MP4 / STRM / 网盘直链，倍速，断点续播，自动连播。
- **界面**：顶部标题 + 横向媒体库标签条，分区单行横滑（3 张完整 + 第 4 张露一点），3:4 海报卡，遥控器全界面可操作。
- **搜索**：首页右上角 🔍，聚合本地与在线结果，结果 3 列网格。
- **日志**：运行日志（单文件 512KB 上限、最新在最上面）+ 崩溃记录，设置页可导出分享。
- **在线更新**：检查更新 → 从本仓库 Releases 下载安装。

## 安装

1. 下载 APK；
2. 电视 / 手机允许「安装未知来源应用」；
3. 打开 App，服务器填 `http://<你的NAS地址>:5666`，账号密码用**飞牛影视的账号**（不是 fnOS 系统账号）；
4. 弹幕服务器默认按 NAS 地址推算为 `:9321`，未部署 danmu-api 时可在设置里改成自己的地址。

> 从旧版 `fnx*` 测试包（debug 签名）升级，需要先卸载再安装；从 1.3.0 起为正式签名，可直接覆盖升级。

## 环境要求

| 项 | 要求 |
|---|---|
| 系统 | Android 4.4（API 19）及以上 |
| 服务端 | 飞牛影视（trim.media）已安装并启用 |
| 在线资源（可选） | [fnmedia-ext](https://github.com/yujian0815/fnmedia-ext) 影视在线扩展 |
| 弹幕（可选） | [danmu_api](https://github.com/huangxd-/danmu_api)，默认端口 9321 |

## 版本历史（本仓库）

- **1.3.5** 问题反馈改为本仓库 Issues，README/仓库信息全部指向本仓库
- **1.3.4** 修复部分片源起播 1.5 秒后闪退（HDR 检查空指针）
- **1.3.3** 首页预览错峰加载，启动重复请求去重
- **1.3.2** 图片请求日志静默、失败海报负缓存
- **1.3.1** 日志倒序 + 图片内存/磁盘缓存优化
- **1.3.0** 首个正式版：在线播放修复、首页改版、搜索、日志、更新源改为本仓库

## 构建

```bash
# 需要 JDK 11 + Android SDK 33（platforms;android-33、build-tools;33.0.2）
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleRelease
```

正式签名使用仓库外的 `keystore.properties`（指向 `.jks`，已在 `.gitignore` 中排除）。
没有该文件时 `assembleRelease` 会产出未签名包。

## 致谢

- [**rgcaafe/fnos_tv_danmu**](https://github.com/rgcaafe/fnos_tv_danmu) — 上游项目
- [**fntv-electron**](https://github.com/QiaoKes/fntv-electron) — 飞牛影视 API 接口逻辑参考
- [**fnmedia-ext**](https://github.com/yujian0815/fnmedia-ext) — 影视在线扩展（本客户端重点适配对象）
- [**Danmu API**](https://github.com/huangxd-/danmu_api) — 弹幕数据服务
- **ExoPlayer** — 播放器

## 声明

本项目为第三方客户端，与飞牛官方无关。在线资源来自用户自行配置的扩展与接口，
本项目不提供、不存储、不分发任何影视内容，请自行确保合法使用。
License：[GPL-3.0](LICENSE)
