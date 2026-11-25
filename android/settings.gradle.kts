pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        // Only Tesseract4Android (Apache-2.0) is published there; nothing else may resolve from it.
        maven("https://jitpack.io") { content { includeGroupByRegex("com\\.github\\.adaptech-cz(\\..*)?") } }
    }
}
rootProject.name = "BuchRadar"
include(":app")
