package com.example.api.dbdoc

import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * DB スキーマドキュメント（dbdoc/）の生成・検査エントリポイント（#447）。
 *
 * Testcontainers で本番ターゲットと同じ PostgreSQL を起動し、Flyway で db/migration の V*.sql を 適用したうえで tbls CLI（mise
 * 管理・PATH 上）を子プロセスで実行する。ランタイムは H2 だが ドキュメントは本番型 PostgreSQL で出す（設計:
 * docs/superpowers/specs/2026-06-30-tbls-db-schema-docs-design.md）。
 *
 * 引数 mode:
 * - "generate": tbls doc で dbdoc/ を再生成する（開発者が手動実行し差分をコミットする）。
 * - "check": tbls diff（dbdoc/ が最新か）と tbls lint（コメント必須）を検査する（CI ゲート）。
 */
private const val POSTGRES_IMAGE = "postgres:17-alpine"

fun main(args: Array<String>) {
    val mode = args.firstOrNull() ?: error("mode が必要です: generate | check")
    val container = PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
    container.start()
    try {
        migrate(container)
        val dsn = buildDsn(container)
        when (mode) {
            "generate" -> runTbls(dsn, listOf("doc", "--rm-dist", "--force"))
            "check" -> {
                runTbls(dsn, listOf("diff"), failOnNonEmptyStdout = true)
                runTbls(dsn, listOf("lint"))
            }
            else -> error("未知の mode: $mode（generate | check）")
        }
    } finally {
        container.stop()
    }
}

private fun migrate(container: PostgreSQLContainer) {
    Flyway.configure()
        .dataSource(container.jdbcUrl, container.username, container.password)
        .load()
        .migrate()
}

private fun buildDsn(container: PostgreSQLContainer): String =
    "postgres://${container.username}:${container.password}@" +
        "${container.host}:${container.firstMappedPort}/${container.databaseName}?sslmode=disable"

/**
 * tbls サブコマンドを子プロセスで実行する。出力は標準出力/標準エラーへ継承する。 非ゼロ終了、または failOnNonEmptyStdout 指定時に標準出力が非空なら例外で失敗させる
 * （Gradle JavaExec が非ゼロ終了に変換しタスクを失敗させる）。
 */
private fun runTbls(dsn: String, args: List<String>, failOnNonEmptyStdout: Boolean = false) {
    // tbls 1.94.5 は cwd の .tbls.yml を自動探索しないため、設定ファイルを --config で明示指定する
    // （build.gradle.kts がシステムプロパティ tbls.config に絶対パスを渡す）。サブコマンド直後へ挿す。
    val command = listOf("tbls", args.first()) + configArgs() + args.drop(1)
    val process =
        ProcessBuilder(command)
            .apply { environment()["TBLS_DSN"] = dsn }
            .redirectErrorStream(false)
            .start()
    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()
    val exit = process.waitFor()
    if (stdout.isNotEmpty()) print(stdout)
    if (stderr.isNotEmpty()) System.err.print(stderr)
    check(exit == 0) { "tbls ${args.joinToString(" ")} が異常終了しました (exit=$exit)" }
    check(!(failOnNonEmptyStdout && stdout.isNotBlank())) {
        "dbdoc/ がスキーマと乖離しています。`./gradlew generateDbDoc` で再生成してコミットしてください。"
    }
}

/**
 * tbls へ渡す設定ファイル指定（--config <path>）を返す。 build.gradle.kts がシステムプロパティ tbls.config に .tbls.yml
 * の絶対パスを設定する。 未設定なら空（tbls 既定の自動探索に委ねる）。
 */
private fun configArgs(): List<String> {
    val config = System.getProperty("tbls.config")
    return if (config.isNullOrBlank()) emptyList() else listOf("--config", config)
}
