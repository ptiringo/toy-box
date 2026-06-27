# 0035. REST と並ぶ MCP インターフェースアダプタを adapter リングに追加する

- Status: Accepted
- Date: 2026-06-27
- Deciders: Matsui

## Context（背景・課題）

本プロジェクトの adapter リングは **`controller`（REST）** のみで構成されており、HTTP 以外のインターフェースから application 層のユースケースを呼び出す経路がなかった。

[MCP（Model Context Protocol）](https://modelcontextprotocol.io/) は LLM エージェントとツール・データソースをつなぐ標準プロトコルであり、Claude Code 等の AI コーディングアシスタントがアプリケーションのユースケースを直接呼び出せるようにするための整備が求められていた（#287）。

アプリは **Spring MVC（servlet）+ Virtual Thread** で構成されており、WebFlux / リアクティブ流派は採らない方針が確定している（[ADR-0002](0002-virtual-thread-over-reactive.md)）。MCP トランスポートもこのスタックと整合させる必要があった。

同じ application 層のユースケースを REST と MCP の両 IF で公開したい。ただし domain / application 層への侵食は避け、`controller` との対称設計を保ちたい。

## Decision（決定）

adapter リングに **`mcp` パッケージ**を追加し、MCP インターフェースアダプタを実装する。

### トランスポート: WebMVC (Streamable HTTP)

Spring MVC + Virtual Thread スタックに整合するよう、トランスポートは `spring-ai-starter-mcp-server-webmvc`（SSE/Streamable HTTP）を採用する。`-webflux` は採らない（[ADR-0002](0002-virtual-thread-over-reactive.md) と一致）。

### ライブラリ: Spring AI 2.0.0

**Spring AI 2.0.0 GA**（2026-06-12 リリース）を採用する。Spring Boot 4.1 / Spring Framework 7.0 に対応した GA バージョンであり、バージョン整合リスクはない。Spring AI 2.0 は MCP Java SDK 2.0.0（MCP spec 2025-11-25）を同梱し、`@McpTool` アノテーションで Spring Bean を MCP ツールとして公開できる。

### 設計原則: `@McpTool` を application 層に直付けしない

`@McpTool` アノテーションは application のユースケースに直付けせず、`mcp` アダプタ（`JockeyMcpTools` 等）でラップする。`controller` が `Result<V, E> → ResponseEntity` 変換を担うのと対称に、`mcp` アダプタが `Result<V, E> → MCP tool result` 変換を担う（失敗は例外送出し、Spring AI が MCP の `isError` へ写す）。MCP 関心（ツール記述・引数スキーマ・エラー表現）は adapter の責務であり、application 層に漏らさない。

### Walking skeleton: FindJockey 1 本から開始

骨格として `FindJockeyUseCase`（読み取り系・軽量 CQRS L2・副作用なし）を MCP ツール 1 本として公開する。「Spring AI MCP server の配線」「`Result` → tool result 変換」「adapter リングとしての ArchUnit 適合」「slice テスト」を実証し、他ユースケースの公開は follow-up とする。

### 認証: 当面入れない（follow-up）

MCP エンドポイントに認証は当面設定しない（ローカル探索前提）。Spring AI には `spring-ai-community/mcp-security` があり OAuth2 / API-key による保護が可能であるが、本 skeleton では導入しない。本番デプロイ（Cloud Run）は `--no-allow-unauthenticated` により外部到達不可なため、全プロファイルで有効化しても即座のリスクはない。

### ArchUnit への適合

`ArchSupport.kt` に `adapter("mcp", MCP)` を追加する（`controller`=rest / `infrastructure`=persistence と並ぶ adapter として）。`mcp` パッケージはコンテキスト分離の `BoundedContextAssignment` 判定において `controller` / `infrastructure` と同様に扱い、コンテキスト循環チェックの対象外とする。

## Consequences（結果・影響）

- `FindJockey` が MCP ツールとして公開され、AI エージェントが騎手情報を取得できるようになる。
- application / domain 層は無変更で再利用され、オニオンの依存方向は維持される。
- ArchUnit のオニオン/コンテキスト分離ルールに `mcp` が正式な adapter として組み込まれ、`./gradlew test` で規約が強制される。
- MCP エンドポイントは全プロファイルで有効になるが、本番 Cloud Run は `--no-allow-unauthenticated` で外部到達不可。
- **書き込みユースケースの公開と副作用ガード**（AI からの副作用操作の確認・制限）は #463（Claude Code から GCP/副作用を安全に扱う）と連携した follow-up として別 issue/PR で扱う。
- 他ユースケース公開・認証・MCP ツールスキーマのドキュメント化・MCP プロトコル E2E テストも follow-up。
- `mcp` パッケージは Kover の成熟ゲート対象外（`total` レポートには出る）。成熟時点で `variant("mature")` の includes に追加する。
