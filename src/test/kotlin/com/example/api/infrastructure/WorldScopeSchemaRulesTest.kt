package com.example.api.infrastructure

import com.example.api.support.PostgresContainerSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 世界スコープのスキーマ規約テスト（#706 / ADR-0067）。
 *
 * #704 で `world_id` を足したのは、その時点で永続化を持っていた 6 テーブルだけである。後から永続化を 持つコンテキスト（sakamichi は #552、tennis
 * は #363）で `world_id` を忘れると、例外も出ないまま 全プレイヤー共有のテーブルが生まれ、データが混ざって初めて気づく。それを `./gradlew check` で
 * 落とすためのゲート。
 *
 * 対象テーブルは `pg_tables` から動的に列挙する。マイグレーションでテーブルが増えても手で追従する
 * 必要はなく、**新しいテーブルは自動で検査対象になる**（追従漏れが安全側に転ぶ）。除外するのは `iam` スキーマ（`Account` / `World`
 * はテナントの根であり世界に属さない）と Flyway の内部管理 テーブルだけ。
 *
 * 列の存在だけでなく `NOT NULL` まで見る。列を足して制約を忘れると（V18 の途中段階で止まると）、 行がどの世界にも属さないまま入れてしまうため。あわせて `world_id`
 * を書き換える UPDATE を拒否するトリガの有無（#727 / ADR-0081）と、`iam.world(id)` への FK の 有無（#905）も見る。
 *
 * 集約間の参照（#911）は 2 本で見る。世界スコープのテーブル同士の FK は `world_id` 同士を対応させた複合 FK で あること（単一列 FK
 * だと他人の世界の行を参照できる）と、`〜_id` 列がその複合 FK で覆われていること（FK の張り忘れ）。 どのテーブルがどの集約を参照すべきかはスキーマから導けないため、後者は列名
 * `〜_id` を参照列の目印にしている。 参照ではない `〜_id` 列は [NON_REFERENCE_ID_COLUMNS] に理由付きで列挙する。名前が `〜_id`
 * で終わらない参照列は素通りする（レビュー担保）。
 *
 * `pg_tables` は通常テーブルとパーティションテーブルのみを列挙し、view / materialized view / foreign table は含まない。読み取りモデル（軽量
 * CQRS の Read Model）をマテビューで実装した場合、そのマテビューは このゲートの対象外になり静かに素通りする（レビュー担保）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorldScopeSchemaRulesTest : PostgresContainerSupport() {

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `iam 以外のスキーマの全テーブルが world_id UUID NOT NULL を持つ`() {
        val violations = jdbc.queryForList(TABLES_WITHOUT_WORLD_ID, String::class.java)

        assert(violations.isEmpty()) {
            "world_id UUID NOT NULL を持たないテーブル: $violations。" +
                "世界スコープのテーブルには world_id が必要（.claude/rules/migrations.md / ADR-0067）"
        }
    }

    @Test
    fun `対象テーブルが world_id の書き換えを拒否するトリガを持つ`() {
        val violations = jdbc.queryForList(TABLES_WITHOUT_IMMUTABLE_TRIGGER, String::class.java)

        assert(violations.isEmpty()) {
            "world_id イミュータブルトリガを持たないテーブル: $violations。" +
                "トリガが無いと行が世界をまたいで移動できる（.claude/rules/migrations.md / ADR-0081）"
        }
    }

    @Test
    fun `対象テーブルが iam の world への FK を持つ`() {
        val violations = jdbc.queryForList(TABLES_WITHOUT_WORLD_FOREIGN_KEY, String::class.java)

        assert(violations.isEmpty()) {
            "iam.world(id) への FK（ON DELETE CASCADE・検証済み）を持たないテーブル: $violations。" +
                "FK が無いと他人の世界の行を参照できる（.claude/rules/migrations.md / ADR-0067）"
        }
    }

    @Test
    fun `世界スコープのテーブル同士の FK は world_id 同士を対応させた複合 FK である`() {
        val violations = jdbc.queryForList(FOREIGN_KEYS_NOT_PAIRING_WORLD_ID, String::class.java)

        assert(violations.isEmpty()) {
            "world_id 同士を対応させていない（または未検証の）集約間 FK: $violations。" +
                "(world_id, 〜_id) → (world_id, id) で張らないと他人の世界の行を参照できる" +
                "（.claude/rules/migrations.md / ADR-0067）"
        }
    }

    @Test
    fun `世界スコープのテーブルの 〜_id 列は world_id を含む複合 FK で覆われている`() {
        val violations =
            jdbc.queryForList(ID_COLUMNS_WITHOUT_COMPOSITE_FOREIGN_KEY, String::class.java)

        assert(violations.isEmpty()) {
            "複合 FK で覆われていない 〜_id 列: $violations。" +
                "集約の参照なら (world_id, 〜_id) の FK を張り、参照でないなら NON_REFERENCE_ID_COLUMNS に理由付きで足す（#911）"
        }
    }

    @Test
    fun `集約間の参照の検査対象を列挙できている`() {
        // 上の 2 本も「対象なし＝違反なし」で静かに通る。FK と 〜_id 列の列挙が全滅していないことを確かめる
        val foreignKeys = jdbc.queryForList(TARGET_FOREIGN_KEYS, String::class.java)
        val idColumns = jdbc.queryForList(TARGET_ID_COLUMNS, String::class.java)

        assert(foreignKeys.isNotEmpty())
        assert(idColumns.isNotEmpty())
    }

    @Test
    fun `検査対象のテーブルを列挙できている`() {
        // 列挙条件を誤ると「対象なし＝違反なし」で静かに通る。空振りをここで落とす
        // （PostgresContainerSupport.truncateAllTables が同じ理由で件数を検査しているのと同じ思想）。
        // `isNotEmpty()` という閾値は、列挙条件のバグで対象が「全滅」する事態の検知に限る意図であり、
        // 一部のテーブルだけが取りこぼされるケースまでは検出できない。
        val targets = jdbc.queryForList(TARGET_TABLES, String::class.java)

        assert(targets.isNotEmpty())
    }

    private companion object {
        /**
         * 世界スコープの対象を絞る CTE 本体（`iam` スキーマと Flyway 管理テーブルを除く）。
         *
         * スキーマはコンテキスト別に分かれ（ADR-0048）今後も増えるため、スキーマ名もテーブル名も ハードコードしない。`TARGET_TABLES` と
         * `TABLES_WITHOUT_WORLD_ID` の両方がこの CTE を 共有することで、除外条件の出所を 1 つに保つ（片方だけ更新されて見逃しが起きるのを防ぐ）。
         */
        val TARGET_TABLES_CTE =
            """
            WITH target AS (
                SELECT schemaname, tablename
                FROM pg_tables
                WHERE schemaname NOT IN ('pg_catalog', 'information_schema', 'iam')
                    AND tablename <> 'flyway_schema_history'
            )
            """
                .trimIndent()

        /** 世界スコープの対象となるテーブル（`iam` スキーマと Flyway 管理テーブルを除く全テーブル）。 */
        val TARGET_TABLES =
            """
            $TARGET_TABLES_CTE
            SELECT format('%I.%I', schemaname, tablename)
            FROM target
            ORDER BY 1
            """
                .trimIndent()

        /** 対象テーブルのうち `world_id UUID NOT NULL` を持たないもの。 */
        val TABLES_WITHOUT_WORLD_ID =
            """
            $TARGET_TABLES_CTE
            SELECT format('%I.%I', t.schemaname, t.tablename)
            FROM target t
            WHERE NOT EXISTS (
                SELECT 1
                FROM information_schema.columns c
                WHERE c.table_schema = t.schemaname
                    AND c.table_name = t.tablename
                    AND c.column_name = 'world_id'
                    AND c.data_type = 'uuid'
                    AND c.is_nullable = 'NO'
            )
            ORDER BY 1
            """
                .trimIndent()

        /**
         * 対象テーブルのうち `world_id` の書き換えを拒否するトリガを持たないもの（#727 / ADR-0081）。
         *
         * 照合はトリガ名ではなく**呼び出す関数名**で行う。命名規約（`trg_<table>_world_id_immutable`）に 依存させないため。`tgisinternal`
         * の除外は、制約が内部生成するトリガ（FK の実体）を数えないため。
         *
         * `tgtype` は BEFORE / ROW / UPDATE 等のビットマスクで、`1`(ROW) | `2`(BEFORE) | `16`(UPDATE) = 19。
         * ここまで見るのは、関数さえ呼べば何でもよいと読める検査にしないため（例えば AFTER INSERT に 張り替えても素通りする検査では、守りたい性質を表していない）。
         */
        val TABLES_WITHOUT_IMMUTABLE_TRIGGER =
            """
            $TARGET_TABLES_CTE
            SELECT format('%I.%I', t.schemaname, t.tablename)
            FROM target t
            WHERE NOT EXISTS (
                SELECT 1
                FROM pg_trigger tg
                    JOIN pg_class c ON c.oid = tg.tgrelid
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    JOIN pg_proc p ON p.oid = tg.tgfoid
                WHERE n.nspname = t.schemaname
                    AND c.relname = t.tablename
                    AND NOT tg.tgisinternal
                    AND p.proname = 'reject_world_id_update'
                    AND (tg.tgtype & 19) = 19
            )
            ORDER BY 1
            """
                .trimIndent()

        /**
         * 対象テーブルのうち `world_id` → `iam.world(id)` の FK を持たないもの（#905 / ADR-0067）。
         *
         * **参照先が `iam.world` であることまで条件に含める**。さもないと集約間の複合 FK（`(world_id, id)`
         * で別の集約テーブルを参照するもの。V19）を数えてしまい、`iam.world` への FK が無くても通る検査になる。
         *
         * `ON DELETE CASCADE`（`confdeltype = 'c'`）と検証済み（`convalidated`）も見る。前者は世界の削除で 配下が消えることを
         * `DeleteWorldUseCase` が前提にしているため（`NO ACTION` で張られると世界が 消せなくなる）。後者は `NOT VALID` のまま
         * VALIDATE を忘れると既存行が未検証のまま残るため （マイグレーションを 2
         * ファイルに分ける規約の後半だけが欠ける形。`.claude/rules/migrations.md`）。
         */
        val TABLES_WITHOUT_WORLD_FOREIGN_KEY =
            """
            $TARGET_TABLES_CTE
            SELECT format('%I.%I', t.schemaname, t.tablename)
            FROM target t
            WHERE NOT EXISTS (
                SELECT 1
                FROM pg_constraint con
                    JOIN pg_class c ON c.oid = con.conrelid
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    JOIN pg_attribute a
                        ON a.attrelid = con.conrelid AND a.attnum = ANY (con.conkey)
                WHERE n.nspname = t.schemaname
                    AND c.relname = t.tablename
                    AND con.contype = 'f'
                    AND con.confrelid = 'iam.world'::regclass
                    AND a.attname = 'world_id'
                    AND con.confdeltype = 'c'
                    AND con.convalidated
            )
            ORDER BY 1
            """
                .trimIndent()
        /**
         * 世界スコープのテーブルにある `〜_id` 列のうち、集約の参照ではないもの（`<schema>.<table>.<column>`）。
         *
         * - `shared.idempotency_record.resource_id`: 冪等キーが指すリソースは種類が 1 つに定まらない（ADR-0072）ため FK を張らない
         */
        val NON_REFERENCE_ID_COLUMNS = listOf("shared.idempotency_record.resource_id")

        /**
         * 参照元・参照先がどちらも世界スコープ（[TARGET_TABLES_CTE] の対象）である FK。
         *
         * `iam.world` への FK や世界スコープ外への参照はここに入らない。#911 が「単一列 FK の禁止」案で懸念した、 世界に属さない参照の巻き込みを避けるため。
         */
        val TARGET_FOREIGN_KEYS_CTE =
            """
            $TARGET_TABLES_CTE,
            fk AS (
                SELECT con.oid, con.conname, con.conrelid, con.confrelid, con.conkey, con.confkey,
                    con.convalidated
                FROM pg_constraint con
                    JOIN target src
                        ON con.conrelid = format('%I.%I', src.schemaname, src.tablename)::regclass
                    JOIN target dst
                        ON con.confrelid = format('%I.%I', dst.schemaname, dst.tablename)::regclass
                WHERE con.contype = 'f'
            ),
            paired AS (
                SELECT fk.*
                FROM fk
                WHERE fk.convalidated
                    AND EXISTS (
                        SELECT 1
                        FROM unnest(fk.conkey, fk.confkey) AS k (src_attnum, dst_attnum)
                            JOIN pg_attribute sa
                                ON sa.attrelid = fk.conrelid AND sa.attnum = k.src_attnum
                            JOIN pg_attribute da
                                ON da.attrelid = fk.confrelid AND da.attnum = k.dst_attnum
                        WHERE sa.attname = 'world_id' AND da.attname = 'world_id'
                    )
            )
            """
                .trimIndent()

        /** 世界スコープのテーブル同士の FK（列挙の空振り検知用）。 */
        val TARGET_FOREIGN_KEYS =
            """
            $TARGET_FOREIGN_KEYS_CTE
            SELECT format('%s.%s', fk.conrelid::regclass, fk.conname)
            FROM fk
            ORDER BY 1
            """
                .trimIndent()

        /**
         * 世界スコープのテーブル同士の FK のうち、`world_id` 同士を対応させていないもの、または未検証のもの（#911）。
         *
         * 対応は `conkey` と `confkey` を**位置で**突き合わせて見る。列の集合に `world_id` が含まれるだけでは足りず、 参照元の `world_id`
         * が参照先の `world_id` に対応していなければ、世界をまたぐ参照を防げない。
         */
        val FOREIGN_KEYS_NOT_PAIRING_WORLD_ID =
            """
            $TARGET_FOREIGN_KEYS_CTE
            SELECT format('%s.%s', fk.conrelid::regclass, fk.conname)
            FROM fk
            WHERE fk.oid NOT IN (SELECT oid FROM paired)
            ORDER BY 1
            """
                .trimIndent()

        /** 世界スコープのテーブルにある `〜_id` 列（`world_id` と [NON_REFERENCE_ID_COLUMNS] を除く）。 */
        val TARGET_ID_COLUMNS_CTE =
            """
            $TARGET_FOREIGN_KEYS_CTE,
            id_column AS (
                SELECT format('%I.%I', t.schemaname, t.tablename)::regclass AS relid,
                    format('%I.%I.%I', t.schemaname, t.tablename, c.column_name) AS qualified,
                    c.column_name
                FROM target t
                    JOIN information_schema.columns c
                        ON c.table_schema = t.schemaname AND c.table_name = t.tablename
                WHERE c.column_name LIKE '%\_id' ESCAPE '\'
                    AND c.column_name <> 'world_id'
                    AND format('%I.%I.%I', t.schemaname, t.tablename, c.column_name)
                        NOT IN (${NON_REFERENCE_ID_COLUMNS.joinToString { "'$it'" }})
            )
            """
                .trimIndent()

        /** 検査対象の `〜_id` 列（列挙の空振り検知用）。 */
        val TARGET_ID_COLUMNS =
            """
            $TARGET_ID_COLUMNS_CTE
            SELECT qualified FROM id_column ORDER BY 1
            """
                .trimIndent()

        /** `〜_id` 列のうち、`world_id` を対応させた検証済みの複合 FK（[TARGET_FOREIGN_KEYS_CTE] の paired）に含まれないもの。 */
        val ID_COLUMNS_WITHOUT_COMPOSITE_FOREIGN_KEY =
            """
            $TARGET_ID_COLUMNS_CTE
            SELECT ic.qualified
            FROM id_column ic
            WHERE NOT EXISTS (
                SELECT 1
                FROM paired p
                    JOIN pg_attribute a ON a.attrelid = p.conrelid AND a.attnum = ANY (p.conkey)
                WHERE p.conrelid = ic.relid AND a.attname = ic.column_name
            )
            ORDER BY 1
            """
                .trimIndent()
    }
}
