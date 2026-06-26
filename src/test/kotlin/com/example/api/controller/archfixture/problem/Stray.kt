package com.example.api.controller.archfixture.problem

/**
 * `problemSubpackageContainsOnlyProblemMappers`（逆方向 allowlist）が違反を検出することを確認するためのフィクスチャ。
 *
 * `problem/` を含むパッケージに置きつつ、名前が `〜ProblemKt` でない「紛れ込み」クラスを意図的に表す。 `ControllerContractRulesTest` 本体は
 * `DoNotIncludeTests` でテストソースを除外するため、本番ルールを汚染しない。
 */
class Stray
