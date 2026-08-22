---
paths:
  - "infra/**"
  - ".claude/settings.json"
---

# GCP 操作のガードレール

Claude Code から Google Cloud（project `ptiringo-toy-box`）を扱うときの安全な既定。auto mode（bypassPermissions / acceptEdits）でも副作用ある操作が無確認で走らないようにする。決定経緯は [ADR-0036](../../docs/adr/0036-gcp-operation-guardrails.md)。

プロジェクト ID は**秘匿対象にしない**（利用者の ID トークンとログイン画面が既に配っており隠す効果が無い）。逆に請求先アカウント ID のような未開示の識別子は書かず、変数名で参照する（[ADR-0075](../../docs/adr/0075-gcp-project-id-not-a-secret.md)）。

## 2 層のガードレール

役割は**非対称**: **permissions＝強制**（アイデンティティ非依存で無確認変更を止める）、**viewer SA＝安全な既定＋多層防御**（owner は IAM でハード強制できない）。

1. **permissions（`.claude/settings.json`）**: `gcloud` / `terraform` / `tfctl` を動詞で 3 層に分ける。**変更・削除・課金を伴う動詞は deny（完全遮断・CI/HCP 専用）、状態を書き換えうる動詞は ask（auto mode でも確認強制）、read-only の動詞だけ allow**。優先順位は deny > ask > allow で、`deny` は bypassPermissions でも必ずブロックし、`ask` は auto mode でも必ずプロンプトを出す。**どのコマンドがどの層かの唯一の出所は `.claude/settings.json`**（ここには転記しない。列挙は必ず drift する）。
2. **最小権限の資格情報（Terraform `infra/modules/local-readonly/`）**: 読み取り作業の安全な既定として `roles/viewer` のみの `local-readonly` SA を impersonation で使う（唯一の資格情報ではない）。変更はローカル identity を通さず CI/HCP か明示昇格で行う。owner はハード強制できない（強制は permissions が担う）。非 owner には tokenCreator のみ渡せば read-only を強制できる。

## 正規の変更ルート

- アプリ deploy: GitHub Actions（`deploy.yml`、WIF + `deployer@`）。
- infra apply: HCP Terraform run（tfctl / HCP UI。[ADR-0034](../../docs/adr/0034-adopt-tfctl-cli.md)）。ローカルは `terraform plan` まで。
- 変更系を流したいときは ask の確認に従うか、上記の正規ルートに寄せる。

## viewer SA は既定で効く（impersonation）

`mise.toml` の `[env]` が `CLOUDSDK_AUTH_IMPERSONATE_SERVICE_ACCOUNT`（gcloud）と `GOOGLE_IMPERSONATE_SERVICE_ACCOUNT`（terraform の google / google-beta provider）を設定するため、**プロジェクトディレクトリでは何も付けなくても viewer SA で走る**。対話シェル（`mise activate`）と Claude Code セッション（session-start hook の `mise hook-env`）の双方に適用される。read-only を「最小抵抗の既定」にするのが狙いで、フラグを覚えている必要はない。

前提は `local_readonly_impersonators`（HCP workspace の Terraform 変数）に自分の `user:<mail>` が入った状態で apply 済みであること。付与前に env だけ有効化すると impersonation が解決できず、**読み取りを含む全 gcloud が壊れる**。

変数は `list(string)` なので HCP UI で登録するときは **HCL フラグをオンにする**（`["user:foo@example.com"]`）。忘れると文字列として渡り run が型エラーで落ちる。付与対象は**実際に `gcloud config list` の `[core] account` になるアカウント**にすること（複数アカウントを使い分けているなら、切り替え先も入れておかないと切り替えた瞬間に全 gcloud が壊れる）。

効いているかは次で確認する:

```bash
gcloud config list                          # [auth] impersonate_service_account に SA が出る
gcloud projects describe ptiringo-toy-box   # 通れば tokenCreator 付与まで含めて健全
```

impersonation が効いている間は実行のたびに `WARNING: This command is using service account impersonation.` が stderr へ出る（2 行出ることがある）。**正常動作の合図でエラーではない**ので、出力をパースするスクリプトでは stdout だけを見ること。

