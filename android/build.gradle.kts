// Must come first in a Kotlin DSL build script.
buildscript {
    dependencies {
        // Pin JavaPoet on the plugin classpath.
        //
        // AGP pulls in JavaPoet 1.10, Hilt's Gradle plugin calls
        // ClassName.canonicalName() which arrived in 1.13, and Gradle resolves
        // the plugin classpath to the older one. The result is
        // hiltAggregateDepsDebug failing with the bare method signature as its
        // message -- a NoSuchMethodError wearing no explanation at all.
        //
        // Belongs here rather than in app/: it is the *plugin* classpath that
        // is wrong, not the app's.
        classpath("com.squareup:javapoet:1.13.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}
