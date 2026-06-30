# 0044. 本番 DB プロダクトに Prisma Postgres を採用する

- Status: Accepted
- Date: 2026-07-01
- Deciders: Matsui

## Context（背景・課題）

[0033](0033-defer-production-db-selection.md) で本番 DB プロダクトの選定を意図的に遅延し、ランタイムは H2(PostgreSQL 互換モード) 据え置き、永続化設計は Testcontainers(PostgreSQL) 契約テストで検証する体制を敷いた。再評価トリガの 1 つは「**ランタイムに永続化が実需要として現れる**（デプロイ間／インスタンス間のデータ保持）」だった。

今回「H2 脱却」を進める文脈（#451）でこのトリガに該当したため、[0033](0033-defer-production-db-selection.md) が保持した #452 の評価ログと再評価出発点を起点に選定を再開した。

評価軸は [0033](0033-defer-production-db-selection.md) / #452 と同じ:

- **スタック適合（最重要）**: 本物の PostgreSQL か（Flyway のトランザクショナル DDL、CHECK 制約による不変条件強制、`squawk` / `sqlfluff(postgres)` の方言投資 [0032](0032-sql-lint-squawk-sqlfluff.md) が毀損されないか）。標準 wire protocol / JDBC で繋がるか（[0027](0027-persistence-spring-data-jdbc.md) の JdbcClient + Spring Data JDBC を作り直さずに済むか）。
- **レイテンシ**: ランタイムは GCP Cloud Run（`asia-northeast1` 東京）。DB との往復が同一リージョン近傍か、クロスクラウド/クロスリージョンか。
- **コスト**: 実データ・実ユーザのいない探索 sandbox。idle 中心で、できれば $0・scale-to-zero。
- **GCP コヒーレンス**: GCP ネイティブ（IAM 認証・監査・単一 Terraform provider）か、外部ベンダー依存か。
- **運用負荷**: フルマネージドか、自前運用（DBA/SRE・バックアップ・HA）か。

### 再評価で分かったこと

- **Neon（#452 で「純 Postgres・コスト最優先・GCP 同居不要」の本命だった候補）を再確認したところ、原典で GCP 非対応・AWS のみ（Azure は新規停止）で、かつ東京リージョンが無い**（アジア太平洋は AWS シンガポール / シドニーのみ）。Cloud Run（東京）から使うと毎クエリが「東京 → シンガポール」かつ別クラウドの往復（RTT ≈ 70–90ms）になる。ブロッキング JDBC で 1 リクエストに複数クエリが走ると加算され、恒久的なレイテンシ税になる。
- **Cloud SQL** は GCP ネイティブ・同一リージョン数 ms だが scale-to-zero が無く、停止しても IP＋storage 課金が残る（最小でも月 ~$10〜30 の idle 課金）。
- #452 リストに無かった新候補 **Prisma Postgres** を一次資料で評価したところ、本プロジェクトの評価軸に対して以下を満たす:
  - **本物の PostgreSQL（v17 ベース）**。標準 SQL / wire protocol、pgvector 等の拡張、`pg_dump` 取り込み可。→ CockroachDB のような方言税が無く、Flyway・CHECK・SQL lint の投資がそのまま生きる。
  - **標準 TCP（pgwire）接続**: `postgres://USER:PASSWORD@db.prisma.io:5432/?sslmode=require`（direct connection）。JDBC ドライバ・Flyway から素直に繋がる（D1/Turso のような作り直しが不要）。
  - **東京リージョン（`ap-northeast-1`）あり**。Cloud Run（GCP 東京）と同一都市のため、別プロバイダでも Neon のシンガポール往復のような距離税を負わない。
  - **scale-to-zero**: Unikraft Cloud の microVM ＋メモリスナップショット方式で「ミリ秒 scale-to-zero・cold start なし」を主張（Neon の 300–500ms 復帰より良い設計）。
  - **無料枠 $0**（約 10 万 operations/月・数百 MB〜・複数 DB）。idle 中心の sandbox なら実質 $0。従量は **operations（クエリ）単位**。
  - **Terraform provider あり**（`prisma/terraform-provider-prisma-postgres`）。IaC 規律と整合。

### 検討した代替案

- **Neon**: 純 Postgres・$0・scale-to-zero だが GCP 非対応・東京リージョン無しで、東京 Cloud Run からはクロスクラウド ~80ms が恒久化する。レイテンシ税が割に合わず却下。
- **Cloud SQL**: GCP ネイティブ・低レイテンシ・IAM 認証・監査と最も一貫するが、scale-to-zero 無しの idle 課金（月 ~$10〜30）。実需要の薄い sandbox には常時課金が過剰で見送り（将来 GCP 一貫性や IAM 認証が要件化したら再評価）。
- **e2-micro 自前ホスト**: $0×完全 Postgres×GCP 同一リージョンだが、単一 VM・HA 無し・手動バックアップ・自分が DBA/SRE という運用負荷を恒久的に引き受ける。マネージドで同等のコスト/レイテンシが得られる Prisma Postgres を優先。
- **CockroachDB / D1 / Turso**: [0033](0033-defer-production-db-selection.md) のとおり方言税・スタック作り直しで却下済み（本物の PG である Prisma Postgres はこの問題を持たない）。

