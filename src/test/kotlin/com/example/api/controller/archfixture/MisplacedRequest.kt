package com.example.api.controller.archfixture

/**
 * `requestDtosResideInRequestSubpackage` ルールが違反を検出することを確認するためのフィクスチャ。
 *
 * `controller` 配下に置きつつ、`request/` サブパッケージの外にある「違反 Request DTO」を意図的に表す。
 * `ControllerContractRulesTest` 本体は `DoNotIncludeTests` でテストソースを除外するため、このフィクスチャが本番ルールを 汚染することはない。
 */
data class MisplacedRequest(val value: String)
