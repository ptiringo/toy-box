# Architecture Decision Records

このディレクトリは、設計・運用上の意思決定を記録する ADR（Architecture Decision Record）を管理する。

- 1 決定 1 ファイル。ファイル名は `NNNN-ケバブケースのタイトル.md`（4 桁ゼロ詰めの連番）。
- 本文は日本語。フォーマット・運用は `.claude/skills/adr/SKILL.md` を参照（`/adr` スキルで新規作成できる）。
- CLAUDE.md / `.claude/rules/` には**結論（守るべきルール）**を置き、「なぜそう決めたか」の経緯はこの ADR に残す。

## 一覧

| # | タイトル | Status |
|---|---------|--------|
| [0001](0001-drop-github-mcp-use-gh-cli.md) | GitHub MCP を撤去し gh CLI 直接利用へ切り替え | Accepted |
| [0002](0002-virtual-thread-over-reactive.md) | Virtual Thread を採用し、リアクティブ流派を採らない | Accepted |
| [0003](0003-consolidate-mcp-config-in-repo.md) | MCP サーバー設定をリポジトリ管理ファイルに集約する | Accepted |
| [0004](0004-secrets-fnox-1password.md) | シークレット管理を fnox + 1Password（参照のみ）で行う | Accepted |
| [0005](0005-time-based-uuid-generation.md) | エンティティ識別子をタイムベース UUID（UUIDv7 相当）に統一する | Accepted |
| [0006](0006-kover-over-jacoco.md) | カバレッジ計測に Kover を採用し、成熟領域のみゲートする | Accepted（運用詳細は [0040](0040-coverage-gate-operation-model.md) で更新） |
| [0007](0007-wire-enum-dto-decoupling.md) | HTTP 契約の enum をドメインから分離し Dto enum + マッパーで往復する | Accepted |
| [0008](0008-uniform-resource-representation-response.md) | REST リソース操作の成功レスポンスは一律でリソース表現を返す | Accepted |
| [0009](0009-immutable-aggregates.md) | ドメイン集約はイミュータブルに保ち、状態遷移は新インスタンスで表す | Accepted |
| [0010](0010-confine-aggregate-creation-to-domain-service.md) | 集約をまたぐ前提条件を持つ生成口はドメインサービスに封じ込める | Superseded by [0014](0014-self-validating-factory-over-confinement.md) |
| [0011](0011-priority-via-projects-custom-field.md) | Issue 優先度を GitHub Projects のカスタムフィールドで管理する（ラベル運用を廃止） | Accepted |
| [0012](0012-rest-naming-convention.md) | REST 命名規約を URL=camelCase / ボディ=snake_case に確定する | Accepted |
| [0013](0013-racehorse-registration-as-separate-context.md) | 競走馬登録(JRA)を JAIRS 中心ドメインから別の境界づけられたコンテキストとして分離する | Accepted |
| [0014](0014-self-validating-factory-over-confinement.md) | 集約をまたぐ前提条件は自己検証ファクトリで検証し、生成口の封じ込めを行わない | Accepted |
| [0015](0015-gradle-build-performance-tuning.md) | Gradle ビルド性能チューニング（build cache 採用・並列フォーク見送り）を実測で決める | Accepted |
| [0016](0016-not-covered-as-foaling-outcome-variant.md) | 「種付せず」を FoalingOutcome の区分として表し covering を nullable 化する | Accepted |
| [0017](0017-terraform-quality-gates-tflint-trivy-defer-opa.md) | Terraform 品質ゲートに tflint と Trivy を採用し policy-as-code（OPA）は当面見送る | Accepted |
| [0018](0018-uncovered-via-discriminated-single-create.md) | 「種付せず」の記録入口を covering 有無で判別する単一 Create にする | Accepted |
| [0019](0019-compiler-warnings-as-errors.md) | コンパイラ警告をエラー化して警告ゼロ運用を強制する（allWarningsAsErrors） | Accepted |
| [0020](0020-sealed-origin-and-discriminated-origin-subobject.md) | 出自を sealed Origin に統合し、リソース表現に discriminated 部分オブジェクトを許す | Accepted |
| [0021](0021-parent-not-found-unprocessable-entity.md) | 父母不在（sire/dam 参照先不在）を 422 Unprocessable Entity で確定する | Accepted |
| [0022](0022-domain-service-repository-for-set-invariants.md) | ドメインサービスは集合制約の検証に限りリポジトリポートを受け取ってよい | Accepted |
| [0023](0023-covering-validity-via-stud-certificate.md) | 種付の有効性（種畜証明書の有効区域・有効期間）をファクトリの段階導入前提条件として検証する | Accepted |
| [0024](0024-split-studbook-and-racing-contexts.md) | horseracing を studbook（JAIRS 登録）と racing（JRA 騎手・競走）の 2 コンテキストへ分割する | Accepted |
| [0025](0025-defer-spring-modulith-adoption.md) | Spring Modulith は現時点では採用せず、永続化とコンテキスト間連携の実需要が出た時点で再評価する | Accepted |
| [0026](0026-request-validation-vo-centric-defer-bean-validation.md) | API リクエストバリデーションは VO 中心を維持し Bean Validation を当面採らない | Accepted |
| [0027](0027-persistence-spring-data-jdbc.md) | 永続化アクセスに Spring Data JDBC を主軸採用し PostgreSQL / Flyway / Testcontainers で構成する | Accepted（一部 [0030](0030-jdbc-only-persistence-retire-inmemory.md) で改訂） |
| [0028](0028-controller-adapter-dto-packaging.md) | controller アダプターの DTO を役割別サブパッケージ（request/ + problem/）へ整理する | Accepted |
| [0029](0029-domain-events-via-state-transition-return.md) | イミュータブル集約のドメインイベントは状態遷移の戻り値に同梱して収集する | Accepted |
| [0030](0030-jdbc-only-persistence-retire-inmemory.md) | 永続化実装を JDBC 一本に統一し InMemory リポジトリを廃止する（datasource を H2↔PostgreSQL で差し替える） | Accepted |
| [0031](0031-lightweight-cqrs-read-model.md) | 読み取りを集約非経由の Read Model 経路で実装する（軽量 CQRS / L2・レイヤー先） | Accepted |
| [0032](0032-sql-lint-squawk-sqlfluff.md) | SQL lint に squawk + sqlfluff を採用する | Accepted |
| [0033](0033-defer-production-db-selection.md) | 本番 DB プロダクトの選定を遅延し、当面ランタイムは H2 据え置きで進める | Superseded by [0044](0044-adopt-prisma-postgres-for-production-db.md) |
| [0034](0034-adopt-tfctl-cli.md) | HCP Terraform 操作 CLI として tfctl を採用する | Accepted |
| [0035](0035-mcp-interface-adapter.md) | REST と並ぶ MCP インターフェースアダプタを adapter リングに追加する | Accepted |
| [0036](0036-gcp-operation-guardrails.md) | Claude Code からの GCP 操作ガードレールを permissions + 最小権限 SA で構成する | Accepted |
| [0037](0037-devcontainer-egress-firewall.md) | devcontainer の egress を firewall で default-deny + 許可リストに制限する | Accepted |
| [0038](0038-inspection-subdomain-aggregate.md) | 審査（個体識別・親子判定）を独立集約とし識別子の出所を審査側へ一本化する | Accepted |
| [0039](0039-e2e-api-tests-with-karate.md) | ブラックボックス API E2E テストに Karate を採用する | Superseded by ADR-0056 |
| [0040](0040-coverage-gate-operation-model.md) | カバレッジゲートの運用モデルを excludes 反転 + LINE/BRANCH 2 ボーンド + 手動ラチェットで構成する | Accepted |
| [0041](0041-immutable-data-model-as-modeling-discipline.md) | イミュータブルデータモデルを永続化機構ではなくモデリング規律として部分採用する | Accepted |
| [0042](0042-defer-external-id-policy-keep-raw-uuid.md) | 外部公開 ID は当面 生 UUID 据え置きとし、不透明化・別 ID 体系の導入を遅延する | Accepted |
| [0043](0043-aggregate-to-table-mapping-guidelines.md) | 集約⇔テーブルのマッピング指針（sealed/埋め込み VO のフラット化・CHECK 必須・子テーブル化の境界）を定める | Accepted |
| [0044](0044-adopt-prisma-postgres-for-production-db.md) | 本番 DB プロダクトに Prisma Postgres を採用する（H2 脱却・東京リージョン・標準 JDBC） | Accepted |
| [0045](0045-tbls-db-schema-docs.md) | DB スキーマドキュメントに tbls を採用し CI でドリフトとコメント必須をゲートする | Accepted |
| [0046](0046-adopt-kotlin-lsp-plugin.md) | Claude Code の Kotlin LSP プラグインを採用し kotlin-lsp を mise http バックエンドで配布する | Accepted |
| [0047](0047-aggregate-version-for-optimistic-locking.md) | 楽観ロックの version は集約が保持し save を一本化する | Accepted |
| [0048](0048-per-context-db-schema-namespaces.md) | DB スキーマ名前空間を境界づけられたコンテキスト別に分割する | Accepted |
| [0049](0049-decline-atlas-keep-flyway-toolchain.md) | Atlas（schema-as-code）を現時点では不採用とし Flyway 中心のスキーマツールチェーンを維持する | Accepted |
| [0050](0050-domain-event-publication-after-commit.md) | ドメインイベントは ApplicationEventPublisher で発行し AFTER_COMMIT で購読する | Accepted |
| [0051](0051-transactional-use-case-boundary.md) | トランザクション境界は application 層ユースケースの宣言的 @Transactional で置く | Accepted |
| [0052](0052-validate-constraint-in-separate-migration.md) | VALIDATE CONSTRAINT は後続の別マイグレーションへ分離する | Accepted |
| [0053](0053-foreign-key-backstop-across-aggregates.md) | 集約間の ID 参照に外部キー制約を backstop として張る | Accepted |
| [0054](0054-vacuum-openapi-lint.md) | OpenAPI 仕様の lint に vacuum を採用し CI でドキュメント品質をゲートする | Accepted |
| [0055](0055-patch-coverage-diff-cover-gate.md) | 差分カバレッジ（patch coverage）を diff-cover で 90% ハードゲートする | Accepted |
| [0056](0056-drop-karate-native-resttestclient-e2e.md) | E2E から Karate を撤退し RestTestClient で書き直す | Accepted |
