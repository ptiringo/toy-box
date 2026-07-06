# 0054. OpenAPI 仕様の lint に vacuum を採用し CI でドキュメント品質をゲートする

- Status: Accepted
- Date: 2026-07-06
- Deciders: Matsui

## Context（背景・課題）

Issue #327。OpenAPI ドキュメントは springdoc-openapi によるランタイム生成（`/v3/api-docs`）で、
仕様の検証は `OpenApiTest`（@SpringBootTest）のスモーク（取得可否・バージョン・タイトル）に留まっていた。
コントローラーには `@Operation` / `@ApiResponse` を付与する方針（CLAUDE.md「OpenAPI/Swagger」）だが、
付け忘れ・記述の薄さを検出する仕組みがなく、仕様そのものの品質（記述漏れ・命名の一貫性）を
機械的にゲートしたい。

### 検討した要素

- **linter の選定**: Spectral（Node、デファクト、JS/TS カスタム関数）/ vacuum（Go 単一バイナリ、
  Spectral ルールセット完全互換、高速）/ Redocly CLI（Node、lint + bundle + docs preview 同梱）を比較した。
  - ルールセットはいずれも実質 Spectral 形式の YAML で、`extends` + 個別ルール追加の構成も同じ。
    vacuum は Spectral ルールセットを完全互換で読めるため、**採用後の乗り換えコストが低い**（ルール資産は持ち越し可能）。
  - ツールバージョンの単一の出所を mise.toml とする方針（CLAUDE.md「ツール管理」）に対し、
    vacuum は aqua backend の単一バイナリで最も素直に管理できる（tbls / shellcheck / trivy と同じ供給経路）。
    Spectral も aqua 管理可能だが Node ベース、Redocly は npm backend が要る。
  - 固有ルールの表現力: 初期スコープ（summary 必須化・operationId 命名）は YAML の組み込み関数
    （truthy / pattern）で書ける。JS/TS カスタム関数が要る凝ったルール（例: error レスポンスに
    ProblemDetail スキーマ参照を強制）は現時点で不要（YAGNI）。
  - OpenAPI 3.1 対応（springdoc の生成は 3.1.0）は 3 候補とも満たす。2026-07 時点で 3 候補とも活発にメンテされている。
- **仕様ファイルの生成方法**: alias 済みの springdoc-openapi-gradle-plugin（`generateOpenApiDocs` が
  forked bootRun でアプリを起動し `/v3/api-docs` を書き出す）を使う。datasource はローカル bootRun と
  同じく spring-boot-docker-compose が compose.yaml の PostgreSQL を自動供給する。
  代替として dbdoc（tbls）型の「専用ソースセット + Testcontainers」自前ジェネレータも検討した。
  前例と一貫するがコード量が増えるため、まずプラグインを使い、CI で不安定な場合のフォールバックとする
  （lint 側は生成方法に依存しないため差し替え可能）。
- **仕様ファイルの扱い**: リポジトリにコミットして dbdoc 型の鮮度検査（diff）を掛ける案もあるが、
  生成・コミットの手間が毎回かかる。build 成果物のみ（コミットしない）とし、API 差分レビューは
  コード差分で代替する。
- **ゲートの場所**: 生成にアプリ起動（Docker + PostgreSQL）が要るため、check / pre-commit / pre-push には
  載せない（checkDbDoc / e2eTest と同じ役割分担）。関心ごとに独立ワークフローを並べる既存の流儀に従い、
  api-tests.yml への相乗りではなく独立ワークフローとする。

## Decision（決定）

### ツール採用

**vacuum を採用し、springdoc が生成する OpenAPI 仕様を CI で lint する**（mise 管理）。

- `./gradlew generateOpenApiDocs`: forked bootRun でアプリを起動し `build/openapi.json` を書き出す
  （build 成果物。コミットしない）。
- `./gradlew lintOpenApiDocs`: 生成に依存し、vacuum（`mise which` で解決）で lint する。
  失敗閾値は `--fail-severity warn`。

### ルールセット

**`config/vacuum/ruleset.yaml`**（detekt の config/detekt/ と対称）に置き、vacuum 同梱の
**recommended をベース**に固有ルールを少数追加する（operation の summary 必須化・operationId の
camelCase 命名）。意図的に無視する指摘（/actuator/** 等のコードで直せないもの）は
`config/vacuum/ignore.yaml` で明示する。

### 実行タイミング

**CI の独立ワークフロー `openapi-lint.yml` のみ**でゲートする（PR / main push、paths フィルタ付き）。
check / lefthook には載せない。既存の `OpenApiTest` は配線スモークとして残し、仕様の品質ゲートは
vacuum 側が担う役割分担とする。

## Consequences（結果・影響）

- `@Operation` の summary 付け忘れ等が CI で機械的に検出される。導入時に既存指摘は全て解消済みのため、
  以降はドリフト検出として機能する。
- ルール資産は Spectral 互換形式なので、JS/TS カスタム関数が必要になったら Spectral へ低コストで
  乗り換えられる（ProblemDetail スキーマ参照の強制などの高度なルールは後続 Issue）。
- ローカルで `lintOpenApiDocs` を実行するには Docker が必要（CI 専用ゲートのためローカル実行は任意）。
- 開発用アプリと compose サービスを起動したまま `generateOpenApiDocs` を実行すると compose サービスを
  共有し、生成完了時に停止されうる（既知の挙動。ポートは 8090 に分離済みでアプリ同士は衝突しない）。
