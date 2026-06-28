---
paths:
  - "infra/**"
---

# インフラストラクチャ（Terraform）規約

`infra/` ディレクトリに Terraform 構成を管理する。HCP Terraform（旧 Terraform Cloud）をバックエンドとして使用する。レジストリ／プロバイダ情報の参照は `terraform` MCP、run / variable / workspace の操作は `tfctl` CLI と棲み分ける（[ADR-0034](../../docs/adr/0034-adopt-tfctl-cli.md)）。

## ディレクトリ構成

```
infra/
├── main.tf              # 共有リソースとモジュール呼び出し
├── providers.tf         # Google プロバイダー設定
├── terraform.tf         # Terraform / バックエンド設定
├── variables.tf         # ルート変数（wif_project_number など）
└── modules/
    ├── cicd/            # CI/CD パイプライン基盤
    │   ├── main.tf      # deployer SA、WIF バインディング、AR 書き込み権限
    │   ├── variables.tf
    │   └── outputs.tf
    └── cloudrun/        # Cloud Run デプロイ基盤
        ├── main.tf      # api-runner SA、run.developer 権限、actAs 権限
        ├── variables.tf
        └── outputs.tf
```

## モジュール

- **cicd**: GitHub Actions がイメージをビルド・プッシュするために必要なリソース群（deployer SA、WIF、AR 権限）
- **cloudrun**: Cloud Run へのデプロイと実行に必要なリソース群（api-runner SA、デプロイ権限）

## コマンド

```bash
terraform init              # 初期化
terraform plan             # 差分確認
terraform apply            # 適用
terraform fmt -recursive   # フォーマット
```
