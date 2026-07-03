package com.example.api.controller.inspection.problem

import com.example.api.application.studbook.inspection.HorseInspectionNotFound
import com.example.api.controller.problem
import com.example.api.domain.studbook.model.inspection.InvalidMicrochipNumber
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 審査リソースの業務エラーを RFC 9457 (`application/problem+json`) の [ProblemDetail] へ変換するマッパー群。
 *
 * どのエラーをどの `status` / `errorCode` に描画するかの方針をここ（adapter 層の `problem/` パッケージ）へ 集約する。
 */

/**
 * [InvalidMicrochipNumber]（マイクロチップ形式不正）を 400 Bad Request の [ProblemDetail] に変換する。
 *
 * VO 検証エラー（形式不正）は入力不正として 400 とする（api-design.md）。`errorCode` は血統登録の同種エラー
 * （`BloodHorseProblem`）と同一文言を踏襲する。
 */
fun InvalidMicrochipNumber.toProblemDetail(): ProblemDetail =
    problem(
        status = HttpStatus.BAD_REQUEST,
        code = "invalid-microchip-number",
        title = "Invalid microchip number",
        detail = "microchip_number は 15 桁の数字でなければなりません。",
    )

/**
 * [HorseInspectionNotFound]（照会対象の審査不在）を 404 Not Found の [ProblemDetail] に変換する。
 *
 * URL パス（`/api/horseInspections/{id}`）で識別される操作対象そのものが無いため 404 とする（api-design.md 「リソース不在のステータス（404
 * vs 422）」）。馬名登録のボディ内参照先不在（422 `inspection-not-found`）とは 発生箇所も意味も異なるため、`errorCode` を分ける。
 */
fun HorseInspectionNotFound.toProblemDetail(): ProblemDetail =
    problem(
            status = HttpStatus.NOT_FOUND,
            code = "horse-inspection-not-found",
            title = "Horse inspection not found",
            detail = "指定された ID の審査は存在しません。",
        )
        .apply { setProperty("inspection_id", id) }
