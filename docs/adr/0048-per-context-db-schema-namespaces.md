# 0048. DB スキーマ名前空間を境界づけられたコンテキスト別に分割する

- Status: Accepted
- Date: 2026-07-07
- Deciders: ptiringo, Claude Code

## Context（背景・課題）

tbls 導入（#447 / [ADR-0045](0045-tbls-db-schema-docs.md)）の crit レビューで、生成された dbdoc の**全テーブルが `public` スキーマ**に置かれていると指摘された（#509）。DB スキーマ（名前空間）戦略を決める必要がある。

現状: 全テーブルが `public`。テーブルを持つのは **studbook**（`blood_horse` / `breeding_registration` / `breeding_result` / `horse_inspection`）と **racing**（`jockey`）の 2 コンテキストのみ。`sakamichi` / `tennis` は永続化を持たない。境界づけられたコンテキストの分離は、コード層で ArchUnit により既に強制されている。

この決定は当初 #513（クローズ済み）で ADR-0047/0048 として起案されたが、同 PR は集約間 FK の決定（現 [ADR-0053](0053-foreign-key-backstop-across-aggregates.md)）と束ねられ、ブランチが古くなりコンフリクトで宙に浮いた。FK 側は ADR-0053 として先行マージされたため、本 ADR は**スキーマ名前空間の決定のみ**を最新 main の上で切り直したものである。

### 検討した代替案

- **案A（`public` 一択・明示的決定として記録）**: 最小構成で YAGNI。分離はコード層で既に効いており、多スキーマの複雑さを持ち込まない。しかしコンテキスト分離を中核価値とする本プロジェクトの思想が DB 層に反映されない。
- **案B（コンテキスト別スキーマに分割・採用）**: 分離を DB 層にも反映し、テーブル帰属を明示化する。tbls ドキュメントもスキーマ別に整理される。H2 全面脱却（#451 / [ADR-0044](0044-adopt-prisma-postgres-for-production-db.md)）により、旧起案時に案 B のコストだった H2↔PostgreSQL の識別子パリティ（`DATABASE_TO_LOWER` / `search_path` 可搬性）懸念が消滅し、案 B が当時より安くなった。
- **案C（`public` のままテーブル名にコンテキスト接頭辞）**: 既存命名の全面改名が要り、スキーマ分離より分離が弱い。

本プロジェクトはコンテキスト分離を中核価値とし、それを **DB 層にも反映する**ことを選ぶ（案 B）。

## Decision（決定）

境界づけられたコンテキストごとに DB スキーマを分ける。

- **スキーマ割当**: `studbook`（`blood_horse` / `breeding_registration` / `breeding_result` / `horse_inspection`）、`racing`（`jockey`）。
- `sakamichi` / `tennis` は**永続化を持ったとき初めてスキーマを作る**（空スキーマの投機的先行作成はしない）。
- **スキーマ名は小文字**（`studbook` / `racing`。PostgreSQL の識別子慣習に従う）。
- **Flyway**: 各マイグレーションで `CREATE SCHEMA IF NOT EXISTS <ctx>;` ＋ 完全修飾 `CREATE TABLE <ctx>.<table>`。`search_path` 依存を避ける。`flyway_schema_history` は既定スキーマに残す（`spring.flyway.schemas` は設定しない）。
- **既存テーブルの移設**: 現状 `public` にある 5 テーブルを新規マイグレーションで `ALTER TABLE ... SET SCHEMA <ctx>` により移す。本番 Prisma Postgres 稼働中のため squawk で安全性を確認する。
- **Spring Data JDBC**: 各 Row に `@Table(schema = "<ctx>", name = "<table>")`（Spring Data Relational の `schema` 属性）。
- **FK（[ADR-0053](0053-foreign-key-backstop-across-aggregates.md)）はコンテキスト内（同一スキーマ内）に限定**。現状 FK 6 本はすべて studbook 内で閉じるため**クロススキーマ FK は発生しない**。コンテキスト間参照は ID 参照のまま FK を張らず、ArchUnit のコンテキスト分離を DB 層にも反映する。
- **tbls**: スキーマ別にドキュメント生成されるよう設定を調整する。

## Consequences（結果・影響）

- **良くなる**: コンテキスト境界が DB 層にも一貫して現れ、テーブルの帰属が名前空間で明示される。tbls ドキュメントもスキーマ別に整理される。将来コンテキストを別 DB / サービスへ切り出す際の布石になる。
- **引き受けるもの**: Flyway の完全修飾 DDL・`CREATE SCHEMA`、Row の `@Table(schema=)` 付与、既存テーブルの移設マイグレーション、tbls 設定調整という多スキーマ運用の複雑さ。新しくテーブルを追加するコンテキストは対応スキーマへの割当を都度決める必要がある。
- 本 ADR は**決定の記録**であり、実装（移設マイグレーション・Row 修正・tbls 設定）は #509 をクローズしたうえで**後続イシューに分割**する。
