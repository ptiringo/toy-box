package com.example.api.domain.studbook.service.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingResult
import com.example.api.domain.studbook.model.breeding.BreedingResultRepository
import com.example.api.domain.studbook.model.breeding.RecordUncoveredError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import java.time.Year

/**
 * 種付せず（種付しなかった年次成績）を記録し、繁殖成績の終端レコード（[BreedingResult]）を起こすドメインサービス。
 *
 * 種付記録（[recordCovering]）と対称で、前提条件は2系統あり性質が異なるため担い手を分ける:
 * - **登録ロール（繁殖牝馬）** … 単一の繁殖成績インスタンスの構築時不変条件。委譲先のファクトリ [BreedingResult.createUncovered]
 *   が自己検証する（[RecordUncoveredError.NotBroodmare]）。
 * - **「繁殖牝馬 × 繁殖年」で一意（同一年への重複記録の禁止）** … 既存成績群（集合）をまたぐ集合制約で、単一インスタンスの 構築では完結しない。本サービスが
 *   [breedingResultRepository] から同年の既存成績を引き当てて検証する （[RecordUncoveredError.AlreadyRecordedForYear]）。
 *
 * 繁殖成績の一意性は「種付した年・種付せずの年を問わず繁殖牝馬 × 繁殖年で年次レコード1件」であるため、種付せず側も 種付記録と同じ
 * [BreedingResultRepository.findByBreedingRegistrationIdAndBreedingYear] を引き当てる。これにより
 * 「種付せずの二重起票」も「既に種付記録がある年への種付せず併存」も同じ AlreadyRecordedForYear で弾く。一意性は
 * 永続化された成績集合に対する問い合わせが本質であるため、本サービスがリポジトリポート [breedingResultRepository] を 直接受け取って引き当てる（リポジトリポートは
 * domainModel に属するため、ドメインサービスからの依存はオニオンの 依存方向 service → model
 * に反しない。[ADR-0022]）。重複が無ければファクトリへ委譲して終端レコードを生成する。
 *
 * @param broodmareRegistration 種付せずの記録対象の繁殖牝馬の繁殖登録（ロールが繁殖牝馬であること）
 * @param breedingYear 種付しなかった繁殖年
 * @param breedingResultRepository 同一繁殖牝馬・同一繁殖年の既存成績を引き当てる繁殖成績ポート
 * @return 起こされた種付せずの [BreedingResult]、または前提条件違反を表す [RecordUncoveredError]
 */
fun recordUncovered(
    broodmareRegistration: BreedingRegistration,
    breedingYear: Year,
    breedingResultRepository: BreedingResultRepository,
): Result<BreedingResult, RecordUncoveredError> {
    val existingForYear =
        breedingResultRepository.findByBreedingRegistrationIdAndBreedingYear(
            broodmareRegistration.id,
            breedingYear,
        )
    if (existingForYear != null) {
        return Err(RecordUncoveredError.AlreadyRecordedForYear(breedingYear, existingForYear.id))
    }
    return BreedingResult.createUncovered(broodmareRegistration, breedingYear)
}
