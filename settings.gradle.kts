pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()}
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "KiesoCounter_v3_1_1"
include(":app")