package com.example.api.domain.horseracing.service.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingRegistration
import com.example.api.domain.horseracing.model.breeding.BreedingResult
import com.example.api.domain.horseracing.model.breeding.Covering
import com.example.api.domain.horseracing.model.breeding.CoveringCertificateNumber
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.LocalDate

/**
 * 繁殖牝馬に対するその年の種付を記録し、繁殖成績（[BreedingResult]）の年次レコードを起こす。
 *
 * 繁殖登録（[BreedingRegistration]）により対象が繁殖牝馬であることは担保される。本サービスは集約をまたぐ前提条件 として、配合相手の種牡馬が雄であることを検証してから
 * [BreedingResult] を生成する。種牡馬は別個体（別集約） であり、生成物は種牡馬を ID（`BloodHorseId`）経由で参照する。
 *
 * @param breedingRegistration 種付対象の繁殖牝馬の繁殖登録
 * @param stallion 配合相手の種牡馬（雄の [BloodHorse]）
 * @param coveringDate 種付日
 * @param certificateNumber 種付の事実を証明する種付証明書の番号
 * @return 種付を記録した [BreedingResult]、または前提条件違反を表す [RecordCoveringError]
 */
fun recordCovering(
    breedingRegistration: BreedingRegistration,
    stallion: BloodHorse,
    coveringDate: LocalDate,
    certificateNumber: CoveringCertificateNumber,
): Result<BreedingResult, RecordCoveringError> =
    if (stallion.sex != Sex.MALE) {
        Err(RecordCoveringError.StallionNotMale)
    } else {
        Ok(
            BreedingResult.of(
                breedingRegistrationId = breedingRegistration.id,
                covering = Covering(stallion.id, coveringDate, certificateNumber),
            )
        )
    }

/**
 * 種付記録の前提条件違反。
 *
 * 現時点では種牡馬が雄であることのみを検証するが、制度上は他の前提条件もありうる（例: 同一種付年の重複記録の禁止、 種牡馬の種牡馬登録の確認）。集約が揃い次第バリアントを追加できるよう
 * sealed interface としておく。
 */
sealed interface RecordCoveringError {
    /** 配合相手は種牡馬（雄）に限られるが、指定された馬が雄でない。 */
    data object StallionNotMale : RecordCoveringError
}
