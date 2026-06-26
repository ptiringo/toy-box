# 0034. HCP Terraform 操作 CLI として tfctl を採用する

- Status: Accepted
- Date: 2026-06-27
- Deciders: Matsui

## Context（背景・課題）

本プロジェクトは `infra/` を Terraform で管理し、バックエンドに **HCP Terraform（旧 Terraform Cloud）** を採用している（`infra/terraform.tf`、org `ptiringo-tech` / workspace `toy-box`）。

現状、ワークスペース・run・変数の状態確認や操作は **HCP Terraform の Web UI** か、Terraform MCP サーバー（`.mcp.json` の `terraform`）経由の**レジストリ参照**に限られ、**run / 変数 / ワークスペースを CLI から直接操作する手段がない**。

[`tfctl`](https://github.com/hashicorp/tfctl-cli) は HashiCorp 公式の HCP Terraform / TFE 管理 CLI（MPL-2.0）。高レベルコマンド（`run start/status`・`variable import`・`workspace`）と API v2 直接アクセス（`tfctl api`）、複数プロファイル（組織・インスタンス切替）に対応する。AI コーディングエージェント向けの skill（harness）も同梱する。本プロジェクトは Claude Code を併用しており、運用との親和性が見込まれたため採否を評価した（#286）。

評価時点の最新は **v0.3.0（2026-06-22 リリース、pre-1.0）**。

## Decision（決定）

tfctl を **HCP Terraform の run / 変数 / ワークスペース操作 CLI として採用する**。運用方針は以下。

### ツール管理: mise の http バックエンドで管理する

`mise.toml` に追加して `mise install` で全員に配布する。ただし通常の registry / aqua / ubi 経路は使えないため **http バックエンド**で `releases.hashicorp.com` を直接指す:

- mise registry の短名 `tfctl` は**別物**（`flux-iac/tofu-controller` の tfctl）と衝突する。
- aqua-registry に `hashicorp/tfctl` は**未登録**（新しすぎる）。
- GitHub Releases に**バイナリ資産が無い**（HashiCorp は `releases.hashicorp.com` で配布）ため `ubi` も使えない。

http バックエンドで `platforms.*` の URL を `{{version}}` テンプレートにし、**バージョン更新は version フィールド 1 箇所**で済むようにする。整合性は**公式 SHA256SUMS の値を各プラットフォームの `checksum` に固定**して担保する（http バックエンドは aqua/ubi と異なり `mise.lock` に per-platform SHA を埋めないため、`mise.toml` 側で明示する）。バージョン更新時は version と 4 つの checksum を SHA256SUMS から更新する。

### 認証: `tfctl auth login`（tfctl 自身の資格情報ストア）を使い、fnox は使わない

tfctl は `tfctl auth login`（ブラウザ経由）でトークンを発行し、自身の資格情報ストア（`~/.config/tfctl/`、フォールバックで Terraform の `~/.terraform.d/credentials.tfrc.json`）に保管する。これは **`gh` CLI が自前の資格情報で GitHub 認証するのと構造的に同じ**であり、本プロジェクトが GitHub 操作を gh CLI の自前認証に委ね fnox を使わない方針（[ADR-0001](0001-drop-github-mcp-use-gh-cli.md)）と整合する。

したがって tfctl のトークンも **fnox + 1Password（[ADR-0004](0004-secrets-fnox-1password.md)）の対象外**とし、`tfctl auth login` の自前ストアに委ねる。fnox は「他プロセスへ env で注入するシークレット」（将来の GCP 認証情報など）に限定する役割を保つ。非対話環境で必要になれば env `TFCTL_TOKEN` で供給できる（その時点で fnox 連携を再検討）。

### MCP との棲み分け

Terraform MCP サーバー（`.mcp.json`）と tfctl は**用途が異なり補完的**。MCP = レジストリ / プロバイダ / モジュールの**ドキュメント参照（読み）**、tfctl = run / workspace / 変数の**操作**。重複しないので両立させる。

### エージェント harness（skill）は本決定では導入しない（follow-up）

`tfctl harness install claude` は `.claude/skills/tfctl/SKILL.md` を生成し、エージェント（Claude Code）の将来挙動を steer する。これは**ツールの採用とは別種の意思決定**（エージェントの behavioral config の追加）であり、本 issue の「ツール採用」の承認範囲に含めない。導入する場合は別途明示的に合意し、生成物をレビューのうえ ADR-0003（skill はリポジトリ管理で共有）に沿って取り込む。本 PR ではコマンドラインツールとしての tfctl 採用までに留める。

## Consequences（結果・影響）

- `mise install` で誰でも tfctl が入る。HCP Terraform の run 状態確認・起動、変数 import を CLI / スクリプトから扱えるようになる。
- **保守コスト**: registry / Dependabot による自動更新は効かない。バージョン更新は `mise.toml` の version + 4 checksum を手動で更新する（手順をコメントに明記済み）。tfctl が aqua-registry に載るか 1.0 に達したら、より標準的な backend へ移行できないか再評価する。
- **pre-1.0 リスク**: v0.x のためコマンド体系の変更余地がある。破壊的変更があればバージョン固定で受け止め、追従是非を都度判断する。
- 認証は各開発者が `tfctl auth login` を一度行う（リポジトリには資格情報を載せない）。Claude Code の sandbox 内からは 1Password / ブラウザに到達できないため、認証や操作系コマンドは sandbox 外（`!` プレフィックス等）で実行する。
- harness skill は未導入。必要になった時点で別 issue / PR で扱う。
- 運用ルールの結論は CLAUDE.md「ツール管理」に記載（経緯は本 ADR）。
