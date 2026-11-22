plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wlgh.hgtv"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // 默认加载固定好的网页；构建时可用 -Phgtv.siteUrl=https://... 覆盖
        val site = (project.findProperty("hgtv.siteUrl") as String?)
            ?: "https://drdrdrrddrdr.github.io/huangguo-web/"
        buildConfigField("String", "SITE_URL", "\"$site\"")
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "device"
    productFlavors {
        create("phone") {
            dimension = "device"
            applicationId = "com.wlgh.hgtv"
            resValue("string", "app_name", "黄果解析")
        }
        create("tv") {
            dimension = "device"
            applicationId = "com.wlgh.hgtv.tv"
            resValue("string", "app_name", "黄果解析TV")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 纯系统 WebView 壳，无第三方运行时依赖，构建快、体积小。
}
