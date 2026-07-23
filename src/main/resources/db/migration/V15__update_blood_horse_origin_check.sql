-- 出自（sealed Origin）に第 3 バリアント「移行取り込み（CARRIED_OVER）」を追加する（#633）。
-- 先行する登録原簿に血統登録済みの馬をシステム境界で取り込む経路で、父母 ID（内国産）も
-- 原産国・揚陸日（輸入）も持たない。判別子の許容値を 3 択に広げ、CARRIED_OVER のとき
-- バリアント固有列がすべて NULL であることをスキーマ側でも強制する（V3 と同じ多層防御）。
-- 既存 CHECK は 2 択なので、いったん落として張り直す。既存行のあるテーブルへの CHECK 追加
-- なので NOT VALID で張り、VALIDATE は V16 に分離する（ADR-0052）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.blood_horse
DROP CONSTRAINT chk_blood_horse_origin;

ALTER TABLE studbook.blood_horse
ADD CONSTRAINT chk_blood_horse_origin CHECK (
    (
        origin_type = 'DOMESTIC'
        AND sire_id IS NOT NULL AND dam_id IS NOT NULL
        AND origin_country IS NULL AND landing_date IS NULL
    )
    OR
    (
        origin_type = 'IMPORTED'
        AND origin_country IS NOT NULL AND landing_date IS NOT NULL
        AND sire_id IS NULL AND dam_id IS NULL
    )
    OR
    (
        origin_type = 'CARRIED_OVER'
        AND sire_id IS NULL AND dam_id IS NULL
        AND origin_country IS NULL AND landing_date IS NULL
    )
) NOT VALID;

COMMENT ON COLUMN studbook.blood_horse.origin_type IS '出自の判別子（DOMESTIC/IMPORTED/CARRIED_OVER）';
