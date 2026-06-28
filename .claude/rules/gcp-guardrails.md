# GCP 操作のガードレール

Claude Code から Google Cloud（project `ptiringo-toy-box`）を扱うときの安全な既定。auto mode（bypassPermissions / acceptEdits）でも副作用ある操作が無確認で走らないようにする。決定経緯は [ADR-0036](../../docs/adr/0036-gcp-operation-guardrails.md)。

## 2 層のガードレール

役割は**非対称**: **permissions＝強制**（アイデンティティ非依存で無確認変更を止める）、**viewer SA＝安全な既定＋多層防御**（owner は IAM でハード強制できない）。

1. **permissions（`.claude/settings.json`）**: `gcloud` / `terraform` / `tfctl` を動詞で層分けする。
   - **deny（完全遮断・CI/HCP 専用）**: `gcloud * deploy`（run/app/functions 等）/ `gcloud * delete` / `gcloud projects delete` / `gcloud storage rm` / `terraform apply` / `terraform destroy`。
   - **ask（auto mode でも確認強制）**: `gcloud create/update` / IAM 変更（set-iam-policy・add/remove-iam-policy-binding）/ `gcloud services enable/disable` / `terraform import`・`terraform state rm/mv`・`terraform taint`・`terraform force-unlock`（リモート state を変更しうる）/ `tfctl run start` / `tfctl variable import` / `tfctl create` / `tfctl api`。
   - **allow（read-only）**: `gcloud describe/list/get-iam-policy` / `terraform plan/validate/show/state list/fmt` / `tfctl run status` / `tfctl get`。
   - 優先順位は deny > ask > allow。`deny` は bypassPermissions でも必ずブロック、`ask` は auto mode でも必ずプロンプトを出す。
2. **最小権限の資格情報（Terraform `infra/modules/local-readonly/`）**: 読み取り作業の安全な既定として `roles/viewer` のみの `local-readonly` SA を impersonation で使う（唯一の資格情報ではない）。変更はローカル identity を通さず CI/HCP か明示昇格で行う。owner はハード強制できない（強制は permissions が担う）。非 owner には tokenCreator のみ渡せば read-only を強制できる。

## 正規の変更ルート

- アプリ deploy: GitHub Actions（`deploy.yml`、WIF + `deployer@`）。
- infra apply: HCP Terraform run（tfctl / HCP UI。[ADR-0034](../../docs/adr/0034-adopt-tfctl-cli.md)）。ローカルは `terraform plan` まで。
- 変更系を流したいときは ask の確認に従うか、上記の正規ルートに寄せる。

## viewer SA を使う（impersonation）

`local_readonly_impersonators` に自分の `user:<mail>` を追加して apply（CI/HCP 経由）したうえで:

```bash
gcloud <read-only command> --impersonate-service-account=local-readonly@ptiringo-toy-box.iam.gserviceaccount.com
```

## メンテナンス

- 新しい変更系コマンド（別ツールや新サブコマンド）を使い始めたら、deny/ask 語彙へ追記する。read-only は allow に足してよい。`gsutil` / `bq` は現状未列挙なので、使い始めたら同様に追記する。
- `gcloud` は動詞が引数末尾に来るため中間ワイルドカード（`gcloud * delete *`）に依存する。マッチ不良が出たら動詞別の列挙へ切り替える。
- **変更系を env ランナーでラップしない**: `mise exec -- <cmd>` / `docker exec` 等はマッチャの前で剥離されないため、`mise exec -- terraform apply` のようにラップすると deny/ask を迂回して無確認実行されうる（`timeout` 等のラッパーは剥離されるので当たる）。変更系は直接呼ぶか正規ルート（CI/HCP）に寄せる。列挙では塞ぎきれないため、viewer SA と CI が backstop。
- sandbox 下では `gcloud auth` / `tfctl auth` / 操作系は 1Password・ブラウザ・認証に到達できないため `!` プレフィックス等で sandbox 外実行する（[ADR-0034](../../docs/adr/0034-adopt-tfctl-cli.md) と整合）。
