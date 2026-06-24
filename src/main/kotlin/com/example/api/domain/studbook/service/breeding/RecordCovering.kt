package com.example.api.domain.studbook.service.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRegion
import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.CoveringCertificateNumber
import com.example.api.domain.studbook.model.breeding.RecordCoveringError
import com.example.api.domain.studbook.model.breeding.StudCertificate
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import java.time.LocalDate
import java.time.Year

/**
 * 種付（配合）を記録し、繁殖成績の年次レコード（[BreedingResult]）を起こすドメインサービス。
 *
 * 種付記録の前提条件は2系統あり、性質が異なるため担い手を分ける:
 * - **配合の登録ロール（繁殖牝馬 × 種牡馬）／種付の有効性（種畜証明書の有効区域・有効期間）** … いずれも単一の繁殖成績
 *   インスタンスの構築時前提条件で、協力与件（繁殖登録・種畜証明書）を引数で受け取れる。委譲先のファクトリ [BreedingResult.create]
 *   が自己検証する（NotBroodmare / NotStallion / InvalidCovering の [RecordCoveringError]）。
 * - **「繁殖牝馬 × 繁殖年」で一意（同一年の重複記録の禁止）** … 既存成績群（集合）をまたぐ集合制約で、単一インスタンスの 構築では完結しない。本サービスが
 *   [breedingResultRepository] から同年の既存成績を引き当てて検証する （[RecordCoveringError.AlreadyRecordedForYear]）。
 *
 * 一意性は永続化された成績集合に対する問い合わせが本質であるため、本サービスがリポジトリポート [breedingResultRepository] を
 * 直接受け取って引き当てる（リポジトリポートは domainModel に属するため、ドメインサービスからの依存はオニオンの 依存方向 service → model
 * に反しない）。重複が無ければファクトリへ委譲して年次レコードを生成する。生成直後は分娩結果が 未報告（`outcome` は null）。
 *
 * @param broodmareRegistration 種付対象の繁殖牝馬の繁殖登録（ロールが繁殖牝馬であること）
 * @param stallionRegistration 配合相手の種牡馬の繁殖登録（ロールが種牡馬であること）
 * @param coveringDate 種付日
 * @param certificateNumber 種付の事実を証明する種付証明書の番号
 * @param breedingResultRepository 同一繁殖牝馬・同一繁殖年の既存成績を引き当てる繁殖成績ポート
 * @param studCertificate 種牡馬の種畜証明書。種付の有効性（有効区域・有効期間）をファクトリが検証する与件
 * @param coveringPlace 種付場所（有効区域の整合検証に用いる）
 * @return 起こされた [BreedingResult]、または前提条件違反を表す [RecordCoveringError]
 */
fun recordCovering(
    broodmareRegistration: BreedingRegistration,
    stallionRegistration: BreedingRegistration,
    coveringDate: LocalDate,
    certificateNumber: CoveringCertificateNumber,
    breedingResultRepository: BreedingResultRepository,
    studCertificate: StudCertificate,
    coveringPlace: BreedingRegion,
): Result<BreedingResult, RecordCoveringError> {
    val breedingYear = Year.of(coveringDate.year)
    val existingForYear =
        breedingResultRepository.findByBreedingRegistrationIdAndBreedingYear(
            broodmareRegistration.id,
            breedingYear,
        )
    if (existingForYear != null) {
        return Err(RecordCoveringError.AlreadyRecordedForYear(breedingYear, existingForYear.id))
    }
    return BreedingResult.create(
        broodmareRegistration,
        stallionRegistration,
        coveringDate,
        certificateNumber,
        studCertificate,
        coveringPlace,
    )
}
