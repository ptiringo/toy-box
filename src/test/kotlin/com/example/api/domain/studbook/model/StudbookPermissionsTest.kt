package com.example.api.domain.studbook.model

import com.example.api.domain.racing.model.RacingPermissions
import org.junit.jupiter.api.Test

class StudbookPermissionsTest {

    @Test
    fun `権限文字列は DB の role_permission と一致する契約なので値を固定する`() {
        assert(StudbookPermissions.HORSE_REGISTER.value == "studbook:horse:register")
        assert(
            StudbookPermissions.HORSE_REGISTER_IMPORTED.value == "studbook:horse:registerImported"
        )
        assert(StudbookPermissions.HORSE_REGISTER_FOAL.value == "studbook:horse:registerFoal")
        assert(StudbookPermissions.HORSE_NAME.value == "studbook:horse:name")
        assert(StudbookPermissions.INSPECTION_RECORD.value == "studbook:inspection:record")
        assert(
            StudbookPermissions.BREEDING_REGISTRATION_REGISTER.value ==
                "studbook:breedingRegistration:register"
        )
        assert(
            StudbookPermissions.BREEDING_RESULT_RECORD_COVERING.value ==
                "studbook:breedingResult:recordCovering"
        )
        assert(
            StudbookPermissions.BREEDING_RESULT_RECORD_UNCOVERED.value ==
                "studbook:breedingResult:recordUncovered"
        )
        assert(
            StudbookPermissions.BREEDING_RESULT_REPORT_FOALING.value ==
                "studbook:breedingResult:reportFoaling"
        )
        assert(
            StudbookPermissions.BREEDING_RESULT_SUBMIT_REPORT.value ==
                "studbook:breedingResult:submitReport"
        )
        assert(StudbookPermissions.COVERING_REPORT_SUBMIT.value == "studbook:coveringReport:submit")
        assert(RacingPermissions.JOCKEY_REGISTER.value == "racing:jockey:register")
    }
}
