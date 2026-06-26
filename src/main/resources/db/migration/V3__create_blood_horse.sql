-- blood_horse テーブル（ADR-0027 / ADR-0030 / #435）。
-- スキーマは H2(PostgreSQL 互換モード) と PostgreSQL の双方で適用される
-- （ランタイムは H2、永続化の契約テストは Testcontainers の PostgreSQL。型は両者で互換）。
-- 識別子は外部採番の UUIDv7（ADR-0005）をアプリ側で採番して渡す（DB 採番ではない）。
-- sex / coat_color / breed_type は対応するドメイン enum の名前を保持する。
-- 出自（sealed Origin）は子テーブルを設けず、判別子 origin_type（DOMESTIC/IMPORTED）と各バリアントの属性列を
-- フラットに並べて表す（1 集約 = 1 テーブルを保ち、1:1 子テーブルや JSON 列の迂回を避ける）。バリアント固有の列は
-- 一方のバリアントにしか現れないため、列定義としては nullable にせざるを得ない（内国産は sire_id/dam_id を、
-- 輸入は origin_country/landing_date を使い、他方は使わない）。
-- ただし「列定義が nullable」と「不正な組合せ（全 NULL・混在）を許す」は別問題なので、相互排他の不変条件は
-- 末尾の CHECK 制約（chk_blood_horse_origin）でスキーマ側にも強制する。マッパーは常に整合した行を書くが、
-- DB 単独でも sealed Origin の不変条件（判別子に応じて該当列が NOT NULL・非該当列が NULL）が破られないようにする。
-- name は馬名（未命名なら NULL）。version は楽観ロック兼「新規 insert 判定」用の列。
CREATE TABLE blood_horse (
    id UUID NOT NULL PRIMARY KEY,
    registration_number VARCHAR(255) NOT NULL,
    sex VARCHAR(16) NOT NULL,
    coat_color VARCHAR(32) NOT NULL,
    breed_type VARCHAR(32) NOT NULL,
    date_of_birth DATE NOT NULL,
    breeder VARCHAR(255) NOT NULL,
    microchip_number VARCHAR(64) NOT NULL,
    name VARCHAR(255), -- noqa: RF04
    origin_type VARCHAR(16) NOT NULL,
    sire_id UUID,
    dam_id UUID,
    origin_country VARCHAR(255),
    landing_date DATE,
    version BIGINT, -- noqa: RF04
    CONSTRAINT chk_blood_horse_origin CHECK (
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
    )
);
