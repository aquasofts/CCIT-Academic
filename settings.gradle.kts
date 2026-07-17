pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Cithub"

include(":app")
include(":core:academic")
include(":core:captcha")
include(":core:ui")
include(":core:webvpn")
include(":feature:captcha-autofill")
include(":feature:home")
include(":feature:tieba")
include(":material-color-utilities")
include(":placeholder")
