# 0090. 不正利用には、乗っ取りの予防と高くつく操作の即時検知で備える

- Status: Accepted
- Date: 2026-11-08
- Deciders: ptiringo

## Context（背景・課題）

[ADR-0089](0089-production-monitoring-uptime-and-deploy-failure.md) でサービス停止の監視から切り離した「想定外の利用に気づく」を検討した（#1017）。

避けたいことは、優先度の順に次の 2 つである。

1. 認証情報が漏れて勝手に使われ、過剰な請求が来る
2. データを勝手に書き換えられる

漏れを心配する認証情報は、GCP と Prisma Postgres の両方である。

### 確認した現状（2026-11-08 時点）

- **GCP の owner**: `admin@ptiringo.tech` と `tfc-service-account`（HCP Terraform が WIF 経由で使う）の 2 つ。`deployer` は `roles/run.developer` だけを持つ。
- **サービスアカウントの鍵**: 6 つのサービスアカウントのどれにも、ユーザー管理の鍵は発行されていない。漏れうるのは鍵ではなく、権限を使える経路である。
- **owner に届く経路**: `admin@ptiringo.tech` のログイン、HCP Terraform のログイン（`toy-box` ワークスペースは owner として apply する）、GitHub の main への push（HCP が VCS 連携で apply する）の 3 つ。
- **Cloud Run の IAM**: 空で、`allUsers` を含まない。invoker 権限のない人は API に到達できない。
- **Identity Platform**: サインアップを禁止していない（`client.permissions` が空）。公開されている Web API キーがあれば誰でもアカウントを作れるが、上の IAM で API には届かない。
- **課金の守り**（[ADR-0074](0074-billing-guardrails-spend-cap-and-budget-alert.md)）: 止められるのは Cloud Run の spend cap だけで、予算アラートは知らせるだけである。課金データは数時間遅れて届く。owner の権限で Compute Engine を有効にして VM や GPU を作られても、止まるものがなく、気づくのも遅れる。
- **監査アラート**（[ADR-0078](0078-audit-via-admin-activity-detect-only.md)）: 人間の principal による変更だけが対象で、サービスアカウントの操作では鳴らない。
- **Prisma**: Free プランは spend limit が $0 に固定されている（ADR-0074 の追補）。service token が漏れても請求にはならず、危険はデータの削除と書き換えに置き換わる。

### 検討して採らなかった案

- **定期レポートを第一の手段にする**（#1017 の起票時の第一候補）: アカウント数やユーザーごとのリクエスト数を週次で見る案。今は Cloud Run の IAM が外部からの利用を止めているので、見ても数字がほとんど動かない。請求の暴走に対しても、週 1 回の配信では間に合わない。
- **課金のアラートを強める**: 金額の閾値を下げても、課金データの遅れは縮まらない。

## Decision（決定）

**乗っ取りは 2 段階認証で予防し、高くつく操作とデータの入口が開く操作を、監査ログから数分で知らせる。** 定期レポートは API を一般に公開するときに検討する。

### 予防: owner に届く経路をすべて 2 段階認証で守る

本 ADR の検討中に、次のとおり揃えた。

- `admin@ptiringo.tech`: 2 段階認証は元から有効（電話番号）。パスキーを追加した
- HCP Terraform: 2 段階認証が無効だったので有効にした（`/account/details` の `two-factor.enabled` で確認）
- GitHub: 有効

### 検知 1: 高くつく操作を、誰が行っても知らせる

Admin Activity ログ（常時記録・無料）に対するログベースのアラートで、API の有効化（`EnableService` / `BatchEnableServices`）と、VM などの高くつくリソースの作成を知らせる。通知は既存のメールの通知チャネル（`infra/modules/audit`）を使う。

ADR-0078 のアラートと違い、**サービスアカウントの操作も対象にする**。乗っ取られた経路がサービスアカウント（HCP の apply など）でも鳴らすためである。正規の apply でも鳴るが、API の有効化は過去 400 日で 11 件（2026-11-08 の実測）しかなく、許容できる。

