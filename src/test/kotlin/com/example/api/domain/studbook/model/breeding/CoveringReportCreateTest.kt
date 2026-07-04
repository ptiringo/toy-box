package com.example.api.domain.studbook.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import java.time.Year
import org.junit.jupiter.api.Test

class CoveringReportCreateTest {

    @Test
    fun `種牡馬の繁殖登録に対して種付成績報告を生成できること`() {
        val stallionRegistration = BreedingFixture.stallionRegistration()

        val report =
            CoveringReport.create(
                    stallionRegistration = stallionRegistration,
                    coveringYear = Year.of(2024),
                    submittedOn = LocalDate.of(2024, 9, 30),
                )
                .unwrap()

        assert(report.stallionRegistrationId == stallionRegistration.id)
        assert(report.coveringYear == Year.of(2024))
        assert(report.submittedOn == LocalDate.of(2024, 9, 30))
    }

    @Test
    fun `期限内（当年9月30日まで）の提出は期限超過にならないこと`() {
        val report =
            CoveringReport.create(
                    stallionRegistration = BreedingFixture.stallionRegistration(),
                    coveringYear = Year.of(2024),
                    submittedOn = LocalDate.of(2024, 9, 30),
                )
                .unwrap()

        assert(!report.submittedLate)
    }

    @Test
    fun `期限超過（当年10月1日以降）の提出も受理され超過が導出されること`() {
        val report =
            CoveringReport.create(
                    stallionRegistration = BreedingFixture.stallionRegistration(),
                    coveringYear = Year.of(2024),
                    submittedOn = LocalDate.of(2024, 10, 1),
                )
                .unwrap()

        assert(report.submittedLate)
    }

    @Test
    fun `繁殖牝馬の繁殖登録に対しては NotStallion を返し生成しないこと`() {
        val broodmareRegistration = BreedingFixture.breedingRegistration()

        val result =
            CoveringReport.create(
                stallionRegistration = broodmareRegistration,
                coveringYear = Year.of(2024),
                submittedOn = LocalDate.of(2024, 9, 30),
            )

        assert(result.getError() == SubmitCoveringReportError.NotStallion)
    }

    @Test
    fun `reconstituteはIDとversionを保って復元すること`() {
        val original = BreedingFixture.coveringReport()

        val reconstituted =
            CoveringReport.reconstitute(
                id = original.id,
                stallionRegistrationId = original.stallionRegistrationId,
                coveringYear = original.coveringYear,
                submittedOn = original.submittedOn,
                version = 3L,
            )

        assert(reconstituted == original) // Entity は ID 等価
        assert(reconstituted.version == 3L)
    }
}
