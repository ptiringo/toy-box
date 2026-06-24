package com.example.api.controller.archfixture.request

/**
 * `requestSubpackageContainsOnlyRequests`（逆方向 allowlist）が違反を検出することを確認するためのフィクスチャ。
 *
 * `request/` を含むパッケージに置きつつ、名前が `〜Request` / `〜RequestKt` のいずれでもない「紛れ込み」クラスを
 * 意図的に表す。`ArchitectureTest` 本体は `DoNotIncludeTests` でテストソースを除外するため、本番ルールを汚染しない。
 */
class Stray
