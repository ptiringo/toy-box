plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktfmt)
}

// このモジュールは Spring を含まないプレーンな Kotlin ライブラリ。
// 本体（root）の detektPlugins から project 依存で取り込まれ、detekt 実行時にカスタムルールを提供する。
repositories { mavenCentral() }

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

// 本体（root）と同じく、コンパイラ警告をエラー扱いにして警告ゼロ運用を強制する。
kotlin { compilerOptions { allWarningsAsErrors = true } }

dependencies {
    // detekt のルール API（PSI は kotlin-compiler 経由で推移的に入る）。実行時は detekt 本体が供給するため compileOnly。
    compileOnly(libs.detekt.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.detekt.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

ktfmt { kotlinLangStyle() }

tasks.withType<Test> { useJUnitPlatform() }
