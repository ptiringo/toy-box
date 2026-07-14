package com.example.api.support

import org.springframework.jdbc.core.simple.JdbcClient

/**
 * iam のテスト用テーブル掃除。`role` / `role_permission` はマイグレーションが投入するマスタなので 消さない（消すと権限展開が空になり、以降のテストが偽陽性で 403
 * になる）。
 */
fun deleteAllIamTables(jdbcClient: JdbcClient) {
    jdbcClient.sql("DELETE FROM iam.account_role").update()
    jdbcClient.sql("DELETE FROM iam.account").update()
}
