package com.example.api.architecture.fixture

import com.example.api.domain.shared.Entity

/**
 * jMolecules アノテーションの付け忘れを検出するルール（#737）が違反を検出することを確認するためのフィクスチャ群。
 *
 * いずれも役割アノテーションを意図的に付けていない。`DomainModelingRulesTest` 本体は `DoNotIncludeTests` でテストソースを
 * 除外するため、本番ルールを汚染しない。
 */

/** [Entity] を継承しているのに `@AggregateRoot` / `@Entity` を付け忘れた集約。 */
class UnannotatedAggregateFixture(override val id: String) : Entity<String>()

/** `@ValueObject` を付け忘れた inline value class。 */
@JvmInline value class UnannotatedValueClassFixture(val value: String)

/** jMolecules の `@Repository` を付け忘れた永続化ポート。 */
interface UnannotatedFixtureRepository {
    fun findById(id: String): UnannotatedAggregateFixture?
}
