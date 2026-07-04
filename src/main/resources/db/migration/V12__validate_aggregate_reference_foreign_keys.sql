-- V11 で NOT VALID 追加した集約間 FK（#508 / ADR-0053）の既存行を後追い検証する。
-- ADR-0052 に従い VALIDATE CONSTRAINT のみを別マイグレーションに分離する（別トランザクションに
-- なることで V11 の ADD CONSTRAINT のロックがコミットで解け、本ファイルの VALIDATE は
-- SHARE UPDATE EXCLUSIVE で走り書き込みを止めない）。他の DDL は同居させない。
-- timeout は squawk（require-timeout-settings）に従い設定する。SET LOCAL は
-- このマイグレーションのトランザクション内に閉じる。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE breeding_registration
VALIDATE CONSTRAINT fk_breeding_registration_registered_horse;

ALTER TABLE blood_horse
VALIDATE CONSTRAINT fk_blood_horse_inspection;

ALTER TABLE blood_horse
VALIDATE CONSTRAINT fk_blood_horse_sire;

ALTER TABLE blood_horse
VALIDATE CONSTRAINT fk_blood_horse_dam;

ALTER TABLE breeding_result
VALIDATE CONSTRAINT fk_breeding_result_breeding_registration;

ALTER TABLE breeding_result
VALIDATE CONSTRAINT fk_breeding_result_covering_stallion;

ALTER TABLE covering_report
VALIDATE CONSTRAINT fk_covering_report_stallion_registration;
