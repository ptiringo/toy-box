# 0074. 課金ガードレールを Cloud Run spend cap（手設定）と budget alert（Terraform）の二段構えにする

- Status: Accepted
- Date: 2026-08-16
- Deciders: Matsui

## Context（背景・課題）

toy-box は sandbox プロジェクト（`ptiringo-toy-box`）だが、課金は実費で発生する。`.github/workflows/deploy.yml` の Cloud Run 設定（`--min-instances=0 --max-instances=3 --memory=1Gi --cpu=1`）が 1 か月張り付いた場合の理論上限は約 ¥30,000/月にのぼる。一方で `infra/` には課金の見張りが一切無く、暴走（誤ったループ・想定外の高頻度リクエスト・設定ミスによるインスタンス張り付き）を**止める**仕組みは無かった。

なお、**検知**については手設定の budget alert `意図せぬ高額請求検知`（月 ¥500、請求先アカウント全体、閾値 50% / 90% / 100% の実績のみ）が既に存在した。これは本作業の apply 後に `gcloud billing budgets list` で実在を確認して初めて判明したもので、着手時点の想定（「課金の見張りが無い」）は `infra/` に限れば正しかったが、実態としては ¥500 の早期警報が動いていた。

2026年7月、Cloud Billing に Spend Cap Budgets（Public Preview）が入り、Cloud Run が対象サービスに含まれた。到達すると課金対象の利用そのものを自動停止できる（budget alert の「知らせるだけ」を超えて「止める」ことができる）。

[ADR-0036](0036-gcp-operation-guardrails.md) は Claude Code から GCP を触る際に「不可逆・課金を伴う変更は無確認で流さない」ガードレールを掲げている。今回はその延長として、実行時の課金暴走そのものを止める仕組みが必要だった。

## Decision（決定）

課金ガードレールを二段構えにする。

1. **Cloud Run spend cap ¥1,000/月**（Console 手設定）。到達すると課金対象の利用が自動停止し、Cloud Run は 5xx を返す。復旧は手動解除のみ。
2. **プロジェクト全体の budget alert ¥3,000/月**（`infra/modules/billing/`、`google_billing_budget`）。閾値は 50% / 90% / 100%（実績）と 100%（予測）の 4 本。通知先は明示せず、`all_updates_rule` を書かないことで請求先アカウント管理者宛の既定メールに委ねる。通知先を明示しない（＝`all_updates_rule` を書かない）と、請求先アカウントの Billing Account Administrator / User ロール保持者にメールが届く。本プロジェクトの所有者は請求先アカウントの IAM で `roles/billing.admin` を持つため条件を満たす。今回付与した `roles/billing.costsManager` は既定受信者の対象ロールではないので、これだけでは通知を担保しない。

### spend cap を Terraform 外に置く理由

`hashicorp/terraform-provider-google`・`-google-beta` いずれの `google_billing_budget` にも enforcement（利用停止）相当のフィールドが無い。持っているのは `threshold_rules` / `all_updates_rule` / `spend_basis` までで、Spend Cap Budgets 自体が Public Preview のため provider が追随していない。**再検討条件**: provider が spend cap の enforcement に対応したら `infra/` へ寄せる。

### 請求 IAM 付与が手作業になる理由

鶏卵問題。請求先アカウント（通貨 JPY。ID の出所は HCP workspace 変数 `billing_account_id`）の IAM を Terraform で書くには、実行アイデンティティ `tfc-service-account@ptiringo-toy-box.iam.gserviceaccount.com`（WIF 経由）自身に請求 IAM の管理権限が要るが、その付与こそが最初にやりたいことだった。最初の 1 回は組織管理者が手で `roles/billing.costsManager` を付与し、あわせて HCP Terraform workspace 変数 `billing_account_id` を登録した（`billingbudgets.googleapis.com` も本作業まで未有効だった）。

### 金額の根拠

