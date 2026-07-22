-- V15 で NOT VALID のまま張った出自 CHECK の既存行検証（ADR-0052 の分離規約）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.blood_horse
VALIDATE CONSTRAINT chk_blood_horse_origin;
