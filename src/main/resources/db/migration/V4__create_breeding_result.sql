-- breeding_result テーブル（ADR-0027 / ADR-0030 / #435）。
-- スキーマは H2(PostgreSQL 互換モード) と PostgreSQL の双方で適用される
-- （ランタイムは H2、永続化の契約テストは Testcontainers の PostgreSQL。型は両者で互換）。
-- 識別子は外部採番の UUIDv7（ADR-0005）をアプリ側で採番して渡す（DB 採番ではない）。
-- breeding_year は java.time.Year の int 値。
-- 種付（nullable な Covering）は子テーブルを設けず covering_* 列にフラット化する。
-- 分娩結果（sealed FoalingOutcome?）は判別子 outcome_type＋LiveFoal のみ持つ outcome_foaling_date にフラット化する。
-- BreedingResult の不変条件（covering の有無と区分 NotCovered・分娩日の整合）を CHECK 制約でスキーマ側にも強制する。
-- version は楽観ロック兼「新規 insert 判定」用の列。
CREATE TABLE breeding_result (
    id UUID NOT NULL PRIMARY KEY,
    breeding_registration_id UUID NOT NULL,
    breeding_year INTEGER NOT NULL,
    covering_stallion_id UUID,
    covering_date DATE,
    covering_place VARCHAR(255),
    covering_certificate_number VARCHAR(255),
    outcome_type VARCHAR(32),
    outcome_foaling_date DATE,
    version BIGINT, -- noqa: RF04
    -- 種付列の整合: 種付ありなら種牡馬ID・種付日・種付証明書番号が揃って NOT NULL、種付なしなら covering_* は全 NULL
    -- （covering_place は Covering 内でも nullable なため種付ありでも NULL を許す）。
    CONSTRAINT chk_breeding_result_covering CHECK (
        (
            covering_stallion_id IS NULL AND covering_date IS NULL
            AND covering_certificate_number IS NULL AND covering_place IS NULL
        )
        OR
        (
            covering_stallion_id IS NOT NULL AND covering_date IS NOT NULL
            AND covering_certificate_number IS NOT NULL
        )
    ),
    -- 種付の有無と区分の整合: 種付なし(covering_date IS NULL)は区分が NOT_COVERED で確定、
    -- 種付あり(covering_date IS NOT NULL)は未報告(NULL)か NOT_COVERED 以外。
    CONSTRAINT chk_breeding_result_outcome_covering CHECK (
        (covering_date IS NULL AND outcome_type = 'NOT_COVERED')
        OR
        (covering_date IS NOT NULL AND (outcome_type IS NULL OR outcome_type <> 'NOT_COVERED'))
    ),
    -- 分娩日は生産(LIVE_FOAL)区分のときだけ持つ。
    CONSTRAINT chk_breeding_result_foaling_date CHECK (
        outcome_foaling_date IS NULL OR outcome_type = 'LIVE_FOAL'
    )
);