理論上限 ¥30,000/月に対し、Cloud Run の spend cap はその 1/30 の ¥1,000。scale-to-zero とリクエスト課金の平常時利用（数百円未満想定）には十分な余裕がある一方、張り付き事故が起きても数日で自動停止する。全体 budget alert の ¥3,000/月は Cloud Run 以外の課金要素を含めた見張り水準として設定した。

閾値の役割は実績（50% / 90%）と予測（100%）で分かれる。予算 ¥3,000 の 50% は ¥1,500 だが、Cloud Run が支配的なコスト要因である以上、Cloud Run 暴走シナリオでは総額が ¥1,500 に届く前に spend cap（¥1,000）が先に切れてしまい、実績ベースの 50% / 90% はこのシナリオでは発火しない。したがって実績閾値が実質的に見張るのは、spend cap の対象外である Artifact Registry 等の緩やかな増加である。Cloud Run 暴走の予兆は、cap 到達前でも早期に踏み抜きうる `FORECASTED_SPEND` 100% が担う。

### 既存の手設定 budget（¥500）との役割分担

Context に書いた `意図せぬ高額請求検知`（月 ¥500、請求先アカウント全体）は**削除せず残す**。金額が本 ADR の ¥3,000 より 6 倍厳しいため、**早期警報としては常にこちらが先に鳴る**。本 ADR で追加した ¥3,000 の budget が上乗せするのは次の 2 点に限られる。

- **予測ベース（`FORECASTED_SPEND`）の閾値**。既存の ¥500 は実績閾値しか持たないため、「今月このままだと超える」を月末前に掴めない。
- **プロジェクトスコープ**。既存は請求先アカウント全体が対象で、プロジェクト単位の切り分けができない。

つまり ¥500 = 早期警報（手設定・請求先アカウント全体・実績のみ）、¥3,000 = 予測とプロジェクト単位の見張り（Terraform 管理）という分担になる。両者は競合しない。ただし**課金の見張りの出所が 2 つに分かれた**状態であり、既存 budget を Terraform に import して一本化するかは別途判断する。

### spend cap が守らない範囲

spend cap は Cloud Run のみが対象。Artifact Registry（イメージ蓄積による単調増加）・Secret Manager・Identity Platform は対象外で、ここは全体 budget alert が検知する（停止はしない）。Prisma Postgres は Prisma 側の請求であり GCP 課金の管轄外（[ADR-0044](0044-adopt-prisma-postgres-for-production-db.md)）。

## Consequences（結果・影響）

- Cloud Run の課金暴走は spend cap（¥1,000/月）で実際に止まる。全体の課金異常（spend cap 対象外のサービスを含む）は budget alert（¥3,000/月）が検知する。二段構えで「止める」と「知らせる」の役割が分かれる。
- 手設定が 2 つ（Cloud Run spend cap・請求先アカウントへの IAM 付与）リポジトリの外に残る。[ADR-0068](0068-manual-infra-apply-with-notification.md) と同型の逸脱であり、本 ADR が唯一の記録になる。
- spend cap は Public Preview のため、仕様変更や GA 化のタイミングで再確認が要る。provider が enforcement に対応した時点で `infra/` への統合を検討する。
- spend cap 到達時は Cloud Run が 5xx を返す（可用性より課金停止を優先する設計）。本番相当の可用性を求める段階になったら、金額と「止める/知らせるのみ」の方針を見直す。
- 課金の見張りの出所が Terraform（¥3,000）と手設定（¥500）の 2 つに分かれた。役割は分担できているが、片方が Terraform の外にある以上、変更が state に反映されず気づかれない経路が残る。一本化するなら既存 budget を `infra/` へ import する。
- budget の実在確認には `gcloud billing budgets list --billing-account=<id> --billing-project=ptiringo-toy-box` が要る。`--billing-project`（クォータプロジェクト）を省くと、ADC が gcloud 共有プロジェクトを見に行き `SERVICE_DISABLED` で失敗する。
