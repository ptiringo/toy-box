package com.example.api.controller.archfixture

import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor

/**
 * `dtosDoNotExposeDomainEnums` ルールが違反を検出することを確認するためのフィクスチャ。
 *
 * `controller` 配下に置きつつ、フィールド型にドメイン enum（[CoatColor]）を直接持つ「違反 DTO」を意図的に表す。 `ArchitectureTest` 本体は
 * `DoNotIncludeTests` でテストソースを除外するため、このフィクスチャが本番ルールを汚染することはない。
 */
class DomainEnumFieldDtoFixture(val coatColor: CoatColor)
