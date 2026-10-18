plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
}

android {
    namespace = "blbl.cat3399"
    compileSdk = 36

    defaultConfig {
        applicationId = "blbl.cat3399"
        minSdk = 21
        targetSdk = 36
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = project.findProperty("versionName") as String? ?: "0.1.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += "armeabi-v7a"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/new_key.jks")
            
            // 如果没读到环境变量，这里会直接抛异常，而不是等打包时报 null
            val pass = System.getenv("RELEASE_STORE_PASSWORD")
            val alias = System.getenv("RELEASE_KEY_ALIAS")
            val keyP = System.getenv("RELEASE_KEY_PASSWORD")

            if (pass != null && alias != null && keyP != null) {
                storePassword = pass
                keyAlias = alias
                keyPassword = keyP
            } else {
                throw GradleException("Missing signing secrets! Please set RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, and RELEASE_KEY_PASSWORD in GitHub Secrets.")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (System.getenv("RELEASE_STORE_PASSWORD") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
            )
        }
        jniLibs {
            excludes += setOf("**/libijkplayer.so")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        register("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.72.0"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") {
                    option("lite")
                }
            }
            task.plugins {
                register("grpc") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(files("libs/ijkplayer-cmake-release.aar"))

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.brotli:dec:0.1.2")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")
    implementation("io.grpc:grpc-okhttp:1.72.0")
    implementation("io.grpc:grpc-protobuf-lite:1.72.0")
    implementation("io.grpc:grpc-stub:1.72.0")
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

val checkThemeTokens =
    tasks.register("checkThemeTokens") {
        group = "verification"
        description = "Fails if layouts reference fixed palette colors instead of theme attributes."

        doLast {
            val resDir = file("src/main/res")
            val layoutDirs = resDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("layout") }.orEmpty()
            fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

            fun containsWholeToken(line: String, token: String): Boolean {
                var fromIndex = 0
                while (true) {
                    val idx = line.indexOf(token, startIndex = fromIndex)
                    if (idx < 0) return false
                    val before = line.getOrNull(idx - 1)
                    val after = line.getOrNull(idx + token.length)
                    if ((before == null || !isWordChar(before)) && (after == null || !isWordChar(after))) return true
                    fromIndex = idx + token.length
                }
            }

            val forbidden = listOf(
                "@color/blbl_bg", "@color/blbl_surface", "@color/blbl_text",
                "@color/blbl_text_secondary", "@color/blbl_focus_stroke", "@drawable/blbl_focus_bg_round"
            )

            val violations = mutableListOf<String>()
            for (dir in layoutDirs) {
                dir.walkTopDown().filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }.forEach { f ->
                    val relPath = f.relativeTo(projectDir).invariantSeparatorsPath
                    f.readLines(Charsets.UTF_8).forEachIndexed { index, line ->
                        for (token in forbidden) if (containsWholeToken(line, token)) violations.add("$relPath:${index + 1}: $token")
                    }
                }
            }
            if (violations.isNotEmpty()) throw GradleException("Theme token check failed:\n" + violations.joinToString("\n"))
        }
    }

tasks.named("preBuild").configure { dependsOn(checkThemeTokens) }