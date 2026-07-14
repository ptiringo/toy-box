-- iam コンテキスト（#606 / ADR-0064）。IdP の subject に、自前で管理する役割と権限を結びつける。
-- 資格情報は持たない（認証は GCP Identity Platform に委譲し、この API は ID トークンを検証するだけ）。
-- role / role_permission は権限定義そのもの（マスタ）なので、この移行で初期データまで投入する。
-- account 行は投入しない（プロビジョニング経路は未決。dev / test はテストコードから作る）。
-- timeout は squawk（require-timeout-settings）に従い設定する。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

CREATE SCHEMA IF NOT EXISTS iam;

CREATE TABLE iam.role (
    name VARCHAR(32) NOT NULL PRIMARY KEY -- noqa: RF04
);

COMMENT ON TABLE iam.role IS '役割のマスタ（制度上の立場に対応する）';
COMMENT ON COLUMN iam.role.name IS '役割名（REGISTRAR=登録機関職員 / BREEDER=生産者・種牡馬所有者 / VIEWER=読み取りのみ）';

CREATE TABLE iam.account (
    id UUID NOT NULL PRIMARY KEY,
    subject_id VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL, -- noqa: RF04
    CONSTRAINT uq_account_subject_id UNIQUE (subject_id)
);

COMMENT ON TABLE iam.account IS 'この API の利用者アカウント（IdP の subject に役割を結びつける）';
COMMENT ON COLUMN iam.account.id IS 'アカウントID（外部採番の UUIDv7）';
COMMENT ON COLUMN iam.account.subject_id IS 'IdP（Identity Platform）が発行する ID トークンの sub';
COMMENT ON COLUMN iam.account.version IS '楽観ロック兼 insert 判定用の version';

CREATE TABLE iam.account_role (
    account_id UUID NOT NULL,
    role_name VARCHAR(32) NOT NULL,
    CONSTRAINT pk_account_role PRIMARY KEY (account_id, role_name),
    CONSTRAINT fk_account_role_account FOREIGN KEY (account_id) REFERENCES iam.account (id) ON DELETE CASCADE,
    CONSTRAINT fk_account_role_role FOREIGN KEY (role_name) REFERENCES iam.role (name)
);

COMMENT ON TABLE iam.account_role IS 'アカウントに与えた役割（多対多）';
COMMENT ON COLUMN iam.account_role.account_id IS '役割を与えたアカウントのID';
COMMENT ON COLUMN iam.account_role.role_name IS '与えた役割名（iam.role への参照）';

CREATE TABLE iam.role_permission (
    role_name VARCHAR(32) NOT NULL,
    permission VARCHAR(128) NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_name, permission),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_name) REFERENCES iam.role (name)
);

COMMENT ON TABLE iam.role_permission IS '役割に紐づく権限（権限定義そのもの。アプリの Permission 定数と文字列で一致する契約）';
COMMENT ON COLUMN iam.role_permission.role_name IS '権限を持つ役割名（iam.role への参照）';
COMMENT ON COLUMN iam.role_permission.permission IS '権限（<context>:<resource>:<action>。StudbookPermissions / RacingPermissions と一致）'; -- noqa: LT05

INSERT INTO iam.role (name) VALUES
('REGISTRAR'),
('BREEDER'),
('VIEWER');

-- REGISTRAR（登録機関職員）は全ての書き込みを行える。
INSERT INTO iam.role_permission (role_name, permission) VALUES
('REGISTRAR', 'studbook:horse:register'),
('REGISTRAR', 'studbook:horse:registerImported'),
('REGISTRAR', 'studbook:horse:registerFoal'),
('REGISTRAR', 'studbook:horse:name'),
('REGISTRAR', 'studbook:inspection:record'),
('REGISTRAR', 'studbook:breedingRegistration:register'),
('REGISTRAR', 'studbook:breedingResult:recordCovering'),
('REGISTRAR', 'studbook:breedingResult:recordUncovered'),
('REGISTRAR', 'studbook:breedingResult:reportFoaling'),
('REGISTRAR', 'studbook:breedingResult:submitReport'),
('REGISTRAR', 'studbook:coveringReport:submit'),
('REGISTRAR', 'racing:jockey:register');

-- BREEDER（生産者・種牡馬所有者）は届出系のみ。
INSERT INTO iam.role_permission (role_name, permission) VALUES
('BREEDER', 'studbook:breedingResult:recordCovering'),
('BREEDER', 'studbook:breedingResult:recordUncovered'),
('BREEDER', 'studbook:breedingResult:reportFoaling'),
('BREEDER', 'studbook:breedingResult:submitReport'),
('BREEDER', 'studbook:coveringReport:submit');

-- VIEWER は書き込み権限を持たない（読み取りは認証だけで通るため role_permission に行を持たない）。
