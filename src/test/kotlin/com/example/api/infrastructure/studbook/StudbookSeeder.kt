package com.example.api.infrastructure.studbook

import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.infrastructure.studbook.breeding.BreedingRegistrationRow
import com.example.api.infrastructure.studbook.breeding.BreedingRegistrationSpringDataRepository
import com.example.api.infrastructure.studbook.breeding.JdbcBreedingRegistrationRepository
import com.example.api.infrastructure.studbook.horse.BloodHorseRow
import com.example.api.infrastructure.studbook.horse.BloodHorseSpringDataRepository
import com.example.api.infrastructure.studbook.horse.JdbcBloodHorseRepository
import com.example.api.infrastructure.studbook.inspection.HorseInspectionRow
import com.example.api.infrastructure.studbook.inspection.HorseInspectionSpringDataRepository
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.util.UUID

/**
 * FK backstop（ADR-0053）を満たすように、テスト対象行が参照する親行を先に永続化するテスト用シーダ。
 *
 * 集約フィクスチャ（Object Mother）は親集約をメモリ上にしか組まないため、FK 導入後はテストが 参照先を先に DB へ入れる必要がある。集約用（seedHorse /
 * seedRegistration）と、生 Row の フィクスチャ用に任意 ID の親行だけ作る row 系（seedInspectionRow / seedHorseRow /
 * seedRegistrationRow）の 2 系統を提供する。
 */
class StudbookSeeder(
    private val worldId: WorldId,
    private val inspectionRows: HorseInspectionSpringDataRepository,
    private val horseRows: BloodHorseSpringDataRepository,
    private val registrationRows: BreedingRegistrationSpringDataRepository,
) {
    /** [horse] が参照する審査行（inspection_id の親）を最小構成で永続化する。 */
    fun seedInspectionFor(horse: BloodHorse) {
        seedInspectionRow(horse.inspectionId.value)
    }

    /** 馬を審査行ごと永続化して返す（父・母・種牡馬・繁殖登録対象馬用）。 */
    fun seedHorse(horse: BloodHorse): BloodHorse {
        seedInspectionFor(horse)
        return JdbcBloodHorseRepository(horseRows).save(worldId, horse).unwrap()
    }

    /** 繁殖登録を永続化して返す。対象馬は事前に [seedHorse] しておくこと。 */
    fun seedRegistration(registration: BreedingRegistration): BreedingRegistration =
        JdbcBreedingRegistrationRepository(registrationRows).save(worldId, registration).unwrap()

    /** 任意 ID の審査行を作り ID を返す（生 Row フィクスチャの inspection_id 用）。 */
    fun seedInspectionRow(id: UUID = generateId()): UUID {
        inspectionRows.save(
            HorseInspectionRow(
                worldId = worldId.value,
                id = id,
                microchipNumber = "392140000000001",
                parentageType = "NOT_APPLICABLE",
            )
        )
        return id
    }

    /**
     * 任意 ID の馬行（輸入馬の最小構成）を審査行ごと作り ID を返す（生 Row の sire/dam・種牡馬用）。
     *
     * 登録番号の既定値は ID から導いて衝突しないようにする。同じ世界に複数の馬を seed するテストが多く、 固定値だと血統登録番号の UNIQUE（V22）に当たるため。
     */
    fun seedHorseRow(
        id: UUID = generateId(),
        sex: String = "MALE",
        registrationNumber: String = "SEED-$id",
    ): UUID {
        horseRows.save(
            BloodHorseRow(
                worldId = worldId.value,
                id = id,
                registrationNumber = registrationNumber,
                sex = sex,
                coatColor = "BAY",
                breedType = "THOROUGHBRED",
                dateOfBirth = LocalDate.of(2020, 4, 10),
                breeder = "Coolmore",
                inspectionId = seedInspectionRow(),
                originType = "IMPORTED",
                originCountry = "アイルランド",
                landingDate = LocalDate.of(2024, 9, 1),
            )
        )
        return id
    }

    /**
     * 任意 ID の繁殖登録行を対象馬・審査行ごと作り ID を返す（生 Row の breeding_registration_id 用）。
     *
     * 登録番号の既定値は ID から導いて衝突しないようにする（[seedHorseRow] と同じ理由。UNIQUE は V22）。
     */
    fun seedRegistrationRow(
        id: UUID = generateId(),
        role: String = "BROODMARE",
        registrationNumber: String = "SEED-REG-$id",
    ): UUID {
        registrationRows.save(
            BreedingRegistrationRow(
                worldId = worldId.value,
                id = id,
                registrationNumber = registrationNumber,
                registeredHorseId =
                    seedHorseRow(sex = if (role == "STALLION") "MALE" else "FEMALE"),
                breedingRole = role,
            )
        )
        return id
    }
}
