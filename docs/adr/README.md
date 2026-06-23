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
| [0006](0006-kover-over-jacoco.md) | カバレッジ計測に Kover を採用し、成熟領域のみゲートする | Accepted |
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
