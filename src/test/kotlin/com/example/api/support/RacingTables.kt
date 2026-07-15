package com.example.api.support

import org.springframework.jdbc.core.simple.JdbcClient

/**
 * racing コンテキストのテーブルを空にするテストユーティリティ。
 *
 * 共有コンテナを使い回す E2E / 契約テストで、他テストが登録したジョッキーが残ると同姓同名の重複
 * （`DuplicateJockey`＝409）を誘発してテストが不安定になるため、各テストの前後で空にする。
 */
fun deleteAllRacingTables(jdbcClient: JdbcClient) {
    jdbcClient.sql("DELETE FROM racing.jockey").update()
}
