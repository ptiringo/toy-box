-- 全テーブルの version 列（楽観ロック用、V1〜V5 で導入）へ NOT NULL を遡及付与する（#531 / ADR-0047）。
-- アプリ経由の insert では Spring Data JDBC が必ず 0 以上を書くため既存行は非 NULL のはずだが、
-- 手動シード・外部ツール経由の NULL 混入をスキーマ側でも強制して多層防御にする（PR #526 最終レビューの指摘）。
-- 既存行があるテーブルへの遡及付与は V6（#522）と同じ 2 段階の作法で行う:
-- NOT VALID の CHECK で新規行への強制を即時開始（既存行は未検証・ロック最小）し、
-- VALIDATE CONSTRAINT で既存行を後追い検証する（SHARE UPDATE EXCLUSIVE・書き込みを止めない。
-- NULL 行が存在すればここで失敗し、混入の見逃しを防ぐ）。
-- そのうえで SET NOT NULL を付与する。PostgreSQL 12+ は検証済み CHECK があると
-- フルスキャンを省略するため、テーブルロック時間は最小で済む。
-- 目的を果たした中間 CHECK は同一制約の重複になるため最後に削除する。
-- timeout は squawk（require-timeout-settings）に従い設定する。Flyway は各マイグレーションを
-- 1 トランザクションで適用するため、SET LOCAL でこのマイグレーション内に閉じる。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

-- jockey
ALTER TABLE jockey
ADD CONSTRAINT chk_jockey_version_not_null
CHECK (version IS NOT NULL) NOT VALID; -- noqa: RF04

ALTER TABLE jockey
VALIDATE CONSTRAINT chk_jockey_version_not_null;

ALTER TABLE jockey
ALTER COLUMN version SET NOT NULL; -- noqa: RF04

ALTER TABLE jockey
DROP CONSTRAINT chk_jockey_version_not_null;

-- breeding_registration
ALTER TABLE breeding_registration
ADD CONSTRAINT chk_breeding_registration_version_not_null
CHECK (version IS NOT NULL) NOT VALID; -- noqa: RF04

ALTER TABLE breeding_registration
VALIDATE CONSTRAINT chk_breeding_registration_version_not_null;

ALTER TABLE breeding_registration
ALTER COLUMN version SET NOT NULL; -- noqa: RF04

ALTER TABLE breeding_registration
DROP CONSTRAINT chk_breeding_registration_version_not_null;

-- blood_horse
ALTER TABLE blood_horse
ADD CONSTRAINT chk_blood_horse_version_not_null
CHECK (version IS NOT NULL) NOT VALID; -- noqa: RF04

ALTER TABLE blood_horse
VALIDATE CONSTRAINT chk_blood_horse_version_not_null;

ALTER TABLE blood_horse
ALTER COLUMN version SET NOT NULL; -- noqa: RF04

ALTER TABLE blood_horse
DROP CONSTRAINT chk_blood_horse_version_not_null;

-- breeding_result
ALTER TABLE breeding_result
ADD CONSTRAINT chk_breeding_result_version_not_null
CHECK (version IS NOT NULL) NOT VALID; -- noqa: RF04

ALTER TABLE breeding_result
VALIDATE CONSTRAINT chk_breeding_result_version_not_null;

ALTER TABLE breeding_result
ALTER COLUMN version SET NOT NULL; -- noqa: RF04

ALTER TABLE breeding_result
DROP CONSTRAINT chk_breeding_result_version_not_null;

-- horse_inspection
ALTER TABLE horse_inspection
ADD CONSTRAINT chk_horse_inspection_version_not_null
CHECK (version IS NOT NULL) NOT VALID; -- noqa: RF04

ALTER TABLE horse_inspection
VALIDATE CONSTRAINT chk_horse_inspection_version_not_null;

ALTER TABLE horse_inspection
ALTER COLUMN version SET NOT NULL; -- noqa: RF04

ALTER TABLE horse_inspection
DROP CONSTRAINT chk_horse_inspection_version_not_null;

-- V7 のコメント「NULL のとき新規」は NOT NULL 化と矛盾して読めるため更新する。
-- （Spring Data JDBC の新規 insert 判定はエンティティ側の version が NULL かどうかで行われ、
-- 保存済み行の列値は常に非 NULL。）
COMMENT ON COLUMN jockey.version IS '楽観ロック用バージョン（新規判定の NULL はエンティティ側のみ。保存済み行は常に非 NULL）';
COMMENT ON COLUMN breeding_registration.version IS '楽観ロック用バージョン（新規判定の NULL はエンティティ側のみ。保存済み行は常に非 NULL）';
COMMENT ON COLUMN blood_horse.version IS '楽観ロック用バージョン（新規判定の NULL はエンティティ側のみ。保存済み行は常に非 NULL）';
COMMENT ON COLUMN breeding_result.version IS '楽観ロック用バージョン（新規判定の NULL はエンティティ側のみ。保存済み行は常に非 NULL）';
COMMENT ON COLUMN horse_inspection.version IS '楽観ロック用バージョン（新規判定の NULL はエンティティ側のみ。保存済み行は常に非 NULL）';
