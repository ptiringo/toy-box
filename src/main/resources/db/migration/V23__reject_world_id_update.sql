-- 世界スコープ化の第 3 段: world_id を実質的にイミュータブルな列にする（#727 / ADR-0081）。
--
-- V19 の複合 FK が封じたのは「他人の世界の行を参照する」経路だけで、「自分の行を他人の世界へ移す」
-- 経路は空いたままだった。Spring Data JDBC の UPDATE は主キー（＋ version）で行を特定するため、save に
-- 誤った worldId を渡すと既存行の world_id が黙って書き換わる。通常経路では起きない（読み取りが常に
-- actor の世界で絞られる）が、それは「今の呼び出し方がたまたま正しい」だけで構造的な保証ではない。
--
-- 集約間の複合 FK を持つテーブル（blood_horse 等）は、world_id を書き換えると参照先が移動先の世界に
-- 無くなるため副作用として FK 違反で弾かれる。しかし他集約を参照しないテーブル（horse_inspection /
-- jockey / idempotency_record）はどの制約にも掛からず素通りする。守備範囲はそこにある。
--
-- PostgreSQL には列単位の immutable 指定が無く、CHECK は OLD を参照できない。列レベルの
-- REVOKE UPDATE(world_id) も、アプリがテーブル所有者と同一ユーザーで接続する以上（所有者は権限検査を
-- 迂回する）効かない。したがってトリガで拒否する。このリポジトリで初のトリガ導入であり、線引き
-- （トリガは不変条件の backstop に限り、業務ロジックは DB に置かない）は ADR-0081 に残した。
--
-- 既存行は検査しないため NOT VALID / VALIDATE の分離（ADR-0052）は対象外。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

-- 関数は 1 本だけ作り、全コンテキストのトリガから共有する。置き場所を iam にするのは、world_id が
-- コンテキスト間参照ではなく全コンテキストを横断するテナント軸だからで、V19 が world_id → iam.world の
-- クロススキーマ FK を ADR-0048 の例外として認めたのと同じ論法による。
CREATE FUNCTION iam.reject_world_id_update() RETURNS TRIGGER AS $$
BEGIN
    -- ERRCODE を明示するのは Spring での訳され方を決めるため。RAISE EXCEPTION の既定 SQLSTATE は
    -- P0001（PL/pgSQL Error クラス）で、SQLStateSQLExceptionTranslator はこのクラスを知らず
    -- UncategorizedSQLException に落とす。23514（check_violation）なら DataIntegrityViolationException
    -- に訳され、「起きたらプログラミングエラー」という位置づけどおり制約違反として届く。
    RAISE EXCEPTION 'world_id は変更できません（行の世界間移動は許可されていない）: %.%',
    TG_TABLE_SCHEMA, TG_TABLE_NAME
    USING ERRCODE = '23514';
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION iam.reject_world_id_update() IS
'world_id を書き換える UPDATE を拒否する（行の世界間移動の禁止）';

-- 対象は world_id を持つ全テーブル。DO ブロックで動的に張らないのは、マイグレーションが表すのは
-- 適用時点のスキーマであり、後から増えたテーブルには効かないため。将来の張り忘れは
-- WorldScopeSchemaRulesTest が pg_trigger を動的に列挙して落とす。
CREATE TRIGGER trg_blood_horse_world_id_immutable
BEFORE UPDATE ON studbook.blood_horse
FOR EACH ROW
WHEN (old.world_id IS DISTINCT FROM new.world_id)
EXECUTE FUNCTION iam.reject_world_id_update();

CREATE TRIGGER trg_breeding_registration_world_id_immutable
BEFORE UPDATE ON studbook.breeding_registration
FOR EACH ROW
WHEN (old.world_id IS DISTINCT FROM new.world_id)
EXECUTE FUNCTION iam.reject_world_id_update();

CREATE TRIGGER trg_breeding_result_world_id_immutable
BEFORE UPDATE ON studbook.breeding_result
FOR EACH ROW
WHEN (old.world_id IS DISTINCT FROM new.world_id)
EXECUTE FUNCTION iam.reject_world_id_update();

CREATE TRIGGER trg_horse_inspection_world_id_immutable
BEFORE UPDATE ON studbook.horse_inspection
FOR EACH ROW
WHEN (old.world_id IS DISTINCT FROM new.world_id)
EXECUTE FUNCTION iam.reject_world_id_update();

CREATE TRIGGER trg_covering_report_world_id_immutable
BEFORE UPDATE ON studbook.covering_report
FOR EACH ROW
WHEN (old.world_id IS DISTINCT FROM new.world_id)
EXECUTE FUNCTION iam.reject_world_id_update();

CREATE TRIGGER trg_jockey_world_id_immutable
BEFORE UPDATE ON racing.jockey
FOR EACH ROW
WHEN (old.world_id IS DISTINCT FROM new.world_id)
EXECUTE FUNCTION iam.reject_world_id_update();

CREATE TRIGGER trg_idempotency_record_world_id_immutable
BEFORE UPDATE ON shared.idempotency_record
FOR EACH ROW
WHEN (old.world_id IS DISTINCT FROM new.world_id)
EXECUTE FUNCTION iam.reject_world_id_update();
