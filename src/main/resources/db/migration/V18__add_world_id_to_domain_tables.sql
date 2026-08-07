-- 世界スコープ化の第 1 段: world_id 列の追加（#704 / ADR-0067）。
-- プレイヤーごとの世界（セーブデータ＝テナント）でデータを分離するため、永続化を持つ 6 集約テーブルに
-- 世界の所属を持たせる。
--
-- ここでは列を nullable で足すだけにする。NOT NULL 化・UNIQUE・FK は V19 で行う。段階を分けるのは、
-- 列と制約を同時に入れると、アプリ側（Row / リポジトリ）が world_id を書けるようになる前に全ての INSERT が
-- 落ちるため。nullable 追加 → アプリ配線 → 制約付与 の順なら各段階で動く状態を保てる。
--
-- ALTER TABLE ... ADD COLUMN はデフォルト値を伴わないため PostgreSQL 11+ でテーブル書き換えを起こさない
-- （メタデータのみの操作）。ACCESS EXCLUSIVE は取るので timeout は squawk（require-timeout-settings）に従う。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.blood_horse ADD COLUMN world_id UUID;
ALTER TABLE studbook.breeding_registration ADD COLUMN world_id UUID;
ALTER TABLE studbook.breeding_result ADD COLUMN world_id UUID;
ALTER TABLE studbook.horse_inspection ADD COLUMN world_id UUID;
ALTER TABLE studbook.covering_report ADD COLUMN world_id UUID;
ALTER TABLE racing.jockey ADD COLUMN world_id UUID;

COMMENT ON COLUMN studbook.blood_horse.world_id IS 'この行が属する世界（セーブデータ）のID';
COMMENT ON COLUMN studbook.breeding_registration.world_id IS 'この行が属する世界（セーブデータ）のID';
COMMENT ON COLUMN studbook.breeding_result.world_id IS 'この行が属する世界（セーブデータ）のID';
COMMENT ON COLUMN studbook.horse_inspection.world_id IS 'この行が属する世界（セーブデータ）のID';
COMMENT ON COLUMN studbook.covering_report.world_id IS 'この行が属する世界（セーブデータ）のID';
COMMENT ON COLUMN racing.jockey.world_id IS 'この行が属する世界（セーブデータ）のID';
