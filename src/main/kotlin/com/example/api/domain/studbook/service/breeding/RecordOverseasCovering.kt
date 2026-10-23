package com.example.api.domain.studbook.service.breeding

import com.example.api.domain.shared.WorldId
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.ForeignCoveringProofNumber
import com.example.api.domain.studbook.model.breeding.RecordOverseasCoveringError
import com.example.api.domain.studbook.model.horse.bloodhorse.ForeignSire
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import java.time.LocalDate
import java.time.Year

/**
 * 海外種付（外国で外国種牡馬と行われた種付）を記録し、繁殖成績の年次レコード（[BreedingResult]）を起こすドメインサービス。
 *
 * 持込馬の経路の入口。前提条件の担い手は国内種付の recordCovering と同じく 2 つに分かれ、登録ロール（繁殖牝馬）は ファクトリ
 * [BreedingResult.createOverseas] が、「繁殖牝馬 × 繁殖年」の一意性は本サービスが [breedingResultRepository]
 * から同年の既存成績を引き当てて検証する（[RecordOverseasCoveringError.AlreadyRecordedForYear]。ADR-0022）。
 *
 * @param broodmareRegistration 種付対象の繁殖牝馬の繁殖登録（ロールが繁殖牝馬であること）
 * @param sire 外国種牡馬
 * @param coveringDate 種付日
 * @param coveringCountry 種付が行われた国
 * @param proofNumber 種付国の血統登録機関が発行した種付事実の証明の番号
 * @param breedingResultRepository 同一繁殖牝馬・同一繁殖年の既存成績を引き当てる繁殖成績ポート
 * @return 起こされた [BreedingResult]、または前提条件違反を表す [RecordOverseasCoveringError]
 */
fun recordOverseasCovering(
    worldId: WorldId,
    broodmareRegistration: BreedingRegistration,
    sire: ForeignSire,
    coveringDate: LocalDate,
    coveringCountry: OriginCountry,
    proofNumber: ForeignCoveringProofNumber,
    breedingResultRepository: BreedingResultRepository,
): Result<BreedingResult, RecordOverseasCoveringError> {
    val breedingYear = Year.of(coveringDate.year)
    val existing =
        findExistingForYear(worldId, broodmareRegistration, breedingYear, breedingResultRepository)
    if (existing != null) {
        return Err(RecordOverseasCoveringError.AlreadyRecordedForYear(breedingYear, existing.id))
    }
    return BreedingResult.createOverseas(
        broodmareRegistration,
        sire,
        coveringDate,
        coveringCountry,
        proofNumber,
    )
}
