pluginManagement {
    repositories {
        // AGP and the AndroidX libraries live on Google's Maven only; a build
        // that cannot reach it will fail at dependency resolution rather than
        // at compile time, which is a confusing first experience.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "watch-guru"
include(":app")
include(":api-client")
