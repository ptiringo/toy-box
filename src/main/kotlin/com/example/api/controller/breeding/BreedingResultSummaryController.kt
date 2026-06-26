package com.example.api.controller.breeding

import com.example.api.application.studbook.breeding.FindBreedingResultSummaryQuery
import com.example.api.application.studbook.breeding.FindBreedingResultSummaryUseCase
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import java.util.UUID
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 繁殖成績の年次集計リソースの HTTP アダプター（軽量 CQRS / L2 の読み取り側。ADR-0031）。
 *
 * JAIRS 様式第2号（繁殖登録原簿〈雄〉）に対応し、ある種牡馬の (種付年) ごとの繁殖成績（種付雌馬数・受胎率・ 生産率）を List で返す。読み取り経路は書き込み集約を一切経由せず
 * [FindBreedingResultSummaryUseCase] が
 * [com.example.api.application.studbook.breeding.BreedingResultSummaryQueries] 経由でストアから直接組む。
 */
@RestController
class BreedingResultSummaryController(
    private val findBreedingResultSummary: FindBreedingResultSummaryUseCase
) {
    @Operation(
        summary = "種牡馬の繁殖成績の年次集計を取得する",
        description =
            "指定した種牡馬について、種付年ごとの種付雌馬数・受胎数・生産数と受胎率・生産率を年昇順で返す。" +
                "該当する成績が無ければ空配列を返す。" +
                "分娩結果が未報告の年は種付雌馬数（分母）にのみ計上され、受胎率・生産率は報告済み時点までの暫定値となる。",
        tags = ["BreedingResult"],
        responses =
            [
                ApiResponse(
                    responseCode = "200",
                    description = "集計の一覧（該当なしは空配列）",
                    content =
                        [
                            Content(
                                array =
                                    ArraySchema(
                                        schema =
                                            Schema(
                                                implementation =
                                                    BreedingResultSummaryResponse::class
                                            )
                                    ),
                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                            )
                        ],
                )
            ],
    )
    @GetMapping("/api/breedingResultSummaries")
    fun list(@RequestParam stallionId: UUID): List<BreedingResultSummaryResponse> =
        findBreedingResultSummary(FindBreedingResultSummaryQuery(BloodHorseId(stallionId))).map {
            it.toResponse()
        }
}
