-- 集約間の ID 参照列に FK を backstop として張る（#508 / ADR-0052）。
-- 参照整合性の一次担保はドメイン層（ADR-0021/0022 の親引き当て検証）のまま、アプリ検証を
-- すり抜けた壊れた参照を DB が最後に止める多層防御（CHECK = ADR-0043 / UNIQUE = #532 と同じ路線）。
-- ON DELETE / ON UPDATE は既定（NO ACTION）。削除ユースケースが無いため削除セマンティクスは設計しない。
-- 既存テーブルへの制約追加はフルスキャン検証で書き込みをブロックしうるため、NOT VALID で新規行への
-- 強制を即時開始し、VALIDATE CONSTRAINT で既存行を後追い検証する 2 段で行う（V6/V8 と同様）。
-- PostgreSQL は FK 列に index を自動作成しないため、参照列の index も併せて張る
-- （covering_report.stallion_breeding_registration_id は既存 UNIQUE 制約の先頭列で担保済みのため張らない）。
-- breeding_result は既存クエリ（繁殖牝馬×繁殖年の引き当て）があるため (breeding_registration_id,
-- breeding_year) の複合 index とし、先頭列で FK を担保しつつクエリにも効かせる。
-- timeout は squawk（require-timeout-settings）に従い設定する。Flyway は各マイグレーションを
-- 1 トランザクションで適用するため、SET LOCAL でこのマイグレーション内に閉じる。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE breeding_registration
ADD CONSTRAINT fk_breeding_registration_registered_horse
FOREIGN KEY (registered_horse_id) REFERENCES blood_horse (id) NOT VALID;

ALTER TABLE breeding_registration
VALIDATE CONSTRAINT fk_breeding_registration_registered_horse;

ALTER TABLE blood_horse
ADD CONSTRAINT fk_blood_horse_inspection
FOREIGN KEY (inspection_id) REFERENCES horse_inspection (id) NOT VALID;

ALTER TABLE blood_horse
VALIDATE CONSTRAINT fk_blood_horse_inspection;

ALTER TABLE blood_horse
ADD CONSTRAINT fk_blood_horse_sire
FOREIGN KEY (sire_id) REFERENCES blood_horse (id) NOT VALID;

ALTER TABLE blood_horse
VALIDATE CONSTRAINT fk_blood_horse_sire;

ALTER TABLE blood_horse
ADD CONSTRAINT fk_blood_horse_dam
FOREIGN KEY (dam_id) REFERENCES blood_horse (id) NOT VALID;

ALTER TABLE blood_horse
VALIDATE CONSTRAINT fk_blood_horse_dam;

ALTER TABLE breeding_result
ADD CONSTRAINT fk_breeding_result_breeding_registration
FOREIGN KEY (breeding_registration_id) REFERENCES breeding_registration (id) NOT VALID;

ALTER TABLE breeding_result
VALIDATE CONSTRAINT fk_breeding_result_breeding_registration;

ALTER TABLE breeding_result
ADD CONSTRAINT fk_breeding_result_covering_stallion
FOREIGN KEY (covering_stallion_id) REFERENCES blood_horse (id) NOT VALID;

ALTER TABLE breeding_result
VALIDATE CONSTRAINT fk_breeding_result_covering_stallion;

ALTER TABLE covering_report
ADD CONSTRAINT fk_covering_report_stallion_registration
FOREIGN KEY (stallion_breeding_registration_id) REFERENCES breeding_registration (id) NOT VALID;

ALTER TABLE covering_report
VALIDATE CONSTRAINT fk_covering_report_stallion_registration;

-- CREATE INDEX CONCURRENTLY はトランザクション外でしか実行できず、Flyway は各マイグレーションを
-- 1 トランザクションで適用するため通常の CREATE INDEX を使う（テーブルは小規模・SET LOCAL で保護済み）。
-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_breeding_registration_registered_horse_id -- noqa: PG01
ON breeding_registration (registered_horse_id);

-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_blood_horse_inspection_id ON blood_horse (inspection_id); -- noqa: PG01

-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_blood_horse_sire_id ON blood_horse (sire_id); -- noqa: PG01

-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_blood_horse_dam_id ON blood_horse (dam_id); -- noqa: PG01

-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_breeding_result_registration_year -- noqa: PG01
ON breeding_result (breeding_registration_id, breeding_year);

-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_breeding_result_covering_stallion_id -- noqa: PG01
ON breeding_result (covering_stallion_id);
