#!/bin/bash
# 下载 Termux 动态编译版 cloudflared（解决 DNS [::1]:53 问题）
# 同时下载到 assets 和 jniLibs
set -e
DEST_ASSETS="app/src/main/assets"
DEST_LIB="app/src/main/jniLibs/arm64-v8a"
mkdir -p "$DEST_ASSETS" "$DEST_LIB"

echo "⬇ 下载 cloudflared (Termux 动态版)..."
TMP_DEB="/tmp/cloudflared-termux.deb"
curl -fsSL -o "$TMP_DEB" "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main/pool/main/c/cloudflared/cloudflared_2026.8.2_aarch64.deb"

echo "📦 解包..."
TMP_DIR=$(mktemp -d)
cd "$TMP_DIR"
ar x "$TMP_DEB"
tar xf data.tar.xz

echo "✏️ 二进制补丁: resolv.conf 路径 → App 可写路径"
python3 << 'PYPATCH'
import shutil
old_path = b"/data/data/com.termux/files/usr/etc/resolv.conf"
new_path = b"/data/data/com.rrt.tracker/files/xx/resolv.conf"
assert len(old_path) == len(new_path), "路径长度不一致"
src = "data/data/com.termux/files/usr/bin/cloudflared"
with open(src, "rb") as f:
    data = bytearray(f.read())
count = data.count(old_path)
if count == 0:
    print("⚠️ 未找到 resolv.conf 路径，跳过补丁")
else:
    data = data.replace(old_path, new_path)
    with open(src, "wb") as f:
        f.write(data)
    print(f"✅ 补丁完成 ({count} 处)")
shutil.copy(src, f"{'$DEST_ASSETS'}/cloudflared")
shutil.copy(src, f"{'$DEST_LIB'}/libcloudflared.so")
print("✅ 已复制到 assets 和 jniLibs")
PYPATCH

cd - > /dev/null
echo "✅ 完成！现在可以构建 APK: ./gradlew assembleRelease"
