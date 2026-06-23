package com.example.api.domain.horseracing.service.breeding

import com.example.api.domain.horseracing.model.breeding.BreedingRegistration
import com.example.api.domain.horseracing.model.breeding.BreedingResult
import com.example.api.domain.horseracing.model.breeding.CoveringCertificateNumber
import com.example.api.domain.horseracing.model.breeding.RecordCoveringError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import java.time.LocalDate
import java.time.Year

/**
 * 種付（配合）を記録し、繁殖成績の年次レコード（[BreedingResult]）を起こすドメインサービス。
 *
 * 種付記録の前提条件は2系統あり、性質が異なるため担い手を分ける:
 * - **配合の登録ロール（繁殖牝馬 × 種牡馬）** … 単一の繁殖成績インスタンスの構築時不変条件。委譲先のファクトリ [BreedingResult.create]
 *   が自己検証する（NotBroodmare / NotStallion の [RecordCoveringError]）。
 * - **「繁殖牝馬 × 繁殖年」で一意（同一年の重複記録の禁止）** … 既存成績群（集合）をまたぐ集合制約で、単一インスタンスの
 *   構築では完結しない。本サービスが検証する（[RecordCoveringError.AlreadyRecordedForYear]）。
 *
 * 一意性の検証に要する「同一繁殖牝馬・同一繁殖年の既存成績」の引き当て（リポジトリ参照＝coordination）は アプリケーション層の責務であり、その結果を [existingForYear]
 * で受け取る（ドメインサービスはリポジトリに依存しない）。 重複が無ければファクトリへ委譲して年次レコードを生成する。生成直後は分娩結果が未報告（`outcome` は null）。
 *
 * @param broodmareRegistration 種付対象の繁殖牝馬の繁殖登録（ロールが繁殖牝馬であること）
 * @param stallionRegistration 配合相手の種牡馬の繁殖登録（ロールが種牡馬であること）
 * @param coveringDate 種付日
 * @param certificateNumber 種付の事実を証明する種付証明書の番号
 * @param existingForYear 同一繁殖牝馬・同一繁殖年に既に存在する年次成績。アプリケーション層が引き当てて渡す。 重複が無ければ null
 * @return 起こされた [BreedingResult]、または前提条件違反を表す [RecordCoveringError]
 */
fun recordCovering(
    broodmareRegistration: BreedingRegistration,
    stallionRegistration: BreedingRegistration,
    coveringDate: LocalDate,
    certificateNumber: CoveringCertificateNumber,
    existingForYear: BreedingResult?,
): Result<BreedingResult, RecordCoveringError> {
    if (existingForYear != null) {
        return Err(
            RecordCoveringError.AlreadyRecordedForYear(
                Year.of(coveringDate.year),
                existingForYear.id,
            )
        )
    }
    return BreedingResult.create(
        broodmareRegistration,
        stallionRegistration,
        coveringDate,
        certificateNumber,
    )
}
