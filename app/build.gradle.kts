import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// release 签名（KEYS）：从 local.properties 读；缺失时 assembleRelease 直接失败（见下方 taskGraph 守卫）
val releaseProps = Properties().apply {
    runCatching { load(FileInputStream(rootProject.file("local.properties"))) }
}
fun hasReleaseKeystore() = releaseProps.getProperty("RELEASE_STORE_FILE") != null

android {
    namespace = "com.dndtimetable"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dndtimetable"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        // release 签名读 local.properties（RELEASE_* 键，密码不入库，见 桌面/密码.md）；
        // 缺配置时不回落 debug 密钥——打包 release 时由下方 taskGraph 守卫报错，避免误发签名错误的 APK。
        signingConfigs {
            create("release") {
                if (hasReleaseKeystore()) {
                    storeFile = rootProject.file(releaseProps.getProperty("RELEASE_STORE_FILE"))
                    storePassword = releaseProps.getProperty("RELEASE_STORE_PASSWORD")
                    keyAlias = releaseProps.getProperty("RELEASE_KEY_ALIAS")
                    keyPassword = releaseProps.getProperty("RELEASE_KEY_PASSWORD")
                        ?: releaseProps.getProperty("RELEASE_STORE_PASSWORD")
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        // Kotlin 2.0.21 下 lint 的 NonNullableMutableLiveDataDetector 有已知崩溃，跳过 release lint
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions {
        unitTests {
            // android.icu 等 stub 方法返回默认值（本地 JVM 单测兼容；不 mock 的 Android API 不能断言行为）
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// 打 release 包前检查签名配置：缺失直接失败并给出指引（test/debug/CI 构建不受影响）
gradle.taskGraph.whenReady {
    val needsSign = allTasks.any { t ->
        t.name.matches(Regex("(assemble|package|bundle|install)Release"))
    }
    check(!needsSign || hasReleaseKeystore()) {
        "local.properties 缺少 RELEASE_STORE_FILE 等签名配置，拒绝出 release 包" +
            "（防止误发 debug 签名的 APK）。请按 桌面/密码.md 配置 RELEASE_* 键。"
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // 图标全集（含 NotificationsOff 等）；release 经 R8 裁剪只保留实际用到的
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 本地单元测试（纯逻辑层）
    testImplementation("junit:junit:4.13.2")
    // JVM 单测用真 org.json（HolidayRemote 解析；android.jar 里的是 stub）
    testImplementation("org.json:json:20240303")
}
