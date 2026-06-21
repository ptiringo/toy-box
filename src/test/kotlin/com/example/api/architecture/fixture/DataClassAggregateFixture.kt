package com.example.api.architecture.fixture

import org.jmolecules.ddd.annotation.AggregateRoot

/**
 * `aggregatesAreNotDataClasses` ルールが違反を検出することを確認するためのフィクスチャ。
 *
 * `@AggregateRoot` を付与しつつ `data class` で宣言した「違反集約」を意図的に表す（data class は `componentN()` を
 * 生成する）。`ArchitectureTest` 本体は `DoNotIncludeTests` でテストソースを除外するため、本番ルールを汚染しない。
 */
@AggregateRoot data class DataClassAggregateFixture(val name: String)
