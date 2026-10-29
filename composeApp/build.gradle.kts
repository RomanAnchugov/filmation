import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
}

compose {
    resources {
        packageOfResClass = "com.filmation.res"
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // Набор иконок заморожен на 1.7.3 — норм для пары кнопок в AppBar
            implementation(compose.materialIconsExtended)
            // Строки UI: единый источник + локализация (values/ = ru, values-en/ = en)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.exifinterface)
        }
    }
}

android {
    namespace = "com.filmation"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Compose Preview (IntelliJ/Android Studio) требует ресурсы в unit-тестах
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Compose Preview в IntelliJ IDEA требует unit-тесты у варианта (для упаковки ресурсов
// в resource APK под Layoutlib). KMP по умолчанию выключает их у release — включаем,
// иначе Gradle Sync падает с "Could not create task ':composeApp:generateReleaseComposePreviewRunfiles'".
// Compose Preview в IntelliJ IDEA требует unit-тесты у варианта (для упаковки ресурсов
// в resource APK под Layoutlib). KMP по умолчанию выключает их у release — включаем,
// иначе Gradle Sync падает с "Could not create task ':composeApp:generateReleaseComposePreviewRunfiles'".
// Внимание: старые свойства enableUnitTest/unitTestEnabled в AGP 9 скрыты (DeprecationLevel.HIDDEN),
// поэтому используется новый API через HasHostTestsBuilder.hostTests.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as? com.android.build.api.variant.HasHostTestsBuilder)
            ?.hostTests
            ?.get(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE)
            ?.enable = true
    }
}
