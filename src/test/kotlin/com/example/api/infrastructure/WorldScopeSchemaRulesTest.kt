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
 * 列の存在だけでなく `NOT NULL` まで見る。列を足して制約を忘れると（V18 の途中段階で止まると）、 行がどの世界にも属さないまま入れてしまうため。複合 FK の有無や
 * `world_id` を書き換える UPDATE の 拒否（#727）は対象外。
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
    fun `検査対象のテーブルを列挙できている`() {
        // 列挙条件を誤ると「対象なし＝違反なし」で静かに通る。空振りをここで落とす
        // （PostgresContainerSupport.truncateAllTables が同じ理由で件数を検査しているのと同じ思想）。
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
    }
}