`gcloud auth login` 等の auth 系は impersonation の影響を受けない（資格情報の取得自体はローカル identity で行う）。

terraform 側の `GOOGLE_IMPERSONATE_SERVICE_ACCOUNT` が効くのは **provider がローカルで認証するときだけ**。`infra/` は HCP Terraform（`cloud` ブロック）なので `plan` / `apply` は HCP 上で HCP の資格情報で走り、この env は渡らない。ローカルの多層防御（将来のローカル実行・別ディレクトリでの provider 利用）として置いてある。`gcloud config list` の `[core] account` も **owner のまま表示される**（impersonation は実行時に被さるだけでアクティブアカウントを置き換えない）ので、これを見て「効いていない」と誤読しないこと。

## owner へ昇格する（例外）

- **変更系は昇格しない**。正規ルート（GitHub Actions / HCP Terraform run）に寄せる。
- viewer では見えない**読み取り**（課金など）に限り、対話シェルで env を外してから叩く:

  ```bash
  unset CLOUDSDK_AUTH_IMPERSONATE_SERVICE_ACCOUNT   # そのシェルの間だけ owner に戻る
  ```

- **`VAR= gcloud ...` の env 代入プレフィックスで昇格しない**。先頭トークンが `gcloud` でなくなり、permissions の deny/ask マッチャに当たらないまま走りうる（下記「変更系を env ランナーでラップしない」と同じ穴）。

## 監査（何が記録され、何が鳴るか）

記録は **Admin Activity ログ**が担う。常時有効・無効化不可で、既定シンクにより `_Required` バケット（保持 400 日・`locked` で変更不可・課金対象外）へ入る。**Data Access ログは有効化していない**ので、読み取りと impersonation のトークン発行（`GenerateAccessToken`）は記録されない。決定経緯と実測値は [ADR-0078](../../docs/adr/0078-audit-via-admin-activity-detect-only.md)。

**呼び出し元は `principalEmail` ではなく delegation 側で見る**。正規ルートの変更は principal が SA になるため、人間や CI を突き止めるには `protoPayload.authenticationInfo.serviceAccountDelegationInfo[].principalSubject` を読む。ここに WIF の subject が入り、GitHub Actions ならリポジトリと environment、HCP Terraform なら workspace と `run_phase` まで分かる。

```bash
gcloud logging read 'log_id("cloudaudit.googleapis.com/activity")' --project=ptiringo-toy-box \
  --limit=10 --freshness=7d \
  --format='value(protoPayload.authenticationInfo.principalEmail,protoPayload.methodName)'
```

**アラートが鳴る条件は「Admin Activity の principal が SA ではない」**（`infra/modules/audit/`）。正規ルート（GitHub Actions の `deployer@` / HCP run の `tfc-service-account@`）はすべて SA なので鳴らない。鳴るのは **owner へ昇格しての直接変更**と **Console 操作**で、平時は無音なので鳴ったこと自体が信号になる。閲覧では鳴らない（Data Access 側のため）。通知先は HCP workspace の sensitive 変数 `audit_alert_email` で供給する。

## メンテナンス

- 新しい変更系コマンド（別ツールや新サブコマンド）を使い始めたら、deny/ask 語彙へ追記する。read-only は allow に足してよい。`gsutil` / `bq` は現状未列挙なので、使い始めたら同様に追記する。
- `gcloud` は動詞が引数末尾に来るため中間ワイルドカード（`gcloud * delete *`）に依存する。マッチ不良が出たら動詞別の列挙へ切り替える。
- **変更系を env ランナーでラップしない**: `mise exec -- <cmd>` / `docker exec` 等はマッチャの前で剥離されないため、`mise exec -- terraform apply` のようにラップすると deny/ask を迂回して無確認実行されうる（`timeout` 等のラッパーは剥離されるので当たる）。変更系は直接呼ぶか正規ルート（CI/HCP）に寄せる。列挙では塞ぎきれないため、viewer SA と CI が backstop。
- sandbox 下では `gcloud auth` / `tfctl auth` / 操作系は 1Password・ブラウザ・認証に到達できないため `!` プレフィックス等で sandbox 外実行する（[ADR-0034](../../docs/adr/0034-adopt-tfctl-cli.md) と整合）。
