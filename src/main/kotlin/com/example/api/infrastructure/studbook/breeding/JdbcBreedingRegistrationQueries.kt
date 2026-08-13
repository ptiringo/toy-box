package com.example.api.infrastructure.studbook.breeding

import com.example.api.application.studbook.breeding.BreedingRegistrationDetailView
import com.example.api.application.studbook.breeding.BreedingRegistrationQueries
import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRetirement
import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.breeding.RetirementReason
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 読み取りポート [BreedingRegistrationQueries] の実装（軽量 CQRS / L2 の Query 側。ADR-0031）。
 *
 * 書き込み側の [JdbcBreedingRegistrationRepository]（集約を `BreedingRegistrationRow` 経由で復元する）とは
 * 別経路として、`studbook.breeding_registration` を [JdbcClient] で直接 SELECT し、集約を組まずに平坦な
 * [BreedingRegistrationDetailView] へ詰める。ロール列は enum 名文字列を `valueOf` でドメイン enum へ戻す。
 */
@Repository
class JdbcBreedingRegistrationQueries(private val jdbcClient: JdbcClient) :
    BreedingRegistrationQueries {

    override fun findById(
        worldId: WorldId,
        id: BreedingRegistrationId,
    ): BreedingRegistrationDetailView? =
        jdbcClient
            .sql(
                """
                SELECT
                    id, registration_number, registered_horse_id, breeding_role,
                    retirement_reason, retirement_occurred_on
                FROM studbook.breeding_registration
                WHERE id = :id AND world_id = :worldId
                """
                    .trimIndent()
            )
            .param("id", id.value)
            .param("worldId", worldId.value)
            .query { rs, _ ->
                BreedingRegistrationDetailView(
                    id = rs.getObject("id", UUID::class.java),
                    registrationNumber = rs.getString("registration_number"),
                    registeredHorseId = rs.getObject("registered_horse_id", UUID::class.java),
                    role = BreedingRole.valueOf(rs.getString("breeding_role")),
                    retirement = rs.toRetirement(),
                )
            }
            .optional()
            .orElse(null)

    /**
     * 共在する 2 列（事由・発生日）から nullable な [BreedingRetirement] を復元する。
     *
     * 両列は「全 NULL（供用中）／全 NOT NULL（供用停止済み）」で在不在を表す（CHECK 制約 `chk_breeding_registration_retirement`
     * がスキーマ側でも強制している。V6）。片寄せの壊れ行に当たったときに どの登録かを残すため、書き込み側
     * （[JdbcBreedingRegistrationRepository]）と対称に診断メッセージつきの `checkNotNull` で受ける。
     */
    private fun ResultSet.toRetirement(): BreedingRetirement? {
        // 列は先に取り出す。checkNotNull の呼び出しごと 1 行に収め、診断メッセージのラムダが単独行に
        // 折られない形にしている（壊れ行でしか通らない行を独立させると常に未カバーになるため）。
        val id = getObject("id", UUID::class.java)
        val occurredOn = getObject("retirement_occurred_on", LocalDate::class.java)
        return getString("retirement_reason")?.let { reason ->
            BreedingRetirement(
                RetirementReason.valueOf(reason),
                checkNotNull(occurredOn) { "供用停止事由があるのに発生日が欠落: id=$id" },
            )
        }
    }
}
