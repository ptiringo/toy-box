-- 持込馬（種付が外国で行われ、輸出国出国以降に生まれた馬。JAIRS 登録規程 第6条第1号）を表すため、
-- 繁殖成績の種付（sealed Covering）に海外種付（OVERSEAS）を、軽種馬の出自（sealed Origin）に
-- 持込馬（BROUGHT_IN）を追加する（#624）。
--
-- 海外種付の父は日本の繁殖登録を持たない外国種牡馬で、ID ではなく属性（馬名・登録機関の国・品種・毛色）で
-- 持つ。種付の証明は種付国の血統登録機関が発行した書類による（同 第8条第1項第4号）。
--
-- 種付には判別子 covering_type を新設し、既存の種付ありの行を DOMESTIC で埋め戻す。種付の CHECK と
-- 出自の CHECK は判別子ごとの「全 NULL／全 NOT NULL」に張り直す（ADR-0043）。既存行のあるテーブルへの
-- CHECK 追加なので NOT VALID で張り、VALIDATE は V25 に分離する（ADR-0052）。
--
-- covering_type は nullable なので、判別子の比較の前に IS NOT NULL を置く。`covering_type = 'DOMESTIC'` は
-- NULL のとき FALSE ではなく NULL を返し、OR 全体も NULL になる。CHECK は NULL を違反とみなさないため、
-- 種付ありなのに判別子を持たない行（埋め戻し漏れ）が素通りする。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.breeding_result
ADD COLUMN covering_type VARCHAR(16),
ADD COLUMN covering_foreign_sire_name VARCHAR(255),
ADD COLUMN covering_foreign_sire_stud_book_country VARCHAR(255),
ADD COLUMN covering_foreign_sire_breed_type VARCHAR(32),
ADD COLUMN covering_foreign_sire_coat_color VARCHAR(32),
ADD COLUMN covering_country VARCHAR(255),
ADD COLUMN covering_proof_number VARCHAR(255);

UPDATE studbook.breeding_result
SET covering_type = 'DOMESTIC'
WHERE covering_date IS NOT NULL;

ALTER TABLE studbook.breeding_result
DROP CONSTRAINT chk_breeding_result_covering;

ALTER TABLE studbook.breeding_result
ADD CONSTRAINT chk_breeding_result_covering CHECK (
    (
        covering_type IS NULL
        AND covering_date IS NULL
        AND covering_stallion_id IS NULL
        AND covering_place IS NULL
        AND covering_certificate_number IS NULL
        AND covering_foreign_sire_name IS NULL
        AND covering_foreign_sire_stud_book_country IS NULL
        AND covering_foreign_sire_breed_type IS NULL
        AND covering_foreign_sire_coat_color IS NULL
        AND covering_country IS NULL
        AND covering_proof_number IS NULL
    )
    OR
    (
        covering_type IS NOT NULL AND covering_type = 'DOMESTIC'
        AND covering_date IS NOT NULL
        AND covering_stallion_id IS NOT NULL
        AND covering_certificate_number IS NOT NULL
        AND covering_foreign_sire_name IS NULL
        AND covering_foreign_sire_stud_book_country IS NULL
        AND covering_foreign_sire_breed_type IS NULL
        AND covering_foreign_sire_coat_color IS NULL
        AND covering_country IS NULL
        AND covering_proof_number IS NULL
    )
    OR
    (
        covering_type IS NOT NULL AND covering_type = 'OVERSEAS'
        AND covering_date IS NOT NULL
        AND covering_foreign_sire_name IS NOT NULL
        AND covering_foreign_sire_stud_book_country IS NOT NULL
        AND covering_foreign_sire_breed_type IS NOT NULL
        AND covering_foreign_sire_coat_color IS NOT NULL
        AND covering_country IS NOT NULL
        AND covering_proof_number IS NOT NULL
        AND covering_stallion_id IS NULL
        AND covering_place IS NULL
        AND covering_certificate_number IS NULL
    )
) NOT VALID;

ALTER TABLE studbook.blood_horse
ADD COLUMN foreign_sire_name VARCHAR(255),
ADD COLUMN foreign_sire_stud_book_country VARCHAR(255),
ADD COLUMN foreign_sire_breed_type VARCHAR(32),
ADD COLUMN foreign_sire_coat_color VARCHAR(32);

