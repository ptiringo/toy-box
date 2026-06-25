package com.example.api.controller.breeding

import com.example.api.domain.studbook.model.breeding.BreedingFixture
import com.example.api.domain.studbook.model.breeding.RetirementReason
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

class BreedingRegistrationResponseTest {

    @Test
    fun `供用中の繁殖登録は retirement が null でロールが wire enum に写ること`() {
        val stallion = BreedingFixture.stallionRegistration()

        val response = stallion.toResponse()

        assert(response.id == stallion.id.value)
        assert(response.registeredHorseId == stallion.registeredHorseId.value)
        assert(response.registrationNumber == stallion.registrationNumber.value)
        assert(response.role == BreedingRoleDto.STALLION)
        assert(response.retirement == null)
    }

    @Test
    fun `供用停止済みの繁殖登録は事由と発生日が wire 表現に写ること`() {
        val occurredOn = LocalDate.of(2024, 5, 1)
        val retired =
            BreedingFixture.breedingRegistration()
                .retire(RetirementReason.DEATH, occurredOn)
                .unwrap()

        val response = retired.toResponse()

        assert(response.role == BreedingRoleDto.BROODMARE)
        assert(response.retirement?.reason == RetirementReasonDto.DEATH)
        assert(response.retirement?.occurredOn == occurredOn)
    }
}
