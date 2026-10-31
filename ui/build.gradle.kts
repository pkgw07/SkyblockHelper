import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

val androidEnabled = gradle.extra["androidEnabled"] as Boolean
if (androidEnabled) {
    extra["androidNamespace"] = "sbhelper.ui"
    apply(from = rootProject.file("gradle/android-library.gradle"))
}

// 앱 버전 (gradle.properties 의 appVersion, CI 에서는 태그 버전) → AppInfo.VERSION
val appVersion = findProperty("appVersion") as String
val generateAppInfo = tasks.register("generateAppInfo") {
    val outDir = layout.buildDirectory.dir("generated/appinfo")
    inputs.property("version", appVersion)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("sbhelper/ui/AppInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package sbhelper.ui\n\nobject AppInfo {\n    const val VERSION = \"$appVersion\"\n}\n")
    }
}

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    if (androidEnabled) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppInfo)
        }
        commonMain.dependencies {
            api(project(":core"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.components.resources)
            api(libs.compose.icons.extended)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "sbhelper.ui.generated"
    publicResClass = true
}