ALTER TABLE studbook.blood_horse
DROP CONSTRAINT chk_blood_horse_origin;

ALTER TABLE studbook.blood_horse
ADD CONSTRAINT chk_blood_horse_origin CHECK (
    (
        origin_type = 'DOMESTIC'
        AND sire_id IS NOT NULL AND dam_id IS NOT NULL
        AND origin_country IS NULL AND landing_date IS NULL
        AND foreign_sire_name IS NULL AND foreign_sire_stud_book_country IS NULL
        AND foreign_sire_breed_type IS NULL AND foreign_sire_coat_color IS NULL
    )
    OR
    (
        origin_type = 'IMPORTED'
        AND origin_country IS NOT NULL AND landing_date IS NOT NULL
        AND sire_id IS NULL AND dam_id IS NULL
        AND foreign_sire_name IS NULL AND foreign_sire_stud_book_country IS NULL
        AND foreign_sire_breed_type IS NULL AND foreign_sire_coat_color IS NULL
    )
    OR
    (
        origin_type = 'CARRIED_OVER'
        AND sire_id IS NULL AND dam_id IS NULL
        AND origin_country IS NULL AND landing_date IS NULL
        AND foreign_sire_name IS NULL AND foreign_sire_stud_book_country IS NULL
        AND foreign_sire_breed_type IS NULL AND foreign_sire_coat_color IS NULL
    )
    OR
    (
        origin_type = 'BROUGHT_IN'
        AND dam_id IS NOT NULL
        AND foreign_sire_name IS NOT NULL AND foreign_sire_stud_book_country IS NOT NULL
        AND foreign_sire_breed_type IS NOT NULL AND foreign_sire_coat_color IS NOT NULL
        AND sire_id IS NULL
        AND origin_country IS NULL AND landing_date IS NULL
    )
) NOT VALID;

COMMENT ON COLUMN studbook.breeding_result.covering_type IS '種付の判別子（DOMESTIC=国内種付/OVERSEAS=海外種付。種付なしは NULL）';
COMMENT ON COLUMN studbook.breeding_result.covering_stallion_id IS '種付種牡馬 ID（国内種付のみ。それ以外は NULL）';
COMMENT ON COLUMN studbook.breeding_result.covering_certificate_number IS '種付証明書番号（国内種付のみ。それ以外は NULL）';
COMMENT ON COLUMN studbook.breeding_result.covering_place IS '種付場所（国内種付のみ・任意）';
COMMENT ON COLUMN studbook.breeding_result.covering_foreign_sire_name IS '外国種牡馬の馬名（海外種付のみ）';
COMMENT ON COLUMN studbook.breeding_result.covering_foreign_sire_stud_book_country IS '外国種牡馬を登録している血統登録機関の国（海外種付のみ）';
COMMENT ON COLUMN studbook.breeding_result.covering_foreign_sire_breed_type IS '外国種牡馬の品種（海外種付のみ）';
COMMENT ON COLUMN studbook.breeding_result.covering_foreign_sire_coat_color IS '外国種牡馬の毛色（海外種付のみ）';
COMMENT ON COLUMN studbook.breeding_result.covering_country IS '種付が行われた国（海外種付のみ）';
COMMENT ON COLUMN studbook.breeding_result.covering_proof_number IS '種付国の血統登録機関が発行した種付事実の証明の番号（海外種付のみ）';
COMMENT ON COLUMN studbook.blood_horse.origin_type IS '出自の判別子（DOMESTIC/IMPORTED/CARRIED_OVER/BROUGHT_IN）';
COMMENT ON COLUMN studbook.blood_horse.dam_id IS '母馬 ID（内国産・持込馬のみ）';
COMMENT ON COLUMN studbook.blood_horse.foreign_sire_name IS '父である外国種牡馬の馬名（持込馬のみ）';
COMMENT ON COLUMN studbook.blood_horse.foreign_sire_stud_book_country IS '父である外国種牡馬を登録している血統登録機関の国（持込馬のみ）';
COMMENT ON COLUMN studbook.blood_horse.foreign_sire_breed_type IS '父である外国種牡馬の品種（持込馬のみ）';
COMMENT ON COLUMN studbook.blood_horse.foreign_sire_coat_color IS '父である外国種牡馬の毛色（持込馬のみ）';
