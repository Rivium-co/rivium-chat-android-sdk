pluginManagement {
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

rootProject.name = "RiviumChat E-commerce Example"
include(":app")

// Include the RiviumChat SDK modules from parent directory
include(":rivium-chat")
include(":rivium-chat-ui")

project(":rivium-chat").projectDir = file("../rivium-chat")
project(":rivium-chat-ui").projectDir = file("../rivium-chat-ui")
