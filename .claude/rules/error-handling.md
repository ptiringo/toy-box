---
paths:
  - "src/main/kotlin/com/example/api/controller/**/*.kt"
  - "src/main/kotlin/com/example/api/application/**/*.kt"
  - "src/main/kotlin/com/example/api/domain/**/*.kt"
  - "src/test/kotlin/com/example/api/**/*.kt"
---

# エラーハンドリング規約

ドメイン / アプリケーション層の失敗ハンドリングは [`kotlin-result`](https://github.com/michaelbull/kotlin-result) の `Result<V, E>` を用いて型として表現する。例外は「インフラ障害」「プログラミングエラー」など、業務的に想定しないものに限定する。

## 例外 vs `Result<V, E>` の使い分け

| 観点 | 方針 |
|------|------|
| 業務ルール違反（重複登録、状態遷移違反、バリデーション NG 等） | `Result.Err` で表現する |
| インフラ障害（DB 切断 / タイムアウト等） | 例外を throw して `@ControllerAdvice` で処理 |
| プログラミングエラー（NPE、不正な前提条件等） | 例外。`@ControllerAdvice` で 500 |
| Repository の単純な lookup（`findById` 等） | `T?` で可。Result を強制しない |

## エラー型の設計

- ドメイン操作の失敗のしかたが **1 種類** なら単一の `data class` をエラー型として持つ
- 失敗のしかたが **2 種類以上** なら `sealed interface` に昇格し、`when` の網羅性チェックで漏れを防ぐ
- 共通親 `interface`（`DomainError` 等）は **当面導入しない**。Controller 境界で横串のハンドリングが重複し始めた段階で初めて切り出す
- エラーバリアントの `Throwable` 保持は、外部 API / ファイル I/O など **例外起因のバリアントだけ** に持たせる。共通 interface に `cause: Throwable?` を強制しない
- ドメインオブジェクトの不変条件違反（例: 名前のブランク）は、そのドメインオブジェクトの `companion object.create()` ファクトリで `Result<T, ValidationError>` を返して表現する。application 層は受けたエラーを必要に応じて自分のエラー型に wrap する

## Controller 境界での変換

全エラーレスポンスは中央の描画 funnel（`GlobalExceptionHandler` ＝ `ResponseEntityExceptionHandler`）に集約する。業務エラー・フレームワーク標準例外・想定外例外のすべてが同じ funnel を通り、[api-design.md](api-design.md) に従い RFC 9457 形式の `ProblemDetail`（`application/problem+json`）として一貫した形で描画される。

業務エラーの流れ:

1. ドメイン / アプリケーション層は **Result-first を保つ**（例外を投げない）
2. Controller でエラーを `mapError { it.toProblemDetail() }` で `ProblemDetail` にマップする（どのエラー→どの `status` / `errorCode` という**方針はアダプタ層に置く**）
3. `orThrowProblem()`（`controller/ProblemResponses.kt`）で `Err` を `ErrorResponseException` として送出し funnel に委譲、`Ok` なら値を取り出す
4. 成功は `@ResponseStatus` ＋ 戻り値で resource を返す（`ResponseEntity` は使わない）

```kotlin
@ResponseStatus(HttpStatus.CREATED)
@PostMapping("/api/jockeys")
fun register(@RequestBody request: RegisterJockeyRequest): RegisterJockeyResponse {
    val jockey = registerJockey(command).mapError { it.toProblemDetail() }.orThrowProblem()
    return jockey.toResponse()
}
```

> **Controller 境界に限った例外条項**: 「業務エラー＝`Result` / 例外＝インフラ」の原則は維持する。ただし**描画のためだけ**に、Controller 境界で業務エラーを `ErrorResponseException` へ再送出することを許す。ドメイン / アプリケーション層は一切例外を投げず、例外化は `orThrowProblem()` の一手に限定する（中央 funnel で problem 形を一元統制するための割り切り）。

problem 形の統一は `GlobalExceptionHandler.handleExceptionInternal` が担い、規約未適用の `ProblemDetail`（フレームワーク標準例外由来）に `type` / `errorCode` 規約を一律付与する。規約済みかは `errorCode` プロパティ名の有無ではなく **`ConventionalProblemDetail` 型かどうか**で判定する（業務エラーは `problem()` ビルダ経由でこの型になり規約済み、二重付与しない）。プロパティ名一致を単一の弱点にしないための型ベース判定。

## 段階的導入

サンドボックスプロジェクトであることを踏まえ、最初から全面適用はしない。`horseracing` ドメインを参考実装として整備し、`sakamichi` / `tennis` 等の他ドメインへは段階的に展開していく。
