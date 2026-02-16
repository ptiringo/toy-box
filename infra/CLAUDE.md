# CLAUDE.md

このファイルは、このリポジトリ内のコードを扱う際の Claude Code（claude.ai/code）向けガイドラインを示します。

## プロジェクト概要

GCP プロジェクト `ptiringo-toy-box` のインフラストラクチャを Terraform で管理するリポジトリ。
Terraform Cloud（org: `ptiringo-tech`, workspace: `toy-box`）でリモートステート管理。

## 言語・スタイル規約

- コメント・ドキュメント・コミットメッセージは**日本語**で記述
- 変数名・関数名・クラス名は**英語**
- コミットメッセージは **Conventional Commits** 形式（日本語）: `feat: 新機能を追加`, `fix: バグを修正`, `docs: ドキュメントを更新` など
- EditorConfig 準拠必須: LF改行、UTF-8、末尾改行あり、末尾空白削除（*.md は例外）

## 開発コマンド

```bash
# ツールセットアップ
mise install          # Terraform, EditorConfig Checker, Lefthook をインストール
lefthook install      # Git hooks をセットアップ

# Terraform（ローカルチェック用途）
terraform init -backend=false    # ローカル初期化（バックエンドなし、fmt/validate 用）
terraform fmt -check -recursive  # フォーマットチェック
terraform validate               # 構文検証

# Terraform（実運用 / リモートステート利用）
terraform init                   # Terraform Cloud バックエンド有効で初期化
terraform plan                   # 実行計画確認（リモートステート利用）
terraform apply                  # インフラ適用（リモートステート利用）
# Pre-commit hooks 手動実行
lefthook run pre-commit
```

## アーキテクチャ

- `main.tf` - GCPリソース定義（API有効化、Artifact Registry、サービスアカウント）
- `terraform.tf` - Terraform Cloud 設定・プロバイダーバージョン制約
- `providers.tf` - Google プロバイダー設定（リージョン: `asia-northeast1`）
- `locals.tf` - ローカル変数
- `variables.tf` / `outputs.tf` - 入出力定義

管理リソース: GCP API有効化（Artifact Registry, IAM, IAM Credentials）、Docker用 Artifact Registry リポジトリ、デプロイ用サービスアカウント

## CI/CD

PR 作成・更新時に GitHub Actions で以下を自動実行:
- `terraform fmt -check -recursive` + `terraform validate`（terraform-check.yml）
- EditorConfig 準拠チェック（editorconfig-check.yml）

Dependabot が GitHub Actions と Terraform Provider を週次で更新チェック。
