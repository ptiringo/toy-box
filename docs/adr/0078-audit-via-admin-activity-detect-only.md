# 0078. GCP の監査は Admin Activity ログに委ね、正規ルート外の変更検知だけを足す

- Status: Accepted
- Date: 2026-08-22
- Deciders: Matsui

## Context（背景・課題）

[ADR-0036](0036-gcp-operation-guardrails.md) は「無確認の変更を止める」予防のガードレールで、「誰が何をしたか」を後から追う監査はスコープ外として分割していた（#473）。さらに #475 でローカルの gcloud が viewer SA の impersonation を既定にしたため、Audit Logs 上の `principalEmail` は人間ではなく SA になり、監査設計の前提が変わっていた。

そこで設計に入る前に、推測ではなく実物のログで前提を確認した（project `ptiringo-toy-box`・2026-08-22 時点）。

- **記録の器はすでにある**: Admin Activity は常時有効・無効化不可で、既定シンクにより `_Required` バケットへ入る。保持は 400 日で `locked: true`（変更不可）、かつ課金対象外。カスタムシンクは無い。
- **呼び出し元は復元できる**: 変更操作の principal は SA だが、`protoPayload.authenticationInfo.serviceAccountDelegationInfo[].principalSubject` に呼び出し元が入る。実物は GitHub Actions が `principal://…/workloadIdentityPools/github/subject/repo:ptiringo/toy-box:environment:production`、HCP Terraform が `…/hcp-tf-pool/subject/organization:ptiringo-tech:project:toy-box-project:workspace:toy-box:run_phase:apply` で、**リポジトリ・environment や workspace・run_phase まで**判別できる。
- **記録されていないものもある**: `auditConfigs` は未設定＝ Data Access ログは既定どおり無効で、`data_access` ログは 0 件。`iamcredentials.googleapis.com` の `GenerateAccessToken` も Admin Activity には 1 件も出ない。つまり「誰が viewer SA を借りたか」は記録されていない。
- **実際の principal 分布**: 直近 30 日の Admin Activity は `deployer@` 192 件 / `tfc-service-account@` 8 件で、**人間は 0 件**。保持期間全体（400 日）まで広げると `admin@ptiringo.tech` 34 件・`ptiringo@gmail.com` 2 件があり、これは impersonation 既定化・IaC 化より前の Console 操作にあたる。

この結果、Issue が挙げていた「Audit Logs の有効化」「保持方針」は**すでに満たされている**か、そもそも変更できない（`_Required` は locked）ことが分かった。足りないのは記録ではなく、**逸脱に気づく手段**だけだった。

検討した代替案:

- **Data Access ログを有効化する**: ローカルの読み取りと impersonation のトークン発行まで可視化でき、「誰が viewer SA を借りたか」が残る。しかし得られるのは read-only 作業の記録で、`_Default`（30 日）へ入り課金対象のログ量が増える。守りたいのは変更操作の追跡なので、費用に見合わないと判断した。
- **ログベースメトリクス＋閾値アラート**: メトリクス経由は集計の遅延が乗る。また「1 件でも出たら異常」であって閾値に意味が無い。
- **検知を入れず記録だけで済ませる**: 記録は追えるが、見に行かなければ気づけない。平時の該当が 0 件（実測）である以上、通知はほぼ鳴らず、鳴ったときの信号価値が高い。

## Decision（決定）

**監査の記録は Admin Activity ログに委ね、追加で足すのは正規ルート外の変更検知だけとする。**

- `infra/modules/audit/` に log-based alert（`google_monitoring_alert_policy` の `condition_matched_log`）とメール通知チャネルを置く。ログベースメトリクスは作らない。
- 検知条件は principal の**否定形**で書く。SA を列挙せず「`\.gserviceaccount\.com$` にマッチしない」とし、SA が増えても追随不要にする（列挙漏れが検知漏れに化けない）。`principalEmail:*` を併記して、principal を持たないエントリ（実測で 400 日に 1 件）が `NOT` を素通りするのを防ぐ。
- 通知先メールは公開リポジトリに直書きせず、HCP workspace の sensitive 変数 `audit_alert_email` で供給する（`billing_account_id` / `prisma_*` と同じ扱い）。
- **Data Access ログは有効化しない**。ログのエクスポート先（BigQuery / GCS sink）も追加せず、保持期間も触らない（`_Required` は locked で不可）。

## Consequences（結果・影響）

- ADR-0036 の「変更は CI/HCP に寄せる」に違反した操作が事後に検知される。GitHub Actions（`deployer@`）と HCP Terraform run（`tfc-service-account@`）は principal が SA なので鳴らず、鳴るのは **owner へ昇格した直接変更**と **Console 操作**に限られる。実測上その該当は直近 30 日で 0 件なので、平時は無音で、鳴ったこと自体が信号になる。
- **読み取りでは鳴らない**。閲覧は Data Access ログ側で、これを無効のままにする決定と表裏である。「誰が viewer SA を借りて何を見たか」は引き続き記録されない。追跡が必要になったら Data Access の有効化を再検討する（費用とのトレードオフを引き受ける判断になる）。
- 検知は事後であって予防ではない。無確認実行を止める役割は引き続き permissions 層（ADR-0036）が担う。
- `audit_alert_email` は HCP workspace にしか無いため、[#794](https://github.com/ptiringo/toy-box/issues/794) が指摘する「コードから読めない変数」が 1 つ増える。
- `monitoring.googleapis.com` は有効だったが Terraform 管理外だったため、この機会に `google_project_service` へ取り込んだ。
- 運用上の結論（どのフィールドで呼び出し元を復元するか、何が鳴るか）は `.claude/rules/gcp-guardrails.md` に置く。関連: [ADR-0036](0036-gcp-operation-guardrails.md) / [ADR-0074](0074-billing-guardrails-spend-cap-and-budget-alert.md)。
