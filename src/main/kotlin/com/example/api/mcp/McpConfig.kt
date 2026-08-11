package com.example.api.mcp

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * MCP アダプタの設定バインドを `local` プロファイルでのみ有効にする（#712）。
 *
 * `ApiApplication` に `@ConfigurationPropertiesScan` を足すとアプリ全体のスキャン方針を変えることになるため、 ここで
 * [McpProperties] だけを局所的に有効化する（本プロジェクト初の `@ConfigurationProperties` 利用）。
 */
@Profile("local")
@Configuration
@EnableConfigurationProperties(McpProperties::class)
class McpConfig
