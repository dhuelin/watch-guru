plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.huelin.watchguru"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.huelin.watchguru"
        minSdk = 26
        targetSdk = 36
        // Properties, not literals: Play rejects a second upload carrying a
        // versionCode it has already seen, so a release pipeline has to be
        // able to set it from outside. The defaults are what a local build
        // gets, which is all a local build needs.
        versionCode = providers.gradleProperty("watchguru.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("watchguru.versionName").orNull ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Google OAuth *web* client id, not the Android one: Credential
        // Manager mints an ID token whose audience is the web client, and that
        // audience is what the backend's WATCH_GURU_AUTH_AUDIENCES must list.
        // Empty when unset, which SignInViewModel surfaces as a configuration
        // error rather than a mysterious sign-in failure.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${providers.gradleProperty("watchguru.googleWebClientId").orNull ?: ""}\"",
        )
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
            // The deployed backend. A property so that a release can point at
            // a real one; the default is a placeholder that resolves to
            // nothing, and shipping it would produce an app that installs and
            // then cannot sign in. The release workflow refuses to build
            // without the property set, which is where that is caught.
            val apiBaseUrl = providers.gradleProperty("watchguru.apiBaseUrl").orNull
                ?: "https://api.watch-guru.example/"
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
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
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

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
