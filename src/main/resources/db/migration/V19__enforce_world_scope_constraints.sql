-- 世界スコープ化の第 2 段: 制約の付与（#704 / ADR-0067）。
-- V18 で足した world_id を NOT NULL 化し、世界をまたいだ参照を DB で成立不能にする。
--
-- 既存行の扱い: world_id の NOT NULL 化は既存行があると通らない。本番（Prisma Postgres）にあるのは sandbox の
-- 動作確認データのみなので、移行用の世界に寄せるのではなく捨てる。持ち主不明の行にダミーのアカウントを
-- でっち上げる概念を残さないため。FK の依存順に沿って DELETE する。
--
-- 集約間 FK の ON DELETE は既定（NO ACTION）のままにする。世界を消すときは 6 テーブルすべてが world_id の
-- FK 経由で同一文中に消えるため NO ACTION でも成立する。ここで CASCADE を付けると「馬を消すと繁殖登録も
-- 消える」という存在しない削除ユースケースのセマンティクスを定義してしまい、ADR-0053 の「削除ユースケースが
-- 無いため削除セマンティクスは設計しない」という判断を壊す。成立性は WorldScopeContractTest で実測する。
--
-- world_id → iam.world はクロススキーマ FK になる。ADR-0048 は「FK はコンテキスト内に限定」としているが、
-- iam.world は「コンテキスト間参照」ではなく全コンテキストを横断するテナント軸であり、この 1 点に限り例外と
-- して認める（AccountId / WorldId を共有カーネルに置いたのと対称。ADR-0048 に追補した）。
--
-- world_id 単独の index は張らない。UNIQUE (world_id, id) の索引が world_id を先頭列に持つため、世界単位の
-- 絞り込みはその索引で足りる（V17 の world.account_id と同じ論法）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

-- 既存行を捨てる（FK の依存順）
DELETE FROM studbook.breeding_result;
DELETE FROM studbook.covering_report;
DELETE FROM studbook.breeding_registration;
DELETE FROM studbook.blood_horse;
DELETE FROM studbook.horse_inspection;
DELETE FROM racing.jockey;

-- world_id を必須にする（上で全行を消しているため NULL は残らない）。
-- squawk の adding-not-nullable-field は「SET NOT NULL のテーブルスキャンが読み取りを止める」という警告だが、
-- 直前の DELETE で対象は空になっており、スキャンは 0 行に対して走る。助言どおり nullable + CHECK に逃がすと
-- 複合 FK の参照元・参照先として world_id が NULL を取りうることになり、世界をまたぐ参照を封じる目的を果たせない。
-- squawk-ignore adding-not-nullable-field
ALTER TABLE studbook.blood_horse ALTER COLUMN world_id SET NOT NULL;
-- squawk-ignore adding-not-nullable-field
ALTER TABLE studbook.breeding_registration ALTER COLUMN world_id SET NOT NULL;
-- squawk-ignore adding-not-nullable-field
ALTER TABLE studbook.breeding_result ALTER COLUMN world_id SET NOT NULL;
-- squawk-ignore adding-not-nullable-field
ALTER TABLE studbook.horse_inspection ALTER COLUMN world_id SET NOT NULL;
-- squawk-ignore adding-not-nullable-field
ALTER TABLE studbook.covering_report ALTER COLUMN world_id SET NOT NULL;
-- squawk-ignore adding-not-nullable-field
ALTER TABLE racing.jockey ALTER COLUMN world_id SET NOT NULL;

-- 複合 FK の参照先として (world_id, id) の UNIQUE を張る
ALTER TABLE studbook.blood_horse
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_blood_horse_world_id_id UNIQUE (world_id, id);

ALTER TABLE studbook.breeding_registration
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_breeding_registration_world_id_id UNIQUE (world_id, id);

ALTER TABLE studbook.breeding_result
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_breeding_result_world_id_id UNIQUE (world_id, id);

ALTER TABLE studbook.horse_inspection
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_horse_inspection_world_id_id UNIQUE (world_id, id);

ALTER TABLE studbook.covering_report
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_covering_report_world_id_id UNIQUE (world_id, id);

ALTER TABLE racing.jockey
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_jockey_world_id_id UNIQUE (world_id, id);

-- world_id → iam.world（世界を消すと配下が消える。DeleteWorldUseCase が前提にしている）
ALTER TABLE studbook.blood_horse
ADD CONSTRAINT fk_blood_horse_world
FOREIGN KEY (world_id) REFERENCES iam.world (id) ON DELETE CASCADE NOT VALID;

ALTER TABLE studbook.breeding_registration
ADD CONSTRAINT fk_breeding_registration_world
FOREIGN KEY (world_id) REFERENCES iam.world (id) ON DELETE CASCADE NOT VALID;

ALTER TABLE studbook.breeding_result
ADD CONSTRAINT fk_breeding_result_world
FOREIGN KEY (world_id) REFERENCES iam.world (id) ON DELETE CASCADE NOT VALID;

ALTER TABLE studbook.horse_inspection
ADD CONSTRAINT fk_horse_inspection_world
FOREIGN KEY (world_id) REFERENCES iam.world (id) ON DELETE CASCADE NOT VALID;

ALTER TABLE studbook.covering_report
ADD CONSTRAINT fk_covering_report_world
FOREIGN KEY (world_id) REFERENCES iam.world (id) ON DELETE CASCADE NOT VALID;

