-- V19 で NOT VALID として追加した FK を検証する（#704 / ADR-0052）。
-- ADD CONSTRAINT のロックはコミットまで残るため、同一ファイルに置くと VALIDATE の
-- SHARE UPDATE EXCLUSIVE への格下げが効かない。テーブル規模によらず常に分離する。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

-- world_id → iam.world（6 本）
ALTER TABLE studbook.blood_horse VALIDATE CONSTRAINT fk_blood_horse_world;
ALTER TABLE studbook.breeding_registration VALIDATE CONSTRAINT fk_breeding_registration_world;
ALTER TABLE studbook.breeding_result VALIDATE CONSTRAINT fk_breeding_result_world;
ALTER TABLE studbook.horse_inspection VALIDATE CONSTRAINT fk_horse_inspection_world;
ALTER TABLE studbook.covering_report VALIDATE CONSTRAINT fk_covering_report_world;
ALTER TABLE racing.jockey VALIDATE CONSTRAINT fk_jockey_world;

-- 集約間の複合 FK（7 本）
ALTER TABLE studbook.breeding_registration
VALIDATE CONSTRAINT fk_breeding_registration_registered_horse;
ALTER TABLE studbook.blood_horse VALIDATE CONSTRAINT fk_blood_horse_inspection;
ALTER TABLE studbook.blood_horse VALIDATE CONSTRAINT fk_blood_horse_sire;
ALTER TABLE studbook.blood_horse VALIDATE CONSTRAINT fk_blood_horse_dam;
ALTER TABLE studbook.breeding_result VALIDATE CONSTRAINT fk_breeding_result_breeding_registration;
ALTER TABLE studbook.breeding_result VALIDATE CONSTRAINT fk_breeding_result_covering_stallion;
ALTER TABLE studbook.covering_report VALIDATE CONSTRAINT fk_covering_report_stallion_registration;
