# 0036. Claude Code からの GCP 操作ガードレールを permissions + 最小権限 SA で構成する

- Status: Accepted
- Date: 2026-06-28
- Deciders: Matsui

## Context（背景・課題）

Claude Code から GCP（project `ptiringo-toy-box`）を扱う際、auto mode（bypassPermissions / acceptEdits）では副作用ある操作（リソース削除・再デプロイ・IAM 変更・課金発生）が無確認で走りうる。sandbox は一部コマンドを制御するが `*.googleapis.com` は到達可能で、`gcloud` / `terraform` / `tfctl` は sandbox 内で動く。正規の変更ルートはアプリ deploy = GitHub Actions（WIF + `deployer@`）、infra apply = HCP Terraform run（tfctl / UI。[ADR-0034](0034-adopt-tfctl-cli.md)）であり、ローカル / エージェントからの直接変更はここに寄せたい。

Claude Code の permission 仕様は本決定の土台となる: `deny` は bypassPermissions でも必ずブロック、`ask` は auto mode でも確認プロンプトを強制、優先順位は deny > ask > allow。

## Decision（決定）

GCP 操作のガードレールを 2 層で構成する。

### 1. permissions の層（deny / ask / allow）

`.claude/settings.json`（リポジトリ共有・コミット対象）の `permissions` に集約する（deny は最上位スコープ集約が最も確実）。

- **deny（auto mode でも遮断・CI/HCP 専用）**: `gcloud * deploy`（run/app/functions 等のデプロイ）、`gcloud * delete` / `gcloud projects delete` / `gcloud storage rm`（不可逆な削除・データ損失）、`terraform apply` / `terraform destroy`（ローカル apply 禁止）。
- **ask（auto mode でも確認強制）**: `gcloud create/update`、`gcloud set-iam-policy` / `add-iam-policy-binding` / `remove-iam-policy-binding`、`gcloud services enable/disable`、`terraform import` / `terraform state rm` / `terraform state mv` / `terraform taint` / `terraform force-unlock`（リモート state を変更しうる）、`tfctl run start`（apply 到達しうるが ADR-0034 の正規 CLI 経路のため遮断せず確認）、`tfctl variable import` / `tfctl create` / `tfctl api`（生 API は変更しうる）。
- **allow（read-only）**: `gcloud describe/list/get-iam-policy`、`terraform plan/validate/show/state list/fmt`、`tfctl run status` / `tfctl get`。

境界の原則は「破壊・デプロイのみ deny、他の変更系は ask（人間確認 or CI 経由）」。

### 2. 資格情報の層（最小権限 viewer SA）

ローカル作業用に `roles/viewer` のみを持つ `local-readonly` SA を Terraform（`infra/modules/local-readonly/`）で定義し、開発者は `roles/iam.serviceAccountTokenCreator` 経由の impersonation で使う。SA / IAM の変更は CI/HCP 経由のみ（ローカルは plan まで）。

### スコープ

課金アラート・quota／実験用 project・環境分離（blast radius）、Cloud Audit Logs、auto-mode 分類器側のブロックはフォローアップ issue とする。

## Consequences（結果・影響）

- auto mode でも副作用ある GCP 操作は deny で遮断 or ask で確認強制され、無確認実行が止まる。
- 新しい変更系コマンドが増えたら deny/ask 語彙へ追記する保守が発生する（メンテ指針は `.claude/rules/gcp-guardrails.md`）。
- Bash マッチは prefix + glob のため、`gcloud` のように動詞が引数末尾に来るコマンドは中間ワイルドカードに依存する。マッチ不良時は動詞別の列挙へ切り替える。
- **env ランナー経由のバイパスは残存リスク**: `timeout` 等のラッパーは matcher 前に剥離されるが、`mise exec -- <cmd>` / `docker exec` 等は剥離されず、`mise exec -- terraform apply` のようにラップすると deny/ask に当たらず無確認実行されうる。本リポジトリは `mise exec -- <tool>` を常用するため現実的リスクがあり、列挙では塞ぎきれない。対策は「変更系を env ランナーでラップしない」運用（rules に明記）と、資格情報層（viewer SA）・CI を backstop とする多層防御。
- 資格情報も最小権限になり、permissions をすり抜けても被害が閲覧に限定される（多層防御）。`gsutil` / `bq` など別バイナリの変更系は現状未使用のため未列挙。使い始める際に deny/ask へ追記する。
- 運用ルールの結論は `.claude/rules/gcp-guardrails.md` と CLAUDE.md に記載（経緯は本 ADR）。関連: [ADR-0001](0001-drop-github-mcp-use-gh-cli.md) / [ADR-0004](0004-secrets-fnox-1password.md) / [ADR-0034](0034-adopt-tfctl-cli.md)。
