-- 血統登録番号・繁殖登録番号の一意性に対する DB UNIQUE 制約の backstop（#652）。
-- ドメインサービス（ensurePedigreeRegistrationNumberAvailable / ensureBreedingRegistrationNumberAvailable）
-- は既存レコードを読み取って一意性を検証するが、READ COMMITTED 下の read-then-insert には並行競合の窓が
-- ある（#532）。検証をすり抜けた敗者の insert を DB 側で拒否する多層防御（V14 と同型）。
--
-- 血統登録番号（blood_horse）と繁殖登録番号（breeding_registration）は別の採番空間なので、原簿ごとに
-- 独立した UNIQUE を張る（登録規程 第3〜5条: 血統登録原簿と繁殖登録原簿は別の原簿で、繁殖登録個体は
-- 両番号を併せ持つ）。
--
-- 一意性の範囲は世界（セーブデータ＝テナント）の中に閉じるため先頭列に world_id を置く（ADR-0067）。
-- プレイヤーごとに独立した原簿を持つので、登録番号が全プレイヤーで早い者勝ちになってはならない
-- （V19 が uq_blood_horse_name を (world_id, name) へ張り替えたのと同じ論法）。
--
-- ロック: 既存テーブルへの UNIQUE 追加は索引構築のあいだ ACCESS EXCLUSIVE を取り、読み書きを止める。
-- 現行のデータ量では一瞬で終わるため受け入れる。テーブルが育ったら CONCURRENTLY へ移す（ADR-0062）。
--
-- PostgreSQL は NOT VALID を CHECK と FOREIGN KEY にしか許さず UNIQUE には使えないため、ADR-0052 の
-- 「VALIDATE を別マイグレーションへ分離する」規約は本ファイルの対象外。squawk の
-- constraint-missing-not-valid / disallowed-unique-constraint はこの文脈では従えない助言なので、
-- 当該文に限って抑止する。
--
-- registration_number は両テーブルとも NOT NULL（V2 / V3）のため、馬名（NULL 可）のような NULL 論点は無い。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.blood_horse
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_blood_horse_registration_number UNIQUE (world_id, registration_number);

ALTER TABLE studbook.breeding_registration
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_breeding_registration_registration_number
UNIQUE (world_id, registration_number);
