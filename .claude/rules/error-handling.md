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

ドメイン / アプリケーション層に Spring 依存を持ち込まないため、`Result` から `ResponseEntity` への変換は Controller 境界で `mapBoth` を用いて行う。レスポンスは [api-design.md](api-design.md) に従い RFC 9457 形式の `ProblemDetail` で返す。

```kotlin
@PostMapping("/api/jockeys")
fun register(@RequestBody request: RegisterJockeyRequest): ResponseEntity<Any> =
    registerJockey(command).mapBoth(
        success = { jockey ->
            ResponseEntity.status(HttpStatus.CREATED).body<Any>(jockey.toResponse())
        },
        failure = { error ->
            val problem = error.toProblemDetail()
            ResponseEntity.status(problem.status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body<Any>(problem)
        },
    )
```

## 段階的導入

サンドボックスプロジェクトであることを踏まえ、最初から全面適用はしない。`horseracing` ドメインを参考実装として整備し、`sakamichi` / `tennis` 等の他ドメインへは段階的に展開していく。
