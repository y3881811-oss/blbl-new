plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
}

// 👇 你的签名变量配置 👇
val storePass: String = System.getenv("KEYSTORE_PASSWORD") ?: "mtfm123456"
val aliasName: String = System.getenv("KEY_ALIAS") ?: "mtfm"
val keyPass: String = System.getenv("KEY_PASSWORD") ?: "mtfm123456"

android {
    namespace = "blbl.cat3399"
    compileSdk = 36

    fun propOrEnv(name: String): String? {
        val fromProp = project.findProperty(name) as String?
        if (!fromProp.isNullOrBlank()) return fromProp
        val fromEnv = System.getenv(name)
        if (!fromEnv.isNullOrBlank()) return fromEnv
        return null
    }

    defaultConfig {
        applicationId = "blbl.cat3399"
        minSdk = 21 
        targetSdk = 36
        versionCode = (propOrEnv("versionCode") ?: "1").toInt()
        versionName = propOrEnv("versionName") ?: "1.0.0"

        // 👇 锁死 32 位 👇
        ndk {
            abiFilters += "armeabi-v7a"
        }
    }

    // 👇 绑定你的固定签名 👇
    signingConfigs {
        create("mtfm") {
            storeFile = rootProject.file("keystore/mtfm.jks")
            storePassword = storePass
            keyAlias = aliasName
            keyPassword = keyPass
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("mtfm")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("mtfm")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
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

// 👇 核心救命配置：强制让 Gradle 下载匹配版本的 protoc 👇
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.12"
    }
}