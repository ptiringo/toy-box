package com.example.api.controller.archfixture

import org.springframework.http.ProblemDetail

/**
 * `problemMappersResideInProblemSubpackage` ルールが違反を検出することを確認するためのフィクスチャ。
 *
 * トップレベル関数を持つこのファイルは facade クラス `MisplacedProblemKt` へコンパイルされる。`controller` 配下だが `problem/`
 * サブパッケージの外に置かれた「違反 Problem マッパー facade」を意図的に表す。`ArchitectureTest` 本体は `DoNotIncludeTests`
 * でテストソースを除外するため、このフィクスチャが本番ルールを汚染することはない。
 */
fun misplacedProblemMapper(): ProblemDetail = ProblemDetail.forStatus(500)
