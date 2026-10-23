# DTMF Generator for Linux amd64

Compose Multiplatform，与 Android 版同源。

## 环境

- JDK 17
- Gradle 8.x

## 构建

```bash
gradle installDist
# build/install/DTMF-Generator-Desktop/{bin,lib}
```

运行（开发）：

```bash
build/install/DTMF-Generator-Desktop/bin/DTMF-Generator-Desktop
```

## 打包

```bash
# bin/{dtmf-generator, lib/, runtime/}
tar czf DTMF-Generator-0.3-Release-linux-amd64.tar.gz DTMF-Generator-0.3-Release-linux-amd64
```

## 说明

- 经XWayland运行（Compose Desktop 的窗口层基于 AWT/Skiko）
- **WTF**PL v2

> -1. just fuck wtf u want to fuck yay