### 検知 2: データの書き換えの入口が開いたら知らせる

データの書き換えに対する主な守りは、Cloud Run の IAM が空であることである。そこで、Cloud Run とプロジェクトの IAM に `allUsers` / `allAuthenticatedUsers` を含むバインディングが入ったら知らせる。

**呼ばれたことではなく中身で絞る**。`deployer` はデプロイのたびに、空のポリシーで `google.cloud.run.v1.Services.SetIamPolicy` を呼ぶ（`--no-allow-unauthenticated` の副作用。過去 400 日で 200 件以上、2026-11-08 の実測）。メソッド名だけで絞ると、デプロイのたびに鳴る。

### 対象外にするもの

- **定期レポート**: API を一般に公開するときに、見る項目（アカウント数、ユーザーごとのリクエスト数、認証失敗、Prisma の operations）と頻度を決める。
- **Prisma のデータの削除・書き換え**: 気づいても消えたデータは戻らない。検知ではなくバックアップの問題として別に扱う。

実装は後続の Issue で行う。

## Consequences（結果・影響）

- 請求につながる乗っ取りは、主に予防で防ぐ。乗っ取られた場合も、高くつく操作をすれば数分で気づける。課金データを待つより速い。
- 検知 1 は、正規の apply でも鳴る。鳴ったら、HCP の run と突き合わせれば正規かどうかを判断できる。
- 検知 1 が見るのは決めた操作だけで、それ以外の方法で費用を膨らませる操作（BigQuery の大量クエリなど）は拾えない。BigQuery は有効なままである。拾えない分は、ADR-0074 の予算アラートが遅れて知らせる。
- 検知 2 は IAM の変更だけを見る。Cloud Run の IAM が空のまま、アプリの認証を破られた場合は拾えない。
- Identity Platform のサインアップは開いたままである。API を一般に公開するときに、定期レポートと合わせて見直す。
- Firebase が自動で作ったサービスアカウント `firebase-adminsdk-fbsvc` は、プロジェクト全体の `roles/iam.serviceAccountTokenCreator` を持つ。理屈の上では owner のサービスアカウントになりすませるが、鍵がなく、使えるのは Google の内部だけなので、今回は変えていない。

## 追補（2026-11-08）: 検知の実装（#1038）

`infra/modules/audit/main.tf` に、ログベースのアラートを 2 本足した（`costly_operation` / `public_access_granted`）。通知は既存のメールの通知チャネルを使う。

### 決定時の実測の訂正

Decision の「API の有効化は過去 400 日で 11 件」は誤りだった。数えたときの取得上限（200 件）を Cloud Run の `SetIamPolicy` が埋め、途中で打ち切られていた。正しくは 20 件（1 回の操作が 2 件ずつ記録される）で、5 分の通知のまとめを通すと通知は 6 回ほどになる。許容できるという判断は変わらない。

### 決定時に想定していなかった点

- **検知 1 の対象**: API の有効化、Compute Engine の VM 作成（v1 / beta）、App Engine のバージョン作成の 3 つにした。App Engine の API は有効なまま残っていた。GKE や Cloud SQL など、ほかの高くつくサービスの API は無効なので、使うには API の有効化を通る。
- **検知 2 の経路が 1 つ多い**: Cloud Run は IAM を変えなくても、invoker の IAM チェックを外す設定（v1 は annotation `run.googleapis.com/invoker-iam-disabled`、v2 は `invokerIamDisabled`）で公開できる。これはデプロイと同じサービスの更新として記録されるので、`SetIamPolicy` だけを見ていると素通りする。両方の形を条件に入れた。
- **IAM の記録の形**: プロジェクトの `SetIamPolicy` はポリシー全体と差分（`serviceData.policyDelta`）の両方を、Cloud Run のものはポリシー全体だけを記録する。どちらも `request.policy.bindings[].members` に載るので、同じ条件で拾える。
