-- 馬名・繁殖年の一意性に対する DB UNIQUE 制約の backstop（#544）。
-- ドメインサービス（nameHorse / recordCovering / recordUncovered）は既存レコードを読み取って一意性を
-- 検証するが、READ COMMITTED 下の read-then-insert には並行競合の窓がある（#532）。検証をすり抜けた
-- 敗者の insert を DB 側で拒否する多層防御。covering_report は #540 で対応済みで、残り 2 箇所を揃える。
--
-- ロック: 既存テーブルへの UNIQUE 追加は索引構築のあいだ ACCESS EXCLUSIVE を取り、読み書きを止める。
-- 「書き込みを止めない」ものではない（ADR-0052 が V6/V8/V9 のコメントを事実誤認と断じた点）。現行の
-- データ量では一瞬で終わるため受け入れる。テーブルが育ったら CONCURRENTLY へ移す（ADR-0060）。
--
-- PostgreSQL は NOT VALID を CHECK と FOREIGN KEY にしか許さず UNIQUE には使えないため、ADR-0052 の
-- 「VALIDATE を別マイグレーションへ分離する」規約は本ファイルの対象外。squawk の
-- constraint-missing-not-valid / disallowed-unique-constraint はこの文脈では従えない助言なので、
-- 当該文に限って抑止する（-- squawk-ignore-file でファイル全体を黙らせない。ADR-0060）。
--
-- blood_horse.name は未命名の馬で NULL になるが、PostgreSQL は UNIQUE 制約で NULL 同士を衝突とみなさ
-- ないため素の UNIQUE (name) で足りる（partial index は不要）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.blood_horse
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_blood_horse_name UNIQUE (name);

ALTER TABLE studbook.breeding_result
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_breeding_result_registration_year
UNIQUE (breeding_registration_id, breeding_year);
