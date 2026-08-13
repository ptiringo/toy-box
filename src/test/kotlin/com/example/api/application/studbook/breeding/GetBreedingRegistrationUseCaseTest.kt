package com.example.api.application.studbook.breeding

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingRegistrationId
import com.example.api.domain.studbook.model.breeding.BreedingRetirement
import com.example.api.domain.studbook.model.breeding.BreedingRole
import com.example.api.domain.studbook.model.breeding.RetirementReason
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

/**
 * 照会ユースケース [GetBreedingRegistrationUseCase] の単体テスト（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 読み取りポート [BreedingRegistrationQueries] を mockk でスタブし、ヒット時は [BreedingRegistrationDetailView] を、
 * 不在時は [BreedingRegistrationNotFound] を返す分岐を検証する（testing.md: applicationService はポート境界をモックする）。
 */
class GetBreedingRegistrationUseCaseTest {
    private val breedingRegistrationQueries = mockk<BreedingRegistrationQueries>()
    private val getBreedingRegistration =
        GetBreedingRegistrationUseCase(breedingRegistrationQueries)

    @Test
    fun `存在するIDなら対応するBreedingRegistrationDetailViewをOkで返す`() {
        val id = generateId()
        val view =
            BreedingRegistrationDetailView(
                id = id,
                registrationNumber = "B-2024-0001",
                registeredHorseId = generateId(),
                role = BreedingRole.BROODMARE,
                retirement = null,
            )
        every { breedingRegistrationQueries.findById(worldId, BreedingRegistrationId(id)) } returns
            view

        val result = getBreedingRegistration(actor, GetBreedingRegistrationQuery(id))

        assert(result.get() == view)
    }

    @Test
    fun `供用停止済みなら供用停止の事由と発生日を持つビューを返す`() {
        val id = generateId()
        val view =
            BreedingRegistrationDetailView(
                id = id,
                registrationNumber = "B-2024-0001",
                registeredHorseId = generateId(),
                role = BreedingRole.STALLION,
                retirement = BreedingRetirement(RetirementReason.DEATH, LocalDate.of(2025, 3, 1)),
            )
        every { breedingRegistrationQueries.findById(worldId, BreedingRegistrationId(id)) } returns
            view

        val result = getBreedingRegistration(actor, GetBreedingRegistrationQuery(id))

        assert(result.get()?.retirement?.reason == RetirementReason.DEATH)
    }

    @Test
    fun `存在しないIDならBreedingRegistrationNotFoundをErrで返す`() {
        val id = generateId()
        every { breedingRegistrationQueries.findById(worldId, BreedingRegistrationId(id)) } returns
            null

        val result = getBreedingRegistration(actor, GetBreedingRegistrationQuery(id))

        assert(result.getError() == BreedingRegistrationNotFound(id))
    }
}
