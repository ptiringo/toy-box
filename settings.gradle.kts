pluginManagement {
    repositories {
        gradlePluginPortal()
        // detekt 2.x (alpha) は dev.detekt namespace で Maven Central のみに公開されているため追加する
        mavenCentral()
    }
}

rootProject.name = "api"
