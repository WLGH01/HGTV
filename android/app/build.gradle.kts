import org.gradle.api.tasks.Sync

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 每次构建前把仓库根目录的 web/ 同步进 assets，
// 保证 APK 里的页面永远和仓库里的修复版本一致，不依赖线上 Pages 是否已更新。
val syncWebAssets by tasks.registering(Sync::class) {
    from(rootProject.projectDir.parentFile.resolve("web"))
    into(layout.projectDirectory.dir("src/main/assets/web"))
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(syncWebAssets) }

android {
    namespace = "com.wlgh.hgtv"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // 默认加载 APK 内置的网页（构建时自动同步 web/）；
        // 需要调试线上版本时用 -Phgtv.siteUrl=https://... 覆盖。
        val site = (project.findProperty("hgtv.siteUrl") as String?)
            ?: "https://appassets.androidplatform.net/assets/web/index.html"
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
    // WebViewAssetLoader：用 https://appassets.androidplatform.net 提供内置页面，
    // 这样是安全上下文（WebCrypto/剪贴板等可用），也避免 file:// 的 CORS 限制。
    implementation("androidx.webkit:webkit:1.11.0")
}
