package com.example.api.support

import org.springframework.jdbc.core.simple.JdbcClient

/**
 * studbook コンテキストの全テーブルを FK の依存順（子→親）で空にするテストユーティリティ（ADR-0053）。
 *
 * 集約間 FK の導入後は、自テーブルだけの deleteAll() では他テーブルの残存行（共有コンテナを使い回す 他テストクラスの遺物）が FK
 * 違反で削除を阻むため、契約テストのクリーンアップはこれで行う。 blood_horse の自己参照 FK（sire/dam）は NO ACTION（文末検査）のため単一 DELETE
 * 文で全行消せる。
 */
fun deleteAllStudbookTables(jdbcClient: JdbcClient) {
    listOf(
            "covering_report",
            "breeding_result",
            "breeding_registration",
            "blood_horse",
            "horse_inspection",
        )
        .forEach { table -> jdbcClient.sql("DELETE FROM $table").update() }
}
