import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.coroutines.swing)
    implementation(libs.zxing.core)
    // 게임 오버레이: Windows 창 전환 신호·클릭 통과 (게임에는 손대지 않음)
    implementation(libs.jna.platform)
}

val appVersion = findProperty("appVersion") as String

compose.desktop {
    application {
        mainClass = "sbhelper.desktop.Boot"

        nativeDistributions {
            // Windows 는 설치 파일 .exe (설치 후 시작 메뉴/바탕화면에 바로가기)
            targetFormats(TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "SkyblockHelper"
            packageVersion = appVersion
            description = "Hypixel SkyBlock helper"
            vendor = "SkyblockHelper"
            includeAllModules = true

            windows {
                menu = true
                menuGroup = "SkyblockHelper"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                // 업그레이드 시 같은 프로그램으로 인식시키기 위한 고정값 (바꾸지 마세요)
                upgradeUuid = "7f1c5b8e-2f4d-4a7e-9b63-5d2a9c0e8f11"
                iconFile.set(project.file("icons/app.ico"))
            }
            macOS {
                iconFile.set(project.file("icons/app.icns"))
            }
            linux {
                iconFile.set(project.file("icons/app.png"))
            }
        }
    }
}

// CI 에서 화면 스크린샷을 만든다: ./gradlew :desktopApp:screenshots
tasks.register<JavaExec>("screenshots") {
    group = "verification"
    mainClass.set("sbhelper.desktop.ScreenshotsKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    jvmArgs("-Djava.awt.headless=true")
}

// CI 에서 앱 안 업데이트를 실제 설치본에 대해 확인한다
tasks.register<JavaExec>("updaterSelfTest") {
    group = "verification"
    mainClass.set("sbhelper.desktop.UpdaterSelfTestKt")
    classpath = sourceSets["main"].runtimeClasspath
    args((findProperty("selfTestArgs") as String? ?: "").split(";"))
}
