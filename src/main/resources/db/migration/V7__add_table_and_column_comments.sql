-- 全テーブル・全カラムに COMMENT ON を付与する追補マイグレーション（#447）。
-- tbls の requireTableComment / requireColumnComment 規約を満たし、スキーマを一次情報とした
-- ドキュメント（dbdoc/）を生成するための説明をスキーマ側に保持する。
-- 既存 V1〜V6 はイミュータブル原則のため書き換えず、コメントは本マイグレーションで追補する。

-- jockey（騎手）
COMMENT ON TABLE jockey IS '騎手（JRA 競馬コンテキスト）';
COMMENT ON COLUMN jockey.id IS '識別子（外部採番の UUIDv7）';
COMMENT ON COLUMN jockey.first_name IS '名';
COMMENT ON COLUMN jockey.last_name IS '姓';
COMMENT ON COLUMN jockey.version IS '楽観ロック兼新規 insert 判定（NULL のとき新規）';

-- breeding_registration（繁殖登録）
COMMENT ON TABLE breeding_registration IS '繁殖登録（軽種馬登録コンテキスト）';
COMMENT ON COLUMN breeding_registration.id IS '識別子（外部採番の UUIDv7）';
COMMENT ON COLUMN breeding_registration.registration_number IS '登録番号';
COMMENT ON COLUMN breeding_registration.registered_horse_id IS '登録対象の血統馬 ID';
COMMENT ON COLUMN breeding_registration.breeding_role IS '繁殖の役割（STALLION/BROODMARE）';
COMMENT ON COLUMN breeding_registration.retirement_reason IS '供用停止事由（供用中は NULL）';
COMMENT ON COLUMN breeding_registration.retirement_occurred_on IS '供用停止発生日（供用中は NULL）';
COMMENT ON COLUMN breeding_registration.version IS '楽観ロック兼新規 insert 判定（NULL のとき新規）';

-- blood_horse（血統馬）
COMMENT ON TABLE blood_horse IS '血統馬（軽種馬登録コンテキスト）';
COMMENT ON COLUMN blood_horse.id IS '識別子（外部採番の UUIDv7）';
COMMENT ON COLUMN blood_horse.registration_number IS '登録番号';
COMMENT ON COLUMN blood_horse.sex IS '性別（ドメイン enum 名）';
COMMENT ON COLUMN blood_horse.coat_color IS '毛色（ドメイン enum 名）';
COMMENT ON COLUMN blood_horse.breed_type IS '品種区分（ドメイン enum 名）';
COMMENT ON COLUMN blood_horse.date_of_birth IS '生年月日';
COMMENT ON COLUMN blood_horse.breeder IS '生産者';
COMMENT ON COLUMN blood_horse.inspection_id IS '個体識別審査（horse_inspection）の ID';
COMMENT ON COLUMN blood_horse.name IS '馬名（未命名なら NULL）';
COMMENT ON COLUMN blood_horse.origin_type IS '出自の判別子（DOMESTIC/IMPORTED）';
COMMENT ON COLUMN blood_horse.sire_id IS '父馬 ID（内国産のみ）';
COMMENT ON COLUMN blood_horse.dam_id IS '母馬 ID（内国産のみ）';
COMMENT ON COLUMN blood_horse.origin_country IS '原産国（輸入のみ）';
COMMENT ON COLUMN blood_horse.landing_date IS '輸入上陸日（輸入のみ）';
COMMENT ON COLUMN blood_horse.version IS '楽観ロック兼新規 insert 判定（NULL のとき新規）';

-- breeding_result（繁殖成績）
COMMENT ON TABLE breeding_result IS '繁殖成績（軽種馬登録コンテキスト）';
COMMENT ON COLUMN breeding_result.id IS '識別子（外部採番の UUIDv7）';
COMMENT ON COLUMN breeding_result.breeding_registration_id IS '対象の繁殖登録 ID';
COMMENT ON COLUMN breeding_result.breeding_year IS '繁殖年（java.time.Year の int 値）';
COMMENT ON COLUMN breeding_result.covering_stallion_id IS '種付種牡馬 ID（種付なしは NULL）';
COMMENT ON COLUMN breeding_result.covering_date IS '種付日（種付なしは NULL）';
COMMENT ON COLUMN breeding_result.covering_place IS '種付場所（任意）';
COMMENT ON COLUMN breeding_result.covering_certificate_number IS '種付証明書番号（種付なしは NULL）';
COMMENT ON COLUMN breeding_result.outcome_type IS '分娩結果の判別子（NOT_COVERED/LIVE_FOAL 等）';
COMMENT ON COLUMN breeding_result.outcome_foaling_date IS '分娩日（生産 LIVE_FOAL のときのみ）';
COMMENT ON COLUMN breeding_result.version IS '楽観ロック兼新規 insert 判定（NULL のとき新規）';

-- horse_inspection（個体識別審査）
COMMENT ON TABLE horse_inspection IS '個体識別審査・親子判定（軽種馬登録コンテキスト）';
COMMENT ON COLUMN horse_inspection.id IS '識別子（外部採番の UUIDv7）';
COMMENT ON COLUMN horse_inspection.microchip_number IS 'マイクロチップ番号';
COMMENT ON COLUMN horse_inspection.parentage_type IS '親子判定の判別子（BY_DNA/BY_BLOOD_TYPE/BY_OVERSEAS_INSTITUTION/NOT_APPLICABLE）'; -- noqa: LT05
COMMENT ON COLUMN horse_inspection.dna_parentage_result IS 'DNA 親子判定結果（BY_DNA のときのみ）';
COMMENT ON COLUMN horse_inspection.feature_hair_whorl IS '特徴記述子: 旋毛（未記録なら NULL）';
COMMENT ON COLUMN horse_inspection.feature_white_markings IS '特徴記述子: 白徴（未記録なら NULL）';
COMMENT ON COLUMN horse_inspection.feature_nose_print IS '特徴記述子: 鼻紋（未記録なら NULL）';
COMMENT ON COLUMN horse_inspection.version IS '楽観ロック兼新規 insert 判定（NULL のとき新規）';
