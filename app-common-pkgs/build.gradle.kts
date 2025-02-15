plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

apply(plugin = "dagger.hilt.android.plugin")

android {
    namespace = "${ProjectConfig.packageName}.common.pkgs"
    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        minSdk = ProjectConfig.minSdk
        targetSdk = ProjectConfig.targetSdk
    }

    setupModuleBuildTypes()

    buildFeatures {
        viewBinding = true
    }

    setupCompileOptions()

    setupKotlinOptions()

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        tasks.withType<Test> {
            useJUnitPlatform()
            setupTestLogging()
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.Desugar.core}")
    implementation(project(":app-common"))
    implementation(project(":app-common-io"))
    implementation(project(":app-common-root"))
    testImplementation(project(":app-common-test"))

    addAndroidCore()
    addDI()
    addCoroutines()
    addTesting()

    implementation("com.github.d4rken.rxshell:core:v3.0.0")
    implementation("com.github.d4rken.rxshell:root:v3.0.0")

    testImplementation("org.robolectric:robolectric:4.9.1")
    testImplementation("androidx.test.ext:junit:1.1.4")
}