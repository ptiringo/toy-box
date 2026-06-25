-- jockey テーブル（ADR-0027）。スキーマは H2(PostgreSQL 互換モード) と PostgreSQL の双方で適用される
-- （ランタイムは H2、永続化の契約テストは Testcontainers の PostgreSQL。型は両者で互換）。
-- 識別子は外部採番の UUIDv7（ADR-0005）をアプリ側で採番して渡す（DB 採番ではない）。
-- version は楽観ロック兼「新規 insert 判定」用の列（version が null のとき Spring Data JDBC が新規とみなす）。
CREATE TABLE jockey (
    id UUID NOT NULL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    version BIGINT
);
