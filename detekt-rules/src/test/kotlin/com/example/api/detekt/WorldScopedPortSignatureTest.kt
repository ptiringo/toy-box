package com.example.api.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.jupiter.api.Test

/**
 * [WorldScopedPortSignature] のルール挙動を検証する。
 *
 * ゲートは「書いたのに何も検査していない」偽陰性が最も危険なので、違反を検出することと 適合を検出しないことの両方を能動的に確かめる（#706 の完了条件）。
 */
class WorldScopedPortSignatureTest {
    private val rule = WorldScopedPortSignature(TestConfig())

    @Test
    fun `WorldId を第1引数に取らないリポジトリポートを検出すること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.domain.racing.model.jockey

                @Repository
                interface JockeyRepository {
                    fun findById(id: JockeyId): Jockey?
                }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `WorldId を第1引数に取るリポジトリポートは検出しないこと`() {
        val findings =
            rule.lint(
                """
                package com.example.api.domain.racing.model.jockey

                @Repository
                interface JockeyRepository {
                    fun findById(worldId: WorldId, id: JockeyId): Jockey?
                    fun save(worldId: WorldId, jockey: Jockey): Jockey
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `完全修飾で書かれた WorldId も適合とみなすこと`() {
        val findings =
            rule.lint(
                """
                package com.example.api.domain.racing.model.jockey

                @Repository
                interface JockeyRepository {
                    fun findById(
                        worldId: com.example.api.domain.shared.WorldId,
                        id: JockeyId,
                    ): Jockey?
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `引数を取らないリポジトリポートの関数を検出すること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.domain.racing.model.jockey

                @Repository
                interface JockeyRepository {
                    fun countAll(): Int
                }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `WorldId を第1引数に取らないクエリポートを検出すること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.racing.jockey

                interface JockeyQueries {
                    fun findById(id: JockeyId): JockeyView?
                }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `iam コンテキストのリポジトリポートは検出しないこと`() {
        // Account / World はテナントの根であり世界に属さない（ADR-0067）。
        val findings =
            rule.lint(
                """
                package com.example.api.domain.iam.model.account

                @Repository
                interface AccountRepository {
                    fun findBySubjectId(subjectId: SubjectId): Account?
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `iam コンテキストのクエリポートは検出しないこと`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.iam.world

                interface WorldQueries {
                    fun findAllByAccountId(accountId: AccountId): List<WorldView>
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `アノテーションなしで名前だけ Repository で終わる domain の interface も検出すること`() {
        // @Repository の付け忘れが世界スコープ契約から静かに抜けないよう、命名でも対象を拾う（#706 レビュー指摘）。
        val findings =
            rule.lint(
                """
                package com.example.api.domain.tennis.model.match

                interface MatchRepository {
                    fun findById(id: MatchId): Match?
                }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `jMolecules Repository を持たない domain の interface は対象外とすること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.domain.racing.model.jockey

                interface JockeyNamePolicy {
                    fun accepts(name: String): Boolean
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `infrastructure の Spring Data リポジトリ interface は対象外とすること`() {
        // 名前が Repository で終わるが adapter 内部の実装詳細であり、世界スコープはポート側で強制する。
        val findings =
            rule.lint(
                """
                package com.example.api.infrastructure.racing.jockey

                @Repository
                interface JockeySpringDataRepository : CrudRepository<JockeyRow, UUID> {
                    fun findByFirstName(firstName: String): JockeyRow?
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `domain の model 以外のパッケージは対象外とすること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.domain.racing.service.race

                @Repository
                interface RaceCalendar {
                    fun findById(id: RaceId): Race?
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }
}
