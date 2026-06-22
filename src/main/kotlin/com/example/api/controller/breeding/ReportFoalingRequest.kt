package com.example.api.controller.breeding

import com.example.api.controller.problem
import com.example.api.domain.horseracing.model.breeding.FoalingOutcome
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.LocalDate
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * `POST /api/breedingResults/{breedingResultId}:reportFoaling` のリクエストボディ。
 *
 * 繁殖成績報告書の「分娩結果」欄に相当する。区分は HTTP 契約専用の [FoalingOutcomeDto] で受け取り（未知の値は Jackson のデシリアライズで弾かれ
 * 400）、ドメインの [FoalingOutcome] へは [toOutcome] で変換する。生産
 * （[FoalingOutcomeDto.LIVE_FOAL]）のみ分娩日を伴うため、区分と分娩日の整合は [toOutcome] が検証する。
 *
 * @property outcome 分娩結果の区分
 * @property foalingDate 分娩日。生産のときは必須、それ以外は無視される
 */
data class ReportFoalingRequest(val outcome: FoalingOutcomeDto, val foalingDate: LocalDate?)

/**
 * リクエストボディをドメインの分娩結果へ変換する。
 *
 * 生産（[FoalingOutcomeDto.LIVE_FOAL]）は分娩日が必須であり、欠けている場合は入力不正として 400 を表す [ProblemDetail]
 * を返す。産駒なしの各区分は分娩日を持たない（指定されても無視する）。種付せず （[FoalingOutcomeDto.NOT_COVERED]）は種付を伴わない年次成績の区分であり分娩結果報告
 * （`:reportFoaling`）では 受け付けない（別経路で記録する）。入力不正として 400 を返す。
 */
fun ReportFoalingRequest.toOutcome(): Result<FoalingOutcome, ProblemDetail> =
    when (outcome) {
        FoalingOutcomeDto.NOT_COVERED ->
            Err(
                problem(
                    status = HttpStatus.BAD_REQUEST,
                    code = "foaling-outcome-not-reportable",
                    title = "Foaling outcome not reportable",
                    detail = "種付せず（NOT_COVERED）は分娩結果として報告できません。",
                )
            )
        FoalingOutcomeDto.LIVE_FOAL ->
            if (foalingDate == null) {
                Err(
                    problem(
                        status = HttpStatus.BAD_REQUEST,
                        code = "missing-foaling-date",
                        title = "Missing foaling date",
                        detail = "生産（LIVE_FOAL）のときは foaling_date が必須です。",
                    )
                )
            } else {
                Ok(FoalingOutcome.LiveFoal(foalingDate))
            }
        FoalingOutcomeDto.NOT_CONCEIVED -> Ok(FoalingOutcome.NotConceived)
        FoalingOutcomeDto.ABORTION -> Ok(FoalingOutcome.Abortion)
        FoalingOutcomeDto.TWIN_ABORTION -> Ok(FoalingOutcome.TwinAbortion)
        FoalingOutcomeDto.STILLBIRTH -> Ok(FoalingOutcome.Stillbirth)
        FoalingOutcomeDto.TWIN_STILLBIRTH -> Ok(FoalingOutcome.TwinStillbirth)
        FoalingOutcomeDto.NEONATAL_DEATH -> Ok(FoalingOutcome.NeonatalDeath)
        FoalingOutcomeDto.TWIN_NEONATAL_DEATH -> Ok(FoalingOutcome.TwinNeonatalDeath)
    }
