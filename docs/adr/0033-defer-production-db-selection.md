# 0033. 本番 DB プロダクトの選定を遅延し、当面ランタイムは H2 据え置きで進める

- Status: Accepted
- Date: 2026-06-27
- Deciders: Matsui

## Context（背景・課題）

永続化（#338）の本番化を進めるなかで、ランタイム（本番）を実 DB に接続する作業が残っていた。経緯:

- #422 で「永続化の本番 DB を PostgreSQL へ寄せる」のうち、契約テストの PostgreSQL(Testcontainers) 化（PR #432）・JDBC 一本化／InMemory 廃止（[0030](0030-jdbc-only-persistence-retire-inmemory.md) / #423 / #435）は完了済み。残るのは「ランタイムを実 DB へ接続する」インフラ作業で、#451 に切り出し、その上流に **DB 選定 #452** を立てた。
- きっかけは「Cloudflare D1 が安いので検討したい」。コスト動機で、Cloud SQL 前提だった配線先そのものを再評価することにした。

DB 選定 #452 で、コストと「現行スタック（JVM + Spring Data JDBC + JdbcClient + Flyway(PostgreSQL 方言) + Virtual Thread + Cloud Run）への適合・移行コスト」を軸に、8 候補を一次資料で評価した（評価ログは #452 のコメントに保持）:

- **D1 / Turso（SQLite 系）**: 公式 JDBC ドライバ・wire protocol が無く（D1 は管理用途向け HTTP REST のみ＝レート制限でアプリ経路に不適、Turso は JVM 経路が pre-release）、SQLite 方言で Flyway 非対応。採用はアクセス層・マイグレーション・lint 一式の作り直しを伴い、コストの安さで相殺できない。
- **純 Postgres でスタックを壊さない群**: Neon は真の scale-to-zero ＋無料枠が手厚いが **AWS のみ・GCP 非対応**でクロスクラウド往復が不可避。Supabase は無料が 7 日無活動で一時停止（手動復元）。Aiven は GCP 同居版が Startup ~$75/月（無料/$5 はリージョン非選択）。Cloud SQL は GCP ネイティブだが **scale-to-zero 無し** ~$10/月〜の常時課金。
- **CockroachDB Basic**: 「安い＋scale-to-zero＋GCP＋pgwire」を同時に満たす唯一の候補。ただし**恒久的な「方言税」**——トランザクショナル DDL 欠如（Flyway のマイグレーション原子性が無い）、CHECK 制約の検証セマンティクス差、`squawk`/`sqlfluff(postgres)` が構文は通すが Cockroach 固有の危険を見落とすサイレントドリフト、Cockroach 専用 Testcontainers の二重化。本プロジェクトは**不変条件を CHECK 制約で強制**し、SQL lint に Postgres 方言を採用（[0032](0032-sql-lint-squawk-sqlfluff.md)）して投資しているため、この税は割に合わない。
- **YugabyteDB Aeon / Spanner**: 安くも serverless でもない（Yugabyte 無料は dev 専用・本番 ~$735/月、Spanner 無料は 90 日でデータ削除・~$66/月〜で PG ダイアレクト＋Flyway は未解決 issue）。
- **GCP Always-Free e2-micro 自前ホスト**: 「$0 × 完全 Postgres × GCP」が成立する唯一の道だが、app+DB の同一 VM 同居・1GB RAM・運用全部（自分が DBA/SRE・手動バックアップ・HA 無し・単一ゾーン SPOF）を引き受ける。

現状の実態:

- ランタイムの datasource は **H2(PostgreSQL 互換モード)** 据え置き（[0030](0030-jdbc-only-persistence-retire-inmemory.md) の「datasource を H2↔PostgreSQL で差し替える」方針のまま）。
- **実データ・実ユーザはまだ存在しない探索 sandbox**。永続化設計の検証は **Testcontainers(PostgreSQL) 契約テスト（PR #432）** が本番ターゲット DB に対して担保している。

### 検討した代替案

- **今いずれかの候補に確定する**: どの候補も不可逆な対価（クロスクラウド往復／常時課金／方言税／運用負荷）を持つ。実データという駆動要因が無い時点で 1 つに縛ると、選定が陳腐化する、あるいは使っていない DB に常時コストを払い続けるリスクがある。
- **安い非 Postgres（D1 / Cockroach 等）へ寄せる**: SQL lint の Postgres 方言投資（[0032](0032-sql-lint-squawk-sqlfluff.md)）・CHECK による不変条件強制・Testcontainers 契約テストの資産を毀損する。コストの安さでは相殺できない。

## Decision（決定）

**本番 DB プロダクトの選定を現時点では行わず、意図的に遅延する。** 当面は次で進める:

- ランタイムは **H2(PostgreSQL 互換モード) 据え置き**（[0030](0030-jdbc-only-persistence-retire-inmemory.md) の datasource 差し替え方針のまま）。Container Smoke Test / Cloud Run デプロイは外部 DB なしで起動するため無傷。
- 永続化設計の検証は **Testcontainers(PostgreSQL) 契約テスト**で継続する。
- DB 選定 #452・配線 #451 は本方針の下で**保留**（クローズせず、再評価トリガ待ち）。
- 一次資料による 8 候補の評価は **#452 のログ**として保持し、再評価時の出発点とする。

### 再評価トリガ（いずれかを満たした時点で選定を再開する）

1. 実データ・実ユーザなど、**ランタイムに永続化が実需要として現れる**。
2. デプロイ間／インスタンス間でのデータ保持が要件になる（複数インスタンス・再起動越しの状態保持）。
3. コスト・運用条件が変わり、特定候補の対価が許容範囲に入る。

### 再評価時の出発点（#452 の所見の要約）

- 純 Postgres を保ちコスト最優先で GCP 同居が不要 → **Neon**（クロスクラウド許容）。
- GCP ネイティブ／低レイテンシ優先・常時課金許容 → **Cloud SQL**。
- $0 × 完全 Postgres × GCP で運用負荷を引き受けられる → **e2-micro 自前ホスト**（app 同居）。
- **CockroachDB の方言税・D1/Turso の作り直しは原則採らない。**

## Consequences（結果・影響）

- **良くなること**: 実需要が無い段階で不可逆なコスト・制約（クロスクラウド／常時課金／方言税／運用負荷）を負わない。Testcontainers 契約テストで永続化設計は磨き続けられる。選定の鮮度を保ち、再評価時は #452 の一次資料ログから即座に再開できる。
- **引き受けること**: 本番は H2 のままで「実永続化していない」状態が続く（スモーク／デプロイは無傷）。#451 / #452 が open のまま滞留するが、本方針の明示と各 issue へのリンクで「未対応の放置」と「意図的な保留」を区別する。
- CLAUDE.md / `.claude/rules/` への変更は不要（本 ADR は時点の決定の記録）。関連: 永続化方針の [0027](0027-persistence-spring-data-jdbc.md) / [0030](0030-jdbc-only-persistence-retire-inmemory.md)、SQL lint・方言投資の [0032](0032-sql-lint-squawk-sqlfluff.md)。
