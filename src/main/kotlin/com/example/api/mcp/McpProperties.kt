package com.example.api.mcp

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * MCP アダプタの設定（`local` プロファイル限定。#712 / ADR-0035）。
 *
 * MCP には HTTP のリクエストも JWT も無いため、REST が JWT の `sub` から解決している「誰が操作しているか」を ここで与える。値は
 * `application-local.yml` が `MCP_SUBJECT_ID` 環境変数から受け取る（個人の subject を リポジトリに書かないため）。
 *
 * 既定は空文字。未設定のまま MCP ツールを呼ぶと [McpActorFactory] が設定ミスとして落とす（fail-loud）。
 *
 * @property subjectId IdP（Identity Platform）が発行する ID トークンの `sub`
 */
@ConfigurationProperties(prefix = "toy-box.mcp")
data class McpProperties(val subjectId: String = "")
