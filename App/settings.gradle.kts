pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://mvn.zetetic.net/public") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://mvn.zetetic.net/public") }
    }
    versionCatalogs {
        create("libs") {
            from(files("android/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "NudgeV1"
include(":android:app")
project(":android:app").name = "app"
include(":android")
