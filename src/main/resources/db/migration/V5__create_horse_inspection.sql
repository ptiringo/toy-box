-- horse_inspection テーブル（#312。ADR-0027 / ADR-0030 / #435 の方針に準拠）。
-- スキーマは H2(PostgreSQL 互換モード) と PostgreSQL の双方で適用される
-- （ランタイムは H2、永続化の契約テストは Testcontainers の PostgreSQL。型は両者で互換）。
-- 識別子は外部採番の UUIDv7（ADR-0005）をアプリ側で採番して渡す（DB 採番ではない）。
-- 個体識別審査（マイクロチップ・特徴記述子）と親子判定（sealed ParentageDetermination）を一体で記録する。
-- 親子判定は子テーブルを設けず、判別子 parentage_type（BY_DNA/BY_BLOOD_TYPE/BY_OVERSEAS_INSTITUTION/
-- NOT_APPLICABLE）と、BY_DNA のみが持つ dna_parentage_result にフラット化する。
-- 特徴記述子（nullable な IdentificationFeatures）は feature_* 列に nullable でフラット化する（未記録なら全 NULL）。
-- ParentageDetermination の不変条件（BY_DNA のときだけ DNA 結果を持つ）を CHECK 制約でスキーマ側にも強制する。
-- version は楽観ロック兼「新規 insert 判定」用の列。
CREATE TABLE horse_inspection (
    id UUID NOT NULL PRIMARY KEY,
    microchip_number VARCHAR(64) NOT NULL,
    parentage_type VARCHAR(32) NOT NULL,
    dna_parentage_result VARCHAR(16),
    feature_hair_whorl VARCHAR(255), -- noqa: RF04
    feature_white_markings VARCHAR(255), -- noqa: RF04
    feature_nose_print VARCHAR(255), -- noqa: RF04
    version BIGINT, -- noqa: RF04
    -- 親子判定の整合: DNA 判定(BY_DNA)のときだけ dna_parentage_result を持ち、他区分は持たない。
    CONSTRAINT chk_horse_inspection_parentage CHECK (
        (parentage_type = 'BY_DNA' AND dna_parentage_result IS NOT NULL)
        OR
        (parentage_type <> 'BY_DNA' AND dna_parentage_result IS NULL)
    )
);
