package com.example.api.architecture.fixture

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * domain 層の依存許可リスト（#784）が違反を検出することを確認するためのフィクスチャ群。
 *
 * `OnionLayerRulesTest` 本体は `DoNotIncludeTests` でテストソースを除外するため、本番ルールを汚染しない。
 */

/** 旧 denylist の列挙外で、素通りしていたライブラリ（slf4j）に依存するドメイン型。 */
class LoggerDependentDomainFixture {
    fun loggerName(): String = LoggerFactory.getLogger(javaClass).name
}

/** 旧 denylist でも捕まえていたフレームワーク（Spring）に依存するドメイン型。 */
@Component class SpringDependentDomainFixture

/** 許可リストのライブラリ（JDK / Kotlin 標準）だけに依存する、違反しないドメイン型。 */
class PureDomainFixture(val values: List<String>) {
    fun first(): String? = values.firstOrNull()
}
