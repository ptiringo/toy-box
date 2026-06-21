package com.example.api.domain.horseracing.service.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingRegistration
import com.example.api.domain.horseracing.model.breeding.BreedingResult
import com.example.api.domain.horseracing.model.breeding.BreedingRole
import com.example.api.domain.horseracing.model.breeding.Covering
import com.example.api.domain.horseracing.model.breeding.CoveringCertificateNumber
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.LocalDate

/**
 * 繁殖牝馬に対するその年の種付を記録し、繁殖成績（[BreedingResult]）の年次レコードを起こす。
 *
 * 種付は「繁殖登録済みの繁殖牝馬」と「繁殖登録済みの種牡馬」の配合であり、両者が繁殖登録（[BreedingRegistration]）
 * を持つことを前提とする（種牡馬も繁殖登録の対象＝繁殖登録証明書の `性` が雄）。本サービスは集約をまたぐ前提条件として、
 * 牝側の登録ロールが繁殖牝馬・雄側の登録ロールが種牡馬であることを検証してから [BreedingResult] を生成する。 生成物は種牡馬を
 * ID（`BloodHorseId`）経由で参照する。
 *
 * @param broodmareRegistration 種付対象の繁殖牝馬の繁殖登録（ロールが繁殖牝馬であること）
 * @param stallionRegistration 配合相手の種牡馬の繁殖登録（ロールが種牡馬であること）
 * @param coveringDate 種付日
 * @param certificateNumber 種付の事実を証明する種付証明書の番号
 * @return 種付を記録した [BreedingResult]、または前提条件違反を表す [RecordCoveringError]
 */
fun recordCovering(
    broodmareRegistration: BreedingRegistration,
    stallionRegistration: BreedingRegistration,
    coveringDate: LocalDate,
    certificateNumber: CoveringCertificateNumber,
): Result<BreedingResult, RecordCoveringError> =
    when {
        broodmareRegistration.role != BreedingRole.BROODMARE ->
            Err(RecordCoveringError.NotBroodmare)
        stallionRegistration.role != BreedingRole.STALLION -> Err(RecordCoveringError.NotStallion)
        else ->
            Ok(
                BreedingResult.of(
                    breedingRegistrationId = broodmareRegistration.id,
                    covering =
                        Covering(
                            stallionRegistration.registeredHorseId,
                            coveringDate,
                            certificateNumber,
                        ),
                )
            )
    }

/**
 * 種付記録の前提条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。種付年の重複記録の禁止など 制度上の他の前提条件が判明したらバリアントを追加する。
 */
sealed interface RecordCoveringError {
    /** 種付対象の繁殖登録のロールが繁殖牝馬（BROODMARE）でない。 */
    data object NotBroodmare : RecordCoveringError

    /** 配合相手の繁殖登録のロールが種牡馬（STALLION）でない。 */
    data object NotStallion : RecordCoveringError
}