ALTER TABLE racing.jockey
ADD CONSTRAINT fk_jockey_world
FOREIGN KEY (world_id) REFERENCES iam.world (id) ON DELETE CASCADE NOT VALID;

-- 集約間 FK（V11 の 7 本）を複合 FK へ張り替える。これで他人の世界の馬を父に指定するといった混線が
-- 構造的に不可能になる。
ALTER TABLE studbook.breeding_registration
DROP CONSTRAINT fk_breeding_registration_registered_horse;

ALTER TABLE studbook.breeding_registration
ADD CONSTRAINT fk_breeding_registration_registered_horse
FOREIGN KEY (world_id, registered_horse_id)
REFERENCES studbook.blood_horse (world_id, id) NOT VALID;

ALTER TABLE studbook.blood_horse DROP CONSTRAINT fk_blood_horse_inspection;

ALTER TABLE studbook.blood_horse
ADD CONSTRAINT fk_blood_horse_inspection
FOREIGN KEY (world_id, inspection_id)
REFERENCES studbook.horse_inspection (world_id, id) NOT VALID;

ALTER TABLE studbook.blood_horse DROP CONSTRAINT fk_blood_horse_sire;

ALTER TABLE studbook.blood_horse
ADD CONSTRAINT fk_blood_horse_sire
FOREIGN KEY (world_id, sire_id)
REFERENCES studbook.blood_horse (world_id, id) NOT VALID;

ALTER TABLE studbook.blood_horse DROP CONSTRAINT fk_blood_horse_dam;

ALTER TABLE studbook.blood_horse
ADD CONSTRAINT fk_blood_horse_dam
FOREIGN KEY (world_id, dam_id)
REFERENCES studbook.blood_horse (world_id, id) NOT VALID;

ALTER TABLE studbook.breeding_result DROP CONSTRAINT fk_breeding_result_breeding_registration;

ALTER TABLE studbook.breeding_result
ADD CONSTRAINT fk_breeding_result_breeding_registration
FOREIGN KEY (world_id, breeding_registration_id)
REFERENCES studbook.breeding_registration (world_id, id) NOT VALID;

ALTER TABLE studbook.breeding_result DROP CONSTRAINT fk_breeding_result_covering_stallion;

ALTER TABLE studbook.breeding_result
ADD CONSTRAINT fk_breeding_result_covering_stallion
FOREIGN KEY (world_id, covering_stallion_id)
REFERENCES studbook.blood_horse (world_id, id) NOT VALID;

ALTER TABLE studbook.covering_report DROP CONSTRAINT fk_covering_report_stallion_registration;

ALTER TABLE studbook.covering_report
ADD CONSTRAINT fk_covering_report_stallion_registration
FOREIGN KEY (world_id, stallion_breeding_registration_id)
REFERENCES studbook.breeding_registration (world_id, id) NOT VALID;

-- FK 列の index を複合 FK に合わせて張り替える（先頭列が world_id でないと複合 FK の参照チェックに効かない）。
-- CREATE INDEX / DROP INDEX とも CONCURRENTLY はトランザクション外でしか実行できず、Flyway は各マイグレーションを
-- 1 トランザクションで適用するため通常の CREATE INDEX / DROP INDEX を使う（テーブルは小規模・SET LOCAL で保護済み）。
-- squawk の require-concurrent-index-creation / require-concurrent-index-deletion はこの文脈では従えない助言。
-- squawk-ignore require-concurrent-index-deletion
DROP INDEX studbook.ix_breeding_registration_registered_horse_id; -- noqa: PG01

-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_breeding_registration_registered_horse_id -- noqa: PG01
ON studbook.breeding_registration (world_id, registered_horse_id);

-- squawk-ignore require-concurrent-index-deletion
DROP INDEX studbook.ix_blood_horse_inspection_id; -- noqa: PG01

-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_blood_horse_inspection_id -- noqa: PG01
ON studbook.blood_horse (world_id, inspection_id);

-- squawk-ignore require-concurrent-index-deletion
DROP INDEX studbook.ix_blood_horse_sire_id; -- noqa: PG01

-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_blood_horse_sire_id ON studbook.blood_horse (world_id, sire_id); -- noqa: PG01

-- squawk-ignore require-concurrent-index-deletion
DROP INDEX studbook.ix_blood_horse_dam_id; -- noqa: PG01

-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_blood_horse_dam_id ON studbook.blood_horse (world_id, dam_id); -- noqa: PG01

-- squawk-ignore require-concurrent-index-deletion
DROP INDEX studbook.ix_breeding_result_registration_year; -- noqa: PG01

-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_breeding_result_registration_year -- noqa: PG01
ON studbook.breeding_result (world_id, breeding_registration_id, breeding_year);

-- squawk-ignore require-concurrent-index-deletion
DROP INDEX studbook.ix_breeding_result_covering_stallion_id; -- noqa: PG01

-- squawk-ignore require-concurrent-index-creation
CREATE INDEX ix_breeding_result_covering_stallion_id -- noqa: PG01
ON studbook.breeding_result (world_id, covering_stallion_id);

-- 馬名の一意性を世界の中に閉じる（馬名が全プレイヤーで早い者勝ちになるのを防ぐ）。
-- breeding_result / covering_report の UNIQUE は先頭列が世界に閉じた集約IDなので変更しない。
ALTER TABLE studbook.blood_horse DROP CONSTRAINT uq_blood_horse_name;

ALTER TABLE studbook.blood_horse
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_blood_horse_name UNIQUE (world_id, name);
