#!/bin/sh
# 打包 DTMF Generator Linux amd64 桌面版（内置 JRE，含图标与菜单项模板）
set -e
VER="1.0-Alpha"
NAME="DTMF-Generator-${VER}-linux-amd64"
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
DIST="$ROOT/dist/$NAME"

echo "== 1/4 构建 =="
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}"
gradle --no-daemon installDist --console=plain -q || /opt/gradle-8.13/bin/gradle --no-daemon installDist --console=plain -q

echo "== 2/4 组装目录 =="
rm -rf "$DIST"
mkdir -p "$DIST/bin/lib" "$DIST/share/applications" \
         "$DIST/share/icons/hicolor/512x512/apps" \
         "$DIST/share/icons/hicolor/256x256/apps" \
         "$DIST/share/icons/hicolor/128x128/apps"
cp "$ROOT"/build/install/DTMF-Generator-Desktop/lib/*.jar "$DIST/bin/lib/"
cp -r /opt/jdk-17.0.20.1+1-jre "$DIST/bin/runtime"

cat > "$DIST/bin/dtmf-generator" <<'LAUNCH'
#!/bin/sh
# DTMF Generator 1.0-Alpha (100) — Linux amd64
# Compose Multiplatform + Material 3；lib/ 与 runtime/ 与本脚本同级
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVA="$DIR/runtime/bin/java"
if [ ! -x "$JAVA" ]; then
  echo "错误：找不到 Java 运行时（$JAVA）" >&2
  echo "请确认解压完整：bin/{dtmf-generator,lib/,runtime/}" >&2
  exit 127
fi
if [ ! -d "$DIR/lib" ]; then
  echo "错误：找不到依赖目录（$DIR/lib）" >&2
  exit 127
fi
exec "$JAVA" -Xmx512m \
  -Dawt.useSystemAAFontSettings=on -Dswing.aatext=true \
  -cp "$DIR/lib/*" app.chumo.dtmf.MainKt "$@"
LAUNCH
chmod 755 "$DIST/bin/dtmf-generator"

cp /home/dsh/test/dtmf_icon_circle_1024.png "$DIST/share/icons/hicolor/512x512/apps/dtmf-generator.png"
cp /tmp/icongen/out_icon_256.png "$DIST/share/icons/hicolor/256x256/apps/dtmf-generator.png"
cp /tmp/icongen/out_icon_128.png "$DIST/share/icons/hicolor/128x128/apps/dtmf-generator.png"

cat > "$DIST/share/applications/dtmf-generator.desktop.in" <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=DTMF Generator
Comment=DTMF 拨号音生成器（Compose Multiplatform / Material 3）
Exec=@EXEC@
Icon=dtmf-generator
Terminal=false
Categories=Utility;AudioVideo;
StartupWMClass=app.chumo.dtmf.MainKt
DESKTOP

cat > "$DIST/install-desktop-entry.sh" <<'INSTALL'
#!/bin/sh
# 把菜单项和图标安装到当前用户（无需 root）
set -e
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APPS="$HOME/.local/share/applications"
ICONS="$HOME/.local/share/icons/hicolor"
mkdir -p "$APPS" "$ICONS/512x512/apps" "$ICONS/256x256/apps" "$ICONS/128x128/apps"
cp "$DIR/share/icons/hicolor/512x512/apps/dtmf-generator.png" "$ICONS/512x512/apps/"
cp "$DIR/share/icons/hicolor/256x256/apps/dtmf-generator.png" "$ICONS/256x256/apps/"
cp "$DIR/share/icons/hicolor/128x128/apps/dtmf-generator.png" "$ICONS/128x128/apps/"
sed "s|@EXEC@|$DIR/bin/dtmf-generator|" \
  "$DIR/share/applications/dtmf-generator.desktop.in" > "$APPS/dtmf-generator.desktop"
chmod 644 "$APPS/dtmf-generator.desktop"
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS" || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache -f -t "$ICONS" || true
echo "已安装菜单项：$APPS/dtmf-generator.desktop"
echo "（卸载：删除该文件及 $ICONS 下对应图标即可）"
INSTALL
chmod 755 "$DIST/install-desktop-entry.sh"

printf '1.0-Alpha (100)\n' > "$DIST/VERSION"
cat > "$DIST/README.txt" <<'README'
DTMF Generator 1.0-Alpha (100) — Linux amd64 桌面版
==================================================
版本号与 Android 版同步：1.0-Alpha（versionCode 100）

【一句话运行】
  ./bin/dtmf-generator

目录结构（lib 与 runtime 都在 bin/ 下，脚本自动定位，无需环境变量）
  bin/dtmf-generator              启动脚本
  bin/lib/*.jar                   Java 依赖（含 skiko-linux-x64）
  bin/runtime/                    内置 JRE 17（amd64，无需系统装 Java）
  share/applications/             菜单项模板（含图标）
  share/icons/hicolor/*/apps/     应用图标（512/256/128）
  install-desktop-entry.sh        安装菜单项到当前用户（可选）
  VERSION                         版本号

图标
  窗口/任务栏图标已内嵌（圆形白底，缩放 0.75，与 Android 版同一张图）。
  想出现在应用菜单里：./install-desktop-entry.sh

自检（排错用）
  ./bin/dtmf-generator --version
  应输出：DTMF Generator 1.0-Alpha (100) - Linux amd64 (...)

界面：Compose Multiplatform + Material 3（与 Android 版同一套组件）
Wayland 桌面经 XWayland 运行；保存目录 ~/DTMF/
许可：WTFPL v2
README
chmod 644 "$DIST/VERSION" "$DIST/README.txt"

echo "== 3/4 打包 =="
cd "$ROOT/dist"
rm -f "${NAME}.tar.gz"
tar czf "${NAME}.tar.gz" "$NAME"

echo "== 4/4 完成 =="
ls -la "${NAME}.tar.gz"
sha256sum "${NAME}.tar.gz" | awk '{print "sha256:", $1}'
echo "目录大小: $(du -sh "$NAME" | cut -f1)"