## Decision（決定）

本番 DB プロダクトに **Prisma Postgres** を採用し、ランタイムを H2 から Prisma Postgres へ切り替える（H2 脱却。配線は #451）。[0033](0033-defer-production-db-selection.md) の「選定を遅延する」決定を本 ADR で **supersede** する。

- 接続は **direct TCP（標準 pgwire）＋ `sslmode=require`** を用い、[0027](0027-persistence-spring-data-jdbc.md) の Spring Data JDBC / JdbcClient / Flyway をそのまま使う。Prisma ORM・Accelerate(HTTP) 経路には依存しない。
- リージョンは **東京（`ap-northeast-1`）** を選び、Cloud Run（`asia-northeast1`）と同一都市に置く。
- 接続情報は **Secret Manager** に保管し、Cloud Run へ env（`SPRING_DATASOURCE_*`）として注入する（[0030](0030-jdbc-only-persistence-retire-inmemory.md) の「datasource 差し替え（プロファイル切替なし）」方針のまま）。
- 永続化設計の検証は引き続き **Testcontainers(PostgreSQL) 契約テスト**で行う（本物の PG v17 なので本番ターゲットとの差は小さい）。

### 配線（#451）での前提条件・検証事項

採用は確定だが、本番投入（#451）で次を確認・成立させてから切り替える:

1. **direct TCP の GA 状況**: Prisma は元々 Accelerate(HTTP) 主体で direct TCP は後発のため、GA であること・Hikari コネクションプールと素直に動くことを確認する。
2. **実レイテンシ実測**: `db.prisma.io` 単一エンドポイント＋ルーティング層（同社「Life of a Prisma Postgres Query」）の構成のため、GCP 東京 Cloud Run → Prisma Postgres `ap-northeast-1` の実 RTT を測り、同一都市相当の低レイテンシが出ることを確認する。
3. **operations 課金の把握**: 従量が「operations 数」単位のため、無料枠（約 10 万/月）の消費見込みと超過時挙動を把握する。
4. Flyway マイグレーション（V1–V5）が Prisma Postgres 実体で素通りすること（本物の PG v17 のため想定問題なし）。

## Consequences（結果・影響）

- **得られるもの**: H2 脱却＝耐久・共有される実永続化を本番に持てる。本物の PostgreSQL v17・標準 JDBC・東京リージョン・scale-to-zero・無料枠・Terraform provider により、[0027](0027-persistence-spring-data-jdbc.md) / [0032](0032-sql-lint-squawk-sqlfluff.md) のスタック・SQL lint・CHECK 不変条件の投資を一切作り直さずに本番化できる。Neon を沈めたクロスクラウド・レイテンシ問題を東京リージョンで回避し、Cloud SQL の idle 課金も負わない。
- **引き受けるもの**:
  - **GCP 非ネイティブ**（外部ベンダー・バレメタル基盤）。IAM データベース認証・Cloud Audit Logs での DB 監査・単一 Terraform provider という GCP 一貫性は得られず、接続情報は接続文字列として Secret Manager に保持する。
  - **従量が operations 単位**でコスト予測モデルが compute 時間課金とは異なる。
  - 比較的新しい製品＋Unikraft 依存の成熟度・継続性リスク。
  - `db.prisma.io` 経由のルーティングによる実レイテンシは**実測で確認するまで残る不確実性**（上記前提条件 2）。
- **積み残しの解禁**: 本番 PostgreSQL 実体が定まることで、[0043](0043-aggregate-to-table-mapping-guidelines.md) で見送った `breeding_registration.retirement` の CHECK を `NOT VALID` ＋ `VALIDATE CONSTRAINT` で安全に遡及する道が開ける。ただし H2 をローカル/CI で残す限り両対応制約は続くため、その整理（全面脱却の度合い）は #451 で判断する。
- **関連**: 永続化方針 [0027](0027-persistence-spring-data-jdbc.md) / [0030](0030-jdbc-only-persistence-retire-inmemory.md)、SQL lint・方言投資 [0032](0032-sql-lint-squawk-sqlfluff.md)、マッピング指針 [0043](0043-aggregate-to-table-mapping-guidelines.md)、選定遅延 [0033](0033-defer-production-db-selection.md)（本 ADR で supersede）。配線作業は #451。
