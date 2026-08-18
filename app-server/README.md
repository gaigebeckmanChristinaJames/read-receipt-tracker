# 📱 已读追踪 Android App 源码

零依赖（纯 Android 框架 API），可在 **电脑终端** 或 **Termux** 本地构建 APK。

## 目录结构

```
android/
├── build.gradle.kts              # 根构建配置
├── settings.gradle.kts
├── gradle.properties
├── gradlew                       # Gradle Wrapper
├── gradle/wrapper/
├── fetch-cloudflared.sh          # 下载+patch cloudflared 二进制
└── app/
    ├── build.gradle.kts          # minSdk 21, 零第三方依赖
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/cloudflared    # Termux 动态版（含 DNS 路径补丁）
        ├── jniLibs/arm64-v8a/libcloudflared.so
        ├── java/com/rrt/tracker/
        │   ├── MainActivity.java
        │   ├── TrackerService.java
        │   ├── Database.java
        │   ├── GeoLookup.java
        │   ├── ConsoleHtml.java
        │   ├── ConsoleActivity.java
        │   └── LogActivity.java
        └── res/
```

## 🖥 电脑终端构建

```bash
# 1. 解压
tar xzf android-src.tar.gz
cd android

# 2. 安装 JDK 17（Ubuntu/Debian）
sudo apt install openjdk-17-jdk

# 3. 下载 Android SDK cmdline-tools
mkdir -p ~/android-sdk && cd ~/android-sdk
curl -fL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip cmdtools.zip
mkdir -p cmdline-tools/latest
mv cmdline-tools/bin cmdline-tools/lib cmdline-tools/NOTICE.txt cmdline-tools/source.properties cmdline-tools/latest/
export ANDROID_HOME=~/android-sdk
yes | cmdline-tools/latest/bin/sdkmanager --licenses
cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0"

# 4. 构建 APK
cd ~/android
./gradlew assembleRelease

# 5. 签名（首次生成密钥）
keytool -genkeypair -keystore rrt.jks -alias rrt -keyalg RSA -keysize 2048 -validity 10000
# 按提示填信息（密码自定，例如 rrt123456）

zipalign -f 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
apksigner sign --ks rrt.jks --out 已读追踪.apk aligned.apk

# 完成！已读追踪.apk 可直接安装
```

## 📱 Termux 构建（手机本地生成 APK）

```bash
# 1. 解压源码到 Termux
tar xzf android-src.tar.gz
cd android

# 2. 安装 JDK
pkg install openjdk-17

# 3. 下载 Android SDK
cd ~
curl -fL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip cmdtools.zip
mkdir -p android-sdk/cmdline-tools/latest
mv cmdline-tools/bin cmdline-tools/lib cmdline-tools/NOTICE.txt cmdline-tools/source.properties android-sdk/cmdline-tools/latest/
export ANDROID_HOME=~/android-sdk
yes | android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0"

# 4. 构建（Termux 上 gradlew 可能需改 JAVA_HOME）
cd ~/android
./gradlew assembleRelease

# 5. 签名
keytool -genkeypair -keystore rrt.jks -alias rrt -keyalg RSA -keysize 2048 -validity 10000
~/android-sdk/build-tools/34.0.0/zipalign -f 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
~/android-sdk/build-tools/34.0.0/apksigner sign --ks rrt.jks --out 已读追踪.apk aligned.apk
```

## 🔧 cloudflared 二进制（已内置，可选重新下载）

`assets/cloudflared` 和 `jniLibs/arm64-v8a/libcloudflared.so` 已包含：

- **来源**：Termux 官方仓库动态编译版（用 Android 系统 DNS）
- **补丁**：`/data/data/com.termux/.../resolv.conf` → `/data/data/com.rrt.tracker/files/xx/resolv.conf`（App 可写路径）

如需重新下载/更新：

```bash
bash fetch-cloudflared.sh
```

## ⚠️ 注意事项

1. **签名密钥**：`rrt.jks` 要妥善保存，更新 APK 必须用同一密钥才能覆盖安装
2. **首次构建**：Gradle 会自动下载依赖（需网络）
3. **gradle 版本**：8.5（wrapper 已配置，自动下载）
4. **Termux 构建**：若内存不足，在 `gradle.properties` 减小 `-Xmx2048m` 为 `-Xmx1024m`
