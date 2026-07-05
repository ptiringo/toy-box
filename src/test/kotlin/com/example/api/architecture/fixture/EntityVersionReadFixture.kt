package com.example.api.architecture.fixture

import com.example.api.domain.shared.Entity

/**
 * `readsEntityVersionOfAnotherClass` 述語の空振り防止メタテスト用フィクスチャ群。
 *
 * [EntityVersionReadRuleTest] が「他クラスの version 読み取りは検出する」「集約自身の `copy` に相当する
 * 自己参照は検出しない」の双方を能動的に検証するために使う。`DomainModelingRulesTest` 本体は `DoNotIncludeTests`
 * でテストソースを除外するため、本番ルールを汚染しない。
 */

/** version を override する集約に相当するフィクスチャ。 */
class VersionedAggregateFixture(override val id: String, override val version: Long?) :
    Entity<String>()

/** 他クラス（[VersionedAggregateFixture]）の version を読んで業務分岐する「違反」フィクスチャ。 */
class VersionBranchingFixture {
    /** version の有無で業務判断する（規約違反の再現）。 */
    fun isPersisted(aggregate: VersionedAggregateFixture): Boolean = aggregate.version != null
}

/** 実集約の手書き `copy` と同型の、自クラス version の自己参照を持つ「非違反」フィクスチャ。 */
class SelfCopyAggregateFixture(override val id: String, override val version: Long?) :
    Entity<String>() {
    /** 同一性と version を引き継いだ新インスタンスを返す（`BloodHorse.copy` と同型）。 */
    fun copyLike(): SelfCopyAggregateFixture = SelfCopyAggregateFixture(id, version)
}
