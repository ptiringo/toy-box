-- iam コンテキスト（#606 書き直し / ADR-0064）。IdP の subject に、この API が管理するアカウントと
-- そのプレイヤーの「世界」（セーブデータ＝テナント）を結びつける。
-- 資格情報は持たない（認証は GCP Identity Platform に委譲し、この API は ID トークンを検証するだけ）。
--
-- ロール・権限のテーブルは作らない。認可の判断は「この世界はあなたのものか」の 1 つだけで、
-- それは account と world の所有関係そのもの（世界ごとにデータが閉じるため、権限を配る軸が存在しない）。
--
-- world.account_id には専用 index を張らない。UNIQUE (account_id, name) の索引が account_id を
-- 先頭列に持つため、アカウント単位の絞り込みはその索引で足りる。
--
-- timeout は squawk（require-timeout-settings）に従い設定する。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

CREATE SCHEMA IF NOT EXISTS iam;

CREATE TABLE iam.account (
    id UUID NOT NULL PRIMARY KEY,
    subject_id VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL, -- noqa: RF04
    CONSTRAINT uq_account_subject_id UNIQUE (subject_id)
);

COMMENT ON TABLE iam.account IS 'この API の利用者アカウント（IdP の subject に世界を結びつける）';
COMMENT ON COLUMN iam.account.id IS 'アカウントID（外部採番の UUIDv7）';
COMMENT ON COLUMN iam.account.subject_id IS 'IdP（Identity Platform）が発行する ID トークンの sub';
COMMENT ON COLUMN iam.account.version IS '楽観ロック兼 insert 判定用の version';

CREATE TABLE iam.world (
    id UUID NOT NULL PRIMARY KEY,
    account_id UUID NOT NULL,
    name VARCHAR(64) NOT NULL, -- noqa: RF04
    version BIGINT NOT NULL, -- noqa: RF04
    CONSTRAINT fk_world_account FOREIGN KEY (account_id) REFERENCES iam.account (id) ON DELETE CASCADE,
    CONSTRAINT uq_world_account_id_name UNIQUE (account_id, name)
);

COMMENT ON TABLE iam.world IS 'プレイヤーごとの世界（セーブデータ）。全ドメインのデータはいずれかの世界に属する';
COMMENT ON COLUMN iam.world.id IS '世界ID（外部採番の UUIDv7）';
COMMENT ON COLUMN iam.world.account_id IS '世界を所有するアカウントのID（削除時は配下の世界も消える）';
COMMENT ON COLUMN iam.world.name IS 'プレイヤーが付けた世界の名前（同一アカウント内で一意）';
COMMENT ON COLUMN iam.world.version IS '楽観ロック兼 insert 判定用の version';
