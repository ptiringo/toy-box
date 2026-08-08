package com.example.api.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.jupiter.api.Test

/**
 * [ActorScopedUseCase] のルール挙動を検証する。
 *
 * 違反を検出することと、適合・対象外（iam / 他層 / invoke 以外）を検出しないことの両方を 能動的に確かめる（#706 の完了条件）。
 */
class ActorScopedUseCaseTest {
    private val rule = ActorScopedUseCase(TestConfig())

    @Test
    fun `Actor を第1引数に取らない書き込みユースケースを検出すること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.horse

                class RegisterFoalUseCase {
                    operator fun invoke(command: Command<RegisterFoalCommand>): Unit = Unit
                }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `Actor を第1引数に取らない読み取りユースケースを検出すること`() {
        // 読み取りのスコープ漏れは DB が守らないため、読みも対象に含める（#706）。
        val findings =
            rule.lint(
                """
                package com.example.api.application.racing.jockey

                class GetJockeyUseCase {
                    operator fun invoke(query: GetJockeyQuery): JockeyView? = null
                }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `引数を取らないユースケースを検出すること`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.horse

                class ListBloodHorsesUseCase {
                    operator fun invoke(): List<BloodHorseView> = emptyList()
                }
                """
                    .trimIndent()
            )

        assert(findings.size == 1)
    }

    @Test
    fun `Actor を第1引数に取るユースケースは検出しないこと`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.horse

                class RegisterFoalUseCase {
                    operator fun invoke(
                        actor: Actor,
                        command: Command<RegisterFoalCommand>,
                    ): Unit = Unit
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `完全修飾で書かれた Actor も適合とみなすこと`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.racing.jockey

                class GetJockeyUseCase {
                    operator fun invoke(
                        actor: com.example.api.domain.shared.Actor,
                        query: GetJockeyQuery,
                    ): JockeyView? = null
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `iam コンテキストのユースケースは検出しないこと`() {
        // 世界を作る操作にはまだ世界が無く、スコープは AccountId で表す（ADR-0067）。
        val findings =
            rule.lint(
                """
                package com.example.api.application.iam.world

                class CreateWorldUseCase {
                    operator fun invoke(
                        accountId: AccountId,
                        command: Command<CreateWorldCommand>,
                    ): Unit = Unit
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `application 以外の層の invoke は検出しないこと`() {
        val findings =
            rule.lint(
                """
                package com.example.api.controller.studbook

                class HorseRequestMapper {
                    operator fun invoke(request: RegisterFoalRequest): RegisterFoalCommand =
                        RegisterFoalCommand(request)
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }

    @Test
    fun `invoke 以外の関数は検出しないこと`() {
        val findings =
            rule.lint(
                """
                package com.example.api.application.studbook.horse

                class RegisterFoalUseCase {
                    private fun toCommand(request: String): String = request
                }
                """
                    .trimIndent()
            )

        assert(findings.isEmpty())
    }
}
