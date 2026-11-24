-- 双子の生産と、繁殖成績あたりの産駒の登録枠（#625 / ADR-0092）。
--
-- 繁殖成績報告書（様式第14号）は「産駒あり」の中に双子の印を持ち、もう一頭の成績を裏に書かせる。
-- 1 つの繁殖成績から血統登録できる産駒は、双子なら 2 頭・そうでなければ 1 頭。
--
-- - breeding_result: もう一頭の分娩日を足す（生産のときだけ持てる）
-- - blood_horse: 生まれた繁殖成績と枠（PRIMARY / TWIN）を逆リンクとして持つ。輸入馬・移行取り込み・
--   直接の血統登録は NULL。既存行も NULL のまま（遡って埋めない）
--
-- 同じ繁殖成績の同じ枠に 2 頭は入れないことを UNIQUE で DB 単独でも守る（最大 2 頭）。双子でない生産で
-- TWIN を使わないことはアプリ（registerFoal / registerBroughtInFoal）が守る。
-- CHECK と FK は NOT VALID で張り、VALIDATE は V29 に分ける（ADR-0052）。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

ALTER TABLE studbook.breeding_result ADD COLUMN outcome_twin_foaling_date DATE;

-- もう一頭の分娩日を持てるのは生産（LIVE_FOAL）だけ。outcome_type は nullable なので IS NOT NULL を前置する（#983）
ALTER TABLE studbook.breeding_result
ADD CONSTRAINT chk_breeding_result_twin_foaling_date CHECK (
    outcome_twin_foaling_date IS NULL
    OR (outcome_type IS NOT NULL AND outcome_type = 'LIVE_FOAL')
) NOT VALID;

ALTER TABLE studbook.blood_horse ADD COLUMN bred_from_breeding_result_id UUID;

ALTER TABLE studbook.blood_horse ADD COLUMN bred_from_position VARCHAR(16);

-- 繁殖成績 ID と枠は揃って在るか、揃って無い
ALTER TABLE studbook.blood_horse
ADD CONSTRAINT chk_blood_horse_bred_from_pair CHECK (
    (bred_from_breeding_result_id IS NULL AND bred_from_position IS NULL)
    OR (bred_from_breeding_result_id IS NOT NULL AND bred_from_position IS NOT NULL)
) NOT VALID;

ALTER TABLE studbook.blood_horse
ADD CONSTRAINT chk_blood_horse_bred_from_position CHECK (
    bred_from_position IS NULL OR bred_from_position IN ('PRIMARY', 'TWIN')
) NOT VALID;

-- 逆リンクを持てるのは、繁殖成績から生まれる内国産と持込馬だけ（origin_type は NOT NULL）
ALTER TABLE studbook.blood_horse
ADD CONSTRAINT chk_blood_horse_bred_from_origin CHECK (
    bred_from_breeding_result_id IS NULL OR origin_type IN ('DOMESTIC', 'BROUGHT_IN')
) NOT VALID;

-- 世界をまたいだ参照を封じる複合 FK（V19 の規約）
ALTER TABLE studbook.blood_horse
ADD CONSTRAINT fk_blood_horse_bred_from_breeding_result
FOREIGN KEY (world_id, bred_from_breeding_result_id)
REFERENCES studbook.breeding_result (world_id, id) NOT VALID;

-- 同じ繁殖成績の同じ枠に 2 頭は入れない。先頭 2 列が FK の列と同じなので FK の索引も兼ねる
ALTER TABLE studbook.blood_horse
-- squawk-ignore disallowed-unique-constraint,constraint-missing-not-valid
ADD CONSTRAINT uq_blood_horse_bred_from UNIQUE (
    world_id, bred_from_breeding_result_id, bred_from_position
);

COMMENT ON COLUMN studbook.breeding_result.outcome_twin_foaling_date IS
'双子のもう一頭の分娩日（生産 LIVE_FOAL で双子のときのみ。様式第14号裏「双子の繁殖成績」）';

COMMENT ON COLUMN studbook.blood_horse.bred_from_breeding_result_id IS
'生まれた繁殖成績（内国産・持込馬で繁殖成績から登録したときのみ）';

COMMENT ON COLUMN studbook.blood_horse.bred_from_position IS
'繁殖成績の中での枠（PRIMARY=様式の表の産駒 / TWIN=双子のもう一頭）';
