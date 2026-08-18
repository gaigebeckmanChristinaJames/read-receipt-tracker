// Android 构建（纯 Java，零第三方依赖）
plugins {
    id("com.android.application")
}

android {
    namespace = "com.rrt.tracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rrt.tracker"
        minSdk = 21
        targetSdk = 34
        versionCode = 5
        versionName = "2.3.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 零第三方依赖：仅用 Android 框架 API
}
