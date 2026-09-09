plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.dhuelin.watchguru"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.dhuelin.watchguru"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Read from the environment so no key material is ever in the repo.
            // Absent locally, in which case the release build stays unsigned
            // and `assembleDebug` is unaffected.
            val keystorePath = System.getenv("WATCHGURU_KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("WATCHGURU_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WATCHGURU_KEY_ALIAS")
                keyPassword = System.getenv("WATCHGURU_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // A local backend over cleartext; see network_security_config.xml,
            // which permits it for 10.0.2.2 (the emulator's host loopback) only.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
            applicationIdSuffix = ".debug"
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://api.watch-guru.example/\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
}

dependencies {
    implementation(project(":api-client"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
