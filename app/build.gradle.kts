plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp) // YukiHookAPI 需要 ksp 来处理注解
}

android {
    namespace = "com.example.douyinhelp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.douyinhelp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // 配置支持的 CPU 架构（DexKit native 库需要）
        ndk {
            abiFilters.addAll(setOf("arm64-v8a", "armeabi-v7a", "x86_64"))
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
    // 1. DexKit 核心依赖
    implementation("org.luckypray:dexkit:2.0.1")

    // 2. YukiHookAPI 依赖与注解处理器
    implementation("com.highcapable.yukihookapi:api:1.2.0")
    ksp("com.highcapable.yukihookapi:ksp-xposed:1.2.0")

    
}
