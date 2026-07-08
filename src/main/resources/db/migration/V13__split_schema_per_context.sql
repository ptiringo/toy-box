-- 境界づけられたコンテキスト別に DB スキーマを分割する（#569 / ADR-0048）。
-- 現状は全テーブルが public。テーブルを持つのは studbook と racing の 2 コンテキストのみ
-- （sakamichi / tennis は永続化を持たないため、永続化を持ったとき初めてスキーマを作る）。
-- コンテキスト分離はコード層で ArchUnit が既に強制しており（ADR-0048）、それを DB 層にも反映する。
--
-- 方針:
--   - スキーマ名は小文字（PostgreSQL の識別子慣習）。
--   - CREATE SCHEMA IF NOT EXISTS で冪等に作成し、既存 5 + covering_report の 6 テーブルを
--     ALTER TABLE ... SET SCHEMA で移設する（search_path 依存を避け完全修飾で操作）。
--   - flyway_schema_history は Flyway の内部管理テーブルなので既定スキーマに残す
--     （spring.flyway.schemas は設定しない）。
--   - FK 6 本はすべて studbook 内で閉じるためクロススキーマ FK は発生しない（ADR-0053）。
--     FK 制約・index はテーブルに従って移設先スキーマへ追随する。
--
-- 安全性: 本番 Prisma Postgres 稼働中。ALTER TABLE ... SET SCHEMA はメタデータのみの操作で
-- テーブル書き換えを伴わないが ACCESS EXCLUSIVE ロックを取るため、squawk（require-timeout-settings）
-- に従い lock_timeout / statement_timeout を SET LOCAL でこのマイグレーション内に閉じる
-- （Flyway は各マイグレーションを 1 トランザクションで適用する）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

CREATE SCHEMA IF NOT EXISTS studbook;
CREATE SCHEMA IF NOT EXISTS racing;

-- studbook コンテキスト（軽種馬登録）
ALTER TABLE blood_horse SET SCHEMA studbook;
ALTER TABLE breeding_registration SET SCHEMA studbook;
ALTER TABLE breeding_result SET SCHEMA studbook;
ALTER TABLE horse_inspection SET SCHEMA studbook;
ALTER TABLE covering_report SET SCHEMA studbook;

-- racing コンテキスト（競馬）
ALTER TABLE jockey SET SCHEMA racing;
