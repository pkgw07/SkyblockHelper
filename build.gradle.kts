// Android Gradle Plugin은 Android SDK가 있을 때만 불러온다 (settings.gradle.kts 참고).
buildscript {
    val androidEnabled = gradle.extra["androidEnabled"] as Boolean
    if (androidEnabled) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath("com.android.tools.build:gradle:8.7.3") // libs.versions.toml 의 agp 와 맞출 것
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
}
