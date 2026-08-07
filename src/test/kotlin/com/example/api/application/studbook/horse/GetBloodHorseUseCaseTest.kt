package com.example.api.application.studbook.horse

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.LandingDate
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** 世界スコープ（#704）のテスト用フィクスチャ。ネストしたテストクラスからも参照できるようファイル直下に置く。 */
private val worldId = WorldId(generateId())
private val actor = Actor(accountId = AccountId(generateId()), worldId = worldId)

/**
 * 照会ユースケース [GetBloodHorseUseCase] の単体テスト（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 読み取りポート [BloodHorseQueries] を mockk でスタブし、ヒット時は [BloodHorseDetailView] を、不在時は
 * [BloodHorseNotFound] を返す分岐を検証する（testing.md: applicationService はポート境界をモックする）。
 */
class GetBloodHorseUseCaseTest {
    private val bloodHorseQueries = mockk<BloodHorseQueries>()
    private val getBloodHorse = GetBloodHorseUseCase(bloodHorseQueries)

    @Test
    fun `存在するIDなら対応するBloodHorseDetailViewをOkで返す`() {
        val id = generateId()
        val view =
            BloodHorseDetailView(
                id = id,
                registrationNumber = "2020900001",
                sex = Sex.MALE,
                coatColor = CoatColor.BAY,
                breedType = BreedType.THOROUGHBRED,
                dateOfBirth = LocalDate.of(2020, 4, 10),
                breeder = "Coolmore",
                microchipNumber = "392140000000001",
                origin =
                    Origin.Imported(
                        originCountry = OriginCountry.create("アイルランド").unwrap(),
                        landingDate = LandingDate(LocalDate.of(2024, 9, 1)),
                    ),
                name = null,
            )
        every { bloodHorseQueries.findById(worldId, BloodHorseId(id)) } returns view

        val result = getBloodHorse(actor, GetBloodHorseQuery(id))

        assert(result.get() == view)
    }

    @Test
    fun `存在しないIDならBloodHorseNotFoundをErrで返す`() {
        val id = generateId()
        every { bloodHorseQueries.findById(worldId, BloodHorseId(id)) } returns null

        val result = getBloodHorse(actor, GetBloodHorseQuery(id))

        assert(result.getError() == BloodHorseNotFound(id))
    }
}
