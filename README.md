# DTMF Generator — Desktop (Linux amd64)

Compose Multiplatform + **Material 3** 桌面版，与 Android 版同源组件、同版本号（当前 `0.3-Release (33)`）。

## 环境要求

- JDK 17
- Gradle 8.x
- Linux x64 目标（交叉打包也支持：显式依赖 `desktop-jvm-linux-x64`，含 `skiko-linux-x64`）

## 构建

```bash
gradle installDist
# 产物：build/install/DTMF-Generator-Desktop/{bin,lib}
```

运行（开发）：

```bash
build/install/DTMF-Generator-Desktop/bin/DTMF-Generator-Desktop
```

## 打包成免安装目录（内置 JRE）

```bash
# 目录结构：bin/{dtmf-generator, lib/, runtime/}
tar czf DTMF-Generator-0.3-Release-linux-amd64.tar.gz DTMF-Generator-0.3-Release-linux-amd64
```

启动脚本 `bin/dtmf-generator` 会自动定位同级的 `lib/` 与 `runtime/`。

## 功能

- 号码输入（数字 / `*` / `#`）
- 音频时长：100 / 200 / 自定义（1–10000 ms）
- 音频间隔：20 / 50 / 100 / 0（实验性）/ 自定义（0–10000 ms）
- 播放 DTMF（44.1 kHz / 16 bit / 单声道，javax.sound.sampled）
- 保存 WAV 到 `~/DTMF/`
- 拨号键盘页：按住持续发声，松手停止
- 关于弹窗（含 GitHub 链接）

## 说明

- Wayland 桌面经 XWayland 运行（Compose Desktop 的窗口层基于 AWT/Skiko）
- 许可：WTFPL v2
