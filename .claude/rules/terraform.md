---
paths:
  - "infra/**"
---

# インフラストラクチャ（Terraform）規約

`infra/` ディレクトリに Terraform 構成を管理する。HCP Terraform（旧 Terraform Cloud）をバックエンドとして使用する。レジストリ／プロバイダ情報の参照は `terraform` MCP、run / variable / workspace の操作は `tfctl` CLI と棲み分ける（[ADR-0034](../../docs/adr/0034-adopt-tfctl-cli.md)）。

## ディレクトリ構成

```
infra/
├── main.tf              # 共有リソース（API 有効化・Artifact Registry）とモジュール呼び出し
├── providers.tf         # Google プロバイダー設定
├── terraform.tf         # Terraform / バックエンド設定
├── variables.tf         # ルート変数（wif_project_number など）
└── modules/
    ├── audit/             # 正規ルート外の変更を検知するアラートと通知チャネル
    ├── billing/           # 月次予算アラート
    ├── cicd/              # CI/CD パイプライン基盤
    ├── cloudrun/          # Cloud Run デプロイ基盤
    ├── identity-platform/ # 認証委譲先
    ├── local-readonly/    # ローカル作業用の read-only SA
    └── prisma-postgres/   # 本番 DB と接続情報
```

各モジュールは `main.tf` / `variables.tf` / `versions.tf`（必要なら `outputs.tf`）で構成する。

## モジュール

- **audit**: 正規ルートを外れた変更操作の検知（log-based alert + メール通知チャネル）。記録そのものは Admin Activity ログが担うので、ここで持つのは「気づく手段」だけ（[ADR-0078](../../docs/adr/0078-audit-via-admin-activity-detect-only.md)）
- **billing**: プロジェクト全体の月次予算アラート。到達しても課金は止まらない検知のみ（[ADR-0074](../../docs/adr/0074-billing-guardrails-spend-cap-and-budget-alert.md)）
- **cicd**: GitHub Actions がイメージをビルド・プッシュするために必要なリソース群（deployer SA、WIF、AR 権限）
- **cloudrun**: Cloud Run へのデプロイと実行に必要なリソース群（api-runner SA、デプロイ権限）
- **identity-platform**: 認証の委譲先（email/password + authorized_domains。[ADR-0064](../../docs/adr/0064-authn-via-identity-platform-authz-in-app.md)）
- **local-readonly**: ローカル作業用の `roles/viewer` のみの SA と impersonation 許可（[ADR-0036](../../docs/adr/0036-gcp-operation-guardrails.md)）
- **prisma-postgres**: 本番 DB（Prisma Postgres）と接続情報を格納する Secret Manager（[ADR-0044](../../docs/adr/0044-adopt-prisma-postgres-for-production-db.md)）

## 変数の供給

機微な値（請求先アカウント ID、Prisma のトークン・プロジェクト名、監査アラートの通知先メール）は公開リポジトリに直書きせず、**HCP workspace の変数**で供給する。`list(string)` 型の変数を HCP UI で登録するときは **HCL フラグをオンにする**（詳細は `.claude/rules/gcp-guardrails.md`）。

## コマンド

```bash
terraform init              # 初期化
terraform plan             # 差分確認
terraform apply            # 適用（ローカルでは実行しない。HCP run に寄せる）
terraform fmt -recursive   # フォーマット
```

CI（`.github/workflows/terraform-check.yml`）が `fmt -check` / `validate` / `tflint` / `trivy config` を PR で回す。
