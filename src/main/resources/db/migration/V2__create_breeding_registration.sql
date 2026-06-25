-- breeding_registration テーブル（ADR-0027 / ADR-0030 / #435）。
-- スキーマは H2(PostgreSQL 互換モード) と PostgreSQL の双方で適用される
-- （ランタイムは H2、永続化の契約テストは Testcontainers の PostgreSQL。型は両者で互換）。
-- 識別子は外部採番の UUIDv7（ADR-0005）をアプリ側で採番して渡す（DB 採番ではない）。
-- role は BreedingRole（STALLION/BROODMARE）の enum 名を保持する。
-- 供用停止（BreedingRetirement）は nullable な値オブジェクトのため事由と発生日の 2 列にフラット化する
-- （両方 NULL なら供用中、両方 NOT NULL なら供用停止済み）。
-- version は楽観ロック兼「新規 insert 判定」用の列（version が NULL のとき Spring Data JDBC が新規とみなす）。
CREATE TABLE breeding_registration (
    id UUID NOT NULL PRIMARY KEY,
    registration_number VARCHAR(255) NOT NULL,
    registered_horse_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL, -- noqa: RF04
    retirement_reason VARCHAR(32),
    retirement_occurred_on DATE,
    version BIGINT -- noqa: RF04
);
