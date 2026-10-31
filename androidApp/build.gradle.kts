import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val appVersion = findProperty("appVersion") as String

android {
    namespace = "sbhelper.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.skyblockhelper"
        minSdk = 26
        targetSdk = 35
        versionName = appVersion
        // 1.2.3 -> 10203
        versionCode = appVersion.split(".").mapNotNull { it.toIntOrNull() }
            .let { (it + listOf(0, 0, 0)).take(3) }.let { it[0] * 10000 + it[1] * 100 + it[2] }
    }

    signingConfigs {
        // CI 는 ANDROID_KEYSTORE_* 환경변수로 서명 키를 넘긴다 (.github/workflows/build.yml). 없으면 디버그 키.
        create("release") {
            val ks = System.getenv("ANDROID_KEYSTORE_FILE")
            if (!ks.isNullOrBlank() && file(ks).isFile) {
                storeFile = file(ks)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/io.netty.versions.properties")
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.core)
    implementation(libs.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android)
}
