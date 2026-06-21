pluginManagement {
    repositories {
        gradlePluginPortal()
        // detekt 2.x (alpha) は dev.detekt namespace で Maven Central のみに公開されているため追加する
        mavenCentral()
    }
}

rootProject.name = "api"

// detekt のカスタムルール（プロジェクト固有のアーキテクチャ規約強制）を提供するモジュール。
// 本体 build の detektPlugins に組み込んで使う。
include(":detekt-rules")
