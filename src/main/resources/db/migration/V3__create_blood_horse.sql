-- blood_horse テーブル（ADR-0027 / ADR-0030 / #435）。
-- スキーマは H2(PostgreSQL 互換モード) と PostgreSQL の双方で適用される
-- （ランタイムは H2、永続化の契約テストは Testcontainers の PostgreSQL。型は両者で互換）。
-- 識別子は外部採番の UUIDv7（ADR-0005）をアプリ側で採番して渡す（DB 採番ではない）。
-- sex / coat_color / breed_type は対応するドメイン enum の名前を保持する。
-- 出自（sealed Origin）は子テーブルを設けず、判別子 origin_type（DOMESTIC/IMPORTED）と各バリアントの属性列を
-- フラットに並べて表す（内国産なら sire_id/dam_id、輸入なら origin_country/landing_date が NOT NULL）。
-- name は馬名（未命名なら NULL）。version は楽観ロック兼「新規 insert 判定」用の列。
CREATE TABLE blood_horse (
    id                  UUID         NOT NULL PRIMARY KEY,
    registration_number VARCHAR(255) NOT NULL,
    sex                 VARCHAR(16)  NOT NULL,
    coat_color          VARCHAR(32)  NOT NULL,
    breed_type          VARCHAR(32)  NOT NULL,
    date_of_birth       DATE         NOT NULL,
    breeder             VARCHAR(255) NOT NULL,
    microchip_number    VARCHAR(64)  NOT NULL,
    name                VARCHAR(255),
    origin_type         VARCHAR(16)  NOT NULL,
    sire_id             UUID,
    dam_id              UUID,
    origin_country      VARCHAR(255),
    landing_date        DATE,
    version             BIGINT
);
