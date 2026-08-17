package com.example.api

import com.example.api.support.EmulatorJwtDecoderConfiguration
import org.springframework.boot.fromApplication
import org.springframework.boot.with

/**
 * ブラウザ E2E（#725）のためのアプリ起動口。`./gradlew bootTestRun` から使う。
 *
 * `bootTestRun` は test runtime classpath でアプリを起動するため、`src/test` にある
 * [EmulatorJwtDecoderConfiguration] を差し込める。これにより **本番成果物（`src/main`）を一切変更せずに** Firebase Auth
 * Emulator の未署名トークンを受け入れる構成を作れる。
 *
 * DataSource は自動配線されない。`spring-boot-docker-compose` は `developmentOnly` 依存であり test classpath
 * には載らないため（#451）、`bootTestRun`（test runtime classpath で起動する）では `compose.yaml` の自動検出が効かない。起動前に
 * `docker compose up -d` で PostgreSQL を手動起動し、 `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD`
 * を環境変数で供給する（本番 Cloud Run と同じ経路。 `application.yml` 参照）。`local` プロファイルは使わない（MCP アダプタを開いてしまい、
 * `MCP_SUBJECT_ID` の供給が要るため）。
 */
fun main(args: Array<String>) {
    fromApplication<ApiApplication>().with(EmulatorJwtDecoderConfiguration::class).run(*args)
}
