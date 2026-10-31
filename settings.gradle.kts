rootProject.name = "SkyblockHelper"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
    }
}

// Android 빌드는 Android SDK가 있을 때만 켠다 (Android Studio가 설치된 PC, GitHub Actions 등).
// 강제로 끄려면: 환경변수 SBH_ANDROID=false 또는 gradle 속성 -Psbh.android=false
val androidEnabled: Boolean = run {
    val forced = System.getenv("SBH_ANDROID") ?: providers.gradleProperty("sbh.android").orNull
    if (forced != null) return@run forced.toBoolean()
    val localProps = file("local.properties")
    val sdkFromLocal = if (localProps.exists()) {
        localProps.readLines().firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("=")
    } else null
    val sdk = sdkFromLocal ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    sdk != null && file(sdk).isDirectory
}
gradle.extra["androidEnabled"] = androidEnabled

include(":core", ":ui", ":desktopApp")
if (androidEnabled) include(":androidApp")
