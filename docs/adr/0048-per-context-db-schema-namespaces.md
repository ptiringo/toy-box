# 0048. DB スキーマ名前空間を境界づけられたコンテキスト別に分割する

- Status: Accepted
- Date: 2026-07-02
- Deciders: Matsui

## Context（背景・課題）

tbls 導入（#447）の crit レビューで、生成された dbdoc の**全テーブルが `public` スキーマ**に置かれていると指摘された（#509）。DB スキーマ（名前空間）戦略を決める必要がある。

現状: 全テーブルが `public`。テーブルを持つのは **studbook**（`blood_horse` / `breeding_registration` / `breeding_result` / `horse_inspection`）と **racing**（`jockey`）の 2 コンテキストのみ。`sakamichi` / `tennis` は永続化を持たない。境界づけられたコンテキストの分離は、コード層で ArchUnit により既に強制されている。

### 検討した代替案

- **案A（`public` 一択・明示的決定として記録）**: 最小構成で YAGNI。分離はコード層で既に効いており、多スキーマの複雑さを持ち込まない。
- **案B（コンテキスト別スキーマに分割・採用）**: 分離を DB 層にも反映し、テーブル帰属を明示化する。tbls ドキュメントもスキーマ別に整理される。
- **案C（`public` のままテーブル名にコンテキスト接頭辞）**: 既存命名の全面改名が要り、スキーマ分離より分離が弱い。

本プロジェクトはコンテキスト分離を中核価値とし、それを **DB 層にも反映する**ことを選ぶ（案B）。多スキーマの複雑さ（Flyway・Spring Data JDBC・H2↔PostgreSQL パリティ・tbls）は引き受ける。

## Decision（決定）

境界づけられたコンテキストごとに DB スキーマを分ける。

- **スキーマ割当**: `studbook`（`blood_horse` / `breeding_registration` / `breeding_result` / `horse_inspection`）、`racing`（`jockey`）。
- `sakamichi` / `tennis` は**永続化を持ったとき初めてスキーマを作る**（空スキーマの投機的先行作成はしない）。
- スキーマ名は**小文字**（`studbook` / `racing`）。H2 は `DATABASE_TO_LOWER=TRUE` で識別子を小文字化するため PostgreSQL と一致する。
- **Flyway**: 各マイグレーションで `CREATE SCHEMA IF NOT EXISTS <ctx>;` ＋ 完全修飾 `CREATE TABLE <ctx>.<table>`。`search_path` 依存を避け H2 / PostgreSQL で可搬にする。`flyway_schema_history` は既定の `public` に残す（`spring.flyway.schemas` は設定しない）。
- **Spring Data JDBC**: 各 Row に `@Table(schema = "<ctx>", name = "<table>")`（Spring Data Relational 3.x の `schema` 属性）。
- **FK（[0047](0047-cross-aggregate-foreign-keys-as-safety-net.md)）はコンテキスト内（同一スキーマ内）に限定**。現状 FK 6 本はすべて studbook 内で閉じるため**クロススキーマ FK は発生しない**。コンテキスト間参照は ID 参照のまま FK を張らず、ArchUnit のコンテキスト分離を DB 層にも反映する。
- tbls（#447）関連の便益（スキーマ別ドキュメント整理）は **#447 マージ後**に有効化（現状 `.tbls.yml` 未導入）。

### 実装タイミングと方式

- 決定は本 ADR で確定し、**実装は #451 の H2 cutover に委ね**、[0047](0047-cross-aggregate-foreign-keys-as-safety-net.md) の FK 追加と**同じ協調マイグレーションで一括実施**する。
- 既存テーブルの移設は `ALTER TABLE ... SET SCHEMA`。H2 とのパリティ・移行安全性の観点から、H2 cutover 後（PostgreSQL 専用）に行う。cutover まで現状維持（`public`）。cutover 前に新設される表も `public` に置き、移行期のクロススキーマ FK の混乱を避けるため cutover で一括移設する。

## Consequences（結果・影響）

- **得られるもの**: テーブル帰属がスキーマで明示化され、コンテキスト分離が DB 層にも表れる。将来コンテキスト別のアクセス制御・別 DB 化・別デプロイへ発展させる余地。tbls ドキュメントがスキーマ別に整理される（#447 後）。「FK はコンテキスト内のみ」という規律が構造的に可視化される（[0047](0047-cross-aggregate-foreign-keys-as-safety-net.md) と相互補強）。
- **引き受けるもの**: 多スキーマの複雑さ — Flyway のスキーマ作成順・完全修飾、Spring Data JDBC のスキーマ修飾（`@Table`）、H2↔PostgreSQL の挙動差の継続確認、tbls の対象スキーマ指定。テーブルを持つのが 2 コンテキストのみの現状に対し先行投資となる。cutover まで実装は保留。
- **再検討・撤回条件**: 多スキーマの運用コストが便益を上回ると判明した場合、`public` 一択へ戻す ADR を起こす（本 ADR を Superseded）。
- **関連**: [0047](0047-cross-aggregate-foreign-keys-as-safety-net.md)（コンテキスト内 FK・相互補強）、[0038](0038-inspection-subdomain-aggregate.md)（審査サブドメイン）、[0027](0027-persistence-spring-data-jdbc.md) / [0030](0030-jdbc-only-persistence-retire-inmemory.md)（永続化）、[0044](0044-adopt-prisma-postgres-for-production-db.md)（本番 Prisma Postgres）、[0043](0043-aggregate-to-table-mapping-guidelines.md)（マッピング指針）、#447 / #451 / #507 / #509。#507（Atlas 宣言的スキーマ）を採用する場合はスキーマ名前空間・FK を宣言的に表現できる。
