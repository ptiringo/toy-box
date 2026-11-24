package com.example.api.controller.breeding

import com.example.api.controller.breeding.request.ReportFoalingRequest
import com.example.api.controller.breeding.request.toOutcome
import com.example.api.domain.studbook.model.breeding.FoalingOutcome
import com.example.api.domain.studbook.model.breeding.TwinFoal
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** 分娩結果（[FoalingOutcome]）の wire ↔ domain マッピングのユニットテスト。全区分を網羅する。 */
class FoalingOutcomeWireTest {

    @Test
    fun `ドメインの全区分が wire 表現へ変換でき生産のみ分娩日を伴うこと`() {
        val foalingDate = LocalDate.of(2025, 3, 20)
        val cases: List<Pair<FoalingOutcome, FoalingOutcomeResponse>> =
            listOf(
                FoalingOutcome.LiveFoal(foalingDate) to
                    FoalingOutcomeResponse(FoalingOutcomeDto.LIVE_FOAL, foalingDate),
                FoalingOutcome.NotConceived to
                    FoalingOutcomeResponse(FoalingOutcomeDto.NOT_CONCEIVED, null),
                FoalingOutcome.Abortion to FoalingOutcomeResponse(FoalingOutcomeDto.ABORTION, null),
                FoalingOutcome.TwinAbortion to
                    FoalingOutcomeResponse(FoalingOutcomeDto.TWIN_ABORTION, null),
                FoalingOutcome.Stillbirth to
                    FoalingOutcomeResponse(FoalingOutcomeDto.STILLBIRTH, null),
                FoalingOutcome.TwinStillbirth to
                    FoalingOutcomeResponse(FoalingOutcomeDto.TWIN_STILLBIRTH, null),
                FoalingOutcome.NeonatalDeath to
                    FoalingOutcomeResponse(FoalingOutcomeDto.NEONATAL_DEATH, null),
                FoalingOutcome.TwinNeonatalDeath to
                    FoalingOutcomeResponse(FoalingOutcomeDto.TWIN_NEONATAL_DEATH, null),
                FoalingOutcome.NotCovered to
                    FoalingOutcomeResponse(FoalingOutcomeDto.NOT_COVERED, null),
            )

        cases.forEach { (domain, expected) -> assert(domain.toResponse() == expected) }
    }

    @Test
    fun `wire の全区分がドメインへ変換でき産駒なしは分娩日を無視すること`() {
        val foalingDate = LocalDate.of(2025, 3, 20)
        val cases: List<Pair<FoalingOutcomeDto, FoalingOutcome>> =
            listOf(
                FoalingOutcomeDto.LIVE_FOAL to FoalingOutcome.LiveFoal(foalingDate),
                FoalingOutcomeDto.NOT_CONCEIVED to FoalingOutcome.NotConceived,
                FoalingOutcomeDto.ABORTION to FoalingOutcome.Abortion,
                FoalingOutcomeDto.TWIN_ABORTION to FoalingOutcome.TwinAbortion,
                FoalingOutcomeDto.STILLBIRTH to FoalingOutcome.Stillbirth,
                FoalingOutcomeDto.TWIN_STILLBIRTH to FoalingOutcome.TwinStillbirth,
                FoalingOutcomeDto.NEONATAL_DEATH to FoalingOutcome.NeonatalDeath,
                FoalingOutcomeDto.TWIN_NEONATAL_DEATH to FoalingOutcome.TwinNeonatalDeath,
            )

        cases.forEach { (dto, expected) ->
            val outcome = ReportFoalingRequest(dto, foalingDate).toOutcome().get()
            assert(outcome == expected)
        }
    }

    @Test
    fun `生産で分娩日が欠けていると入力不正の ProblemDetail を返すこと`() {
        val problem = ReportFoalingRequest(FoalingOutcomeDto.LIVE_FOAL, null).toOutcome().getError()

        assert(problem != null)
        assert(problem?.properties?.get("error_code") == "missing-foaling-date")
    }

    @Test
    fun `種付せずは分娩結果報告では受け付けず入力不正の ProblemDetail を返すこと`() {
        val problem =
            ReportFoalingRequest(FoalingOutcomeDto.NOT_COVERED, null).toOutcome().getError()

        assert(problem != null)
        assert(problem?.properties?.get("error_code") == "foaling-outcome-not-reportable")
    }

    @Test
    fun `双子の生産は twin_foaling_date を返す`() {
        val outcome =
            FoalingOutcome.LiveFoal(LocalDate.of(1991, 3, 20), TwinFoal(LocalDate.of(1991, 3, 21)))

        val response = outcome.toResponse()

        assert(response.twinFoalingDate == LocalDate.of(1991, 3, 21))
        assert(response.foalingDate == LocalDate.of(1991, 3, 20))
    }

    @Test
    fun `もう一頭の分娩日を伴う生産の報告は双子の LiveFoal になる`() {
        val request =
            ReportFoalingRequest(
                FoalingOutcomeDto.LIVE_FOAL,
                LocalDate.of(1991, 3, 20),
                twinFoalingDate = LocalDate.of(1991, 3, 21),
            )

        val twin = TwinFoal(LocalDate.of(1991, 3, 21))
        assert(
            request.toOutcome().get() == FoalingOutcome.LiveFoal(LocalDate.of(1991, 3, 20), twin),
        )
    }

    @Test
    fun `生産以外でもう一頭の分娩日を送ると unexpected-twin-foaling-date`() {
        val request =
            ReportFoalingRequest(
                FoalingOutcomeDto.STILLBIRTH,
                null,
                twinFoalingDate = LocalDate.of(1991, 3, 21),
            )

        val problem = request.toOutcome().getError()!!

        assert(problem.status == 400)
        assert(problem.properties?.get("error_code") == "unexpected-twin-foaling-date")
    }
}
