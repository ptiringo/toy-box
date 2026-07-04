package com.example.api.domain.studbook.service.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.CoveringReport
import com.example.api.domain.studbook.model.breeding.CoveringReportRepository
import com.example.api.domain.studbook.model.breeding.SubmitCoveringReportError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import java.time.LocalDate
import java.time.Year

/**
 * 種付成績報告書（様式第13号）の年次提出を記録し、種付成績報告（[CoveringReport]）を起こすドメインサービス。
 *
 * 提出の前提条件は2系統あり、性質が異なるため担い手を分ける:
 * - **提出対象の登録ロール（種牡馬）** … 単一の報告インスタンスの構築時前提条件で、協力与件（繁殖登録）を 引数で受け取れる。委譲先のファクトリ
 *   [CoveringReport.create] が自己検証する （[SubmitCoveringReportError.NotStallion]）。
 * - **「種牡馬 × 種付年」で提出は一度（同一年の二重提出の禁止）** … 既存報告群（集合）をまたぐ集合制約で、 単一インスタンスの構築では完結しない。本サービスが
 *   [coveringReportRepository] から同年の既存報告を
 *   引き当てて検証する（[SubmitCoveringReportError.AlreadySubmittedForYear]）。
 *
 * 一意性は永続化された報告集合に対する問い合わせが本質であるため、本サービスがリポジトリポート [coveringReportRepository]
 * を直接受け取って引き当てる（ADR-0022。リポジトリポートは domainModel に属する ため、ドメインサービスからの依存はオニオンの依存方向 service → model
 * に反しない）。重複が無ければ ファクトリへ委譲して提出記録を生成する。種付実績の有無は前提にしない（種付 0 件の年の提出も受理する。#540）。
 *
 * @param stallionRegistration 提出する種牡馬の繁殖登録（ロールが種牡馬であること）
 * @param coveringYear 報告対象の種付年
 * @param submittedOn 提出日（日本の暦日）
 * @param coveringReportRepository 同一種牡馬・同一種付年の既存報告を引き当てる種付成績報告ポート
 * @return 起こされた [CoveringReport]、または前提条件違反を表す [SubmitCoveringReportError]
 */
fun submitCoveringReport(
    stallionRegistration: BreedingRegistration,
    coveringYear: Year,
    submittedOn: LocalDate,
    coveringReportRepository: CoveringReportRepository,
): Result<CoveringReport, SubmitCoveringReportError> {
    val existingForYear =
        coveringReportRepository.findByStallionRegistrationIdAndCoveringYear(
            stallionRegistration.id,
            coveringYear,
        )
    if (existingForYear != null) {
        return Err(
            SubmitCoveringReportError.AlreadySubmittedForYear(coveringYear, existingForYear.id)
        )
    }
    return CoveringReport.create(stallionRegistration, coveringYear, submittedOn)
}
