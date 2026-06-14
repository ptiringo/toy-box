# 0002. Virtual Thread を採用し、リアクティブ流派を採らない

- Status: Accepted
- Date: 2026-06-14
- Deciders: ptiringo

## Context（背景・課題）

高い並行性を確保しつつ、ブロッキング IO（JDBC 等）を素直に書きたい。Spring での選択肢は大きく 2 つ。

- **リアクティブ流派**（WebFlux / Reactor / coroutine）: ノンブロッキングで高スケールだが、`suspend` /
  `Mono` / `Flux` が伝播してコードが複雑になり、スタックトレース・デバッグが難しい。同期的な JDBC など
  ブロッキング IO とは相性が悪く、専用のリアクティブドライバや境界の工夫が要る。
- **Virtual Thread 流派**（JDK 21）: リクエスト処理スレッドを仮想スレッド上で走らせれば、ブロッキング IO を
  呼んでも OS スレッドを占有しない。同期コードのまま高並行を得られる。

本プロジェクトは永続化に同期 JDBC 等を想定する探索的 sandbox であり、リアクティブの記述コストに見合う動機が薄い。

## Decision（決定）

JDK 21 の **Virtual Thread を有効化**（`spring.threads.virtual.enabled=true`）し、**Spring MVC の標準的な
`@RestController` パターン + 同期コード**で書く。`suspend` / `Mono` / `Flux` は原則導入しない。

## Consequences（結果・影響）

- ブロッキング IO を素直に書きながら、リクエスト処理スレッドが OS スレッドを占有しない。
- 学習コスト・デバッグコストが低く、標準的な Spring MVC の書き方が通用する。
- テストも sync で書ける（統合テストは `RestTestClient` を使用）。
- 反面、バックプレッシャーやストリーム合成などリアクティブ特有の機能は得られない。必要になった箇所だけ
  局所的に検討する。
- この方針はコーディング規約（CLAUDE.md「構成パターン」）に反映済み。
