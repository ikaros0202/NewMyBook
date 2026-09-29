pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "XinYue"

include(":app")
include(":benchmark")
include(":core:domain")
include(":core:text")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":feature:library")
include(":feature:home")
include(":feature:settings")
include(":feature:import")
include(":feature:reader")
include(":feature:backup")
