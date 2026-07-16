-- 血統登録番号・繁殖登録番号の一意性に対する DB UNIQUE 制約の backstop（#652）。
-- ドメインサービス（ensurePedigreeRegistrationNumberAvailable / ensureBreedingRegistrationNumberAvailable）
-- は既存レコードを読み取って一意性を検証するが、READ COMMITTED 下の read-then-insert には並行競合の窓が
-- ある（#532）。検証をすり抜けた敗者の insert を DB 側で拒否する多層防御。
--
-- 血統登録番号（blood_horse）と繁殖登録番号（breeding_registration）は別の採番空間なので、原簿ごとに
-- 独立した UNIQUE を張る（登録規程 第3〜5条）。
--
-- ロック: 既存テーブルへの UNIQUE 追加は索引構築のあいだ ACCESS EXCLUSIVE を取り、読み書きを止める。
-- 現行のデータ量では一瞬で終わるため受け入れる。テーブルが育ったら CONCURRENTLY へ移す（ADR-0062）。
--
-- PostgreSQL は NOT VALID を UNIQUE に使えないため、ADR-0052 の「VALIDATE を別マイグレーションへ分離」規約は
-- 本ファイルの対象外。squawk の constraint-missing-not-valid / disallowed-unique-constraint は当該文に限って抑止する。
--
-- registration_number は両テーブルとも NOT NULL（V2 / V3）のため、馬名（NULL 可）のような NULL 論点は無い。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.blood_horse
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_blood_horse_registration_number UNIQUE (registration_number);

ALTER TABLE studbook.breeding_registration
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_breeding_registration_registration_number UNIQUE (registration_number);
