-- V28 で NOT VALID のまま張った制約の既存行検証（ADR-0052 の分離規約）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.breeding_result VALIDATE CONSTRAINT chk_breeding_result_twin_foaling_date;

ALTER TABLE studbook.blood_horse VALIDATE CONSTRAINT chk_blood_horse_bred_from_pair;

ALTER TABLE studbook.blood_horse VALIDATE CONSTRAINT chk_blood_horse_bred_from_position;

ALTER TABLE studbook.blood_horse VALIDATE CONSTRAINT chk_blood_horse_bred_from_origin;

ALTER TABLE studbook.blood_horse VALIDATE CONSTRAINT fk_blood_horse_bred_from_breeding_result;
