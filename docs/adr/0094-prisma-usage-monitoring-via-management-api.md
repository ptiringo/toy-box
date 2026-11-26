# 0094. 本番 DB の operations の使用量を Management API で毎日見て、閾値で Issue を立てる

- Status: Accepted
- Date: 2026-11-26
- Deciders: ptiringo

## Context（背景・課題）

2026-11-24、本番 DB（Prisma Postgres の Free プラン）が月の operations の上限（決定時点で 200,000 回）に達し、月が変わるまで接続ごと拒否されるようになった（#1041）。原因は接続プールの既定値で、#1077 で直した。ただし、上限に達するまで誰も気づかなかったこと自体は残っている。

上限が近いことを事前に知る手段を調べた（2026-11-26 時点のドキュメント）。

- **Free プランには知らせるメールが無い**。75% で届く警告メールは、有料プランで自分で決める支払い上限のためのもの（ https://www.prisma.io/docs/console/more/billing ）
- **Management API に使用量のエンドポイントがある**。`GET /v1/databases/{databaseId}/usage` が、その月の `metrics.operations.used` を返す。実際に呼び、Console の表示と一致することを確かめた（200,478 回）
- **API のトークンは読み取り専用にできない**。OpenAPI 仕様（ https://api.prisma.io/v1/doc ）で確かめると、OAuth のスコープは `workspace:admin` と `offline_access` だけ。サービストークンを作るときに指定できるのは `name` / `projectId` / `expiresAt` だけで、範囲はプロジェクトまでしか絞れない。プロジェクトに絞っても、その中の DB は削除できる（ https://www.prisma.io/docs/console/workspace-access ）
- Prisma 公式の MCP サーバとスキル集にも、使用量を扱う機能は無い（導入の検討はそれぞれ #1083 / #1084）

検討した案:

- **配線せず、月に 1 回 Console を見る**: トークンを置かずに済む。ただし今回のように数日で消費が急に伸びると、月 1 回では間に合わない
- **Viewer の役割を持つ別アカウントのトークンを使う**: 漏れても壊されない。ただし Viewer で使用量 API を呼べるか、Viewer 用のトークンを定期実行で使い続けられるか、Free プランで招待できるかがいずれも未確認で、監視のためのアカウントを 1 つ持ち続けることにもなる
- **本番 DB のプロジェクトに絞り、期限を付けたサービストークンで毎日見る** ← 採用

## Decision（決定）

- **`.github/workflows/prisma-usage.yml` が毎日 1 回（と手動実行で）`scripts/report-prisma-usage.sh` を動かす**。使用量が閾値（決定時点で 50% / 75% / 90%）を超えたら、ラベル `prisma-usage` の Issue を立て、リポジトリの所有者に割り当てる。閾値と上限の値の出所はワークフローの `THRESHOLDS` / `LIMIT`
- **Issue は月ごとに 1 つ**。同じ月のうちは、より高い閾値を超えたときだけ追記する。同じ閾値は 1 度しか知らせない（本文に残した印で判定する）
- **使用量を取得できなければ、監視が止まっていることを Issue に残す**。トークンの期限切れで、知らないうちに監視が止まるのを避けるため
- **トークンは本番 DB のプロジェクトだけに絞り、有効期限を付ける**。GitHub のシークレット `PRISMA_USAGE_TOKEN` に置き、使うのはこのワークフローだけにする。DB の ID もシークレット `PRISMA_DATABASE_ID` に置く（Prisma のプロジェクト名を sensitive として扱っている方針に合わせる）
- Issue を自動では閉じない（ADR-0089 のデプロイ失敗と同じ）

## Consequences（結果・影響）

- 上限に近づいたことを、遅くとも 1 日遅れで知れる。#1077 のあとの消費の見込みは月 1〜2 万回で、50% に届くことは通常ない。届いたら想定外の消費が起きている。
- 毎日の確認は Management API への問い合わせで、DB への問い合わせではない。operations を消費しないはずだが、ドキュメントには書かれていない。導入後に Console の数字で確かめる。

引き受けたこと:

- **トークンが漏れると、本番 DB を削除されうる**。読み取り専用にする手段が無いため。被害はプロジェクトの範囲に留まり、本番のデータは sandbox のものなので許容した。漏れる経路を減らすため、ワークフローは schedule と手動実行でしか動かず、PR からは起動しない。Prisma が読み取り専用のトークンを出したら差し替える。
- **トークンには期限があり、作り直しが要る**。期限が切れると取得失敗の Issue が立つので、それを合図に作り直してシークレットを更新する。
- **Project #4 への追加は Project の UI の設定に頼る**。GITHUB_TOKEN はユーザー所有の Project を操作できない。Free プランでは自動追加を 1 つしか作れないので、ADR-0089 の自動追加の条件を `label:deploy-failure,prisma-usage` に広げて共用する。
- **上限の値はプランに従う**。プランを変えたら `LIMIT` を直す。

関連: [ADR-0089](0089-production-monitoring-uptime-and-deploy-failure.md) / [ADR-0090](0090-misuse-detection-via-costly-operation-alerts.md) / [ADR-0044](0044-adopt-prisma-postgres-for-production-db.md)、#1041、#1017、#1083、#1084
