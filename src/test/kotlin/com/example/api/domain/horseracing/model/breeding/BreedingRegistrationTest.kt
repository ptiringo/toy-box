package com.example.api.domain.horseracing.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** BreedingRegistration 集約の繁殖供用停止（retire）に関するユニットテスト。 */
class BreedingRegistrationTest {
    private val occurredOn = LocalDate.of(2024, 6, 1)

    @Test
    fun `生成直後は供用中で供用停止を持たない`() {
        val registration = BreedingFixture.breedingRegistration()

        assert(!registration.isRetired)
        assert(registration.retirement == null)
    }

    @Test
    fun `供用停止すると事由と発生日を持つ新しい登録が返り 同一性は引き継がれる`() {
        val active = BreedingFixture.breedingRegistration()

        val retired = active.retire(RetirementReason.DEATH, occurredOn).unwrap()

        assert(retired.isRetired)
        assert(retired.retirement == BreedingRetirement(RetirementReason.DEATH, occurredOn))
        assert(retired.id == active.id)
        // 他の属性も引き継がれる
        assert(retired.registrationNumber == active.registrationNumber)
        assert(retired.role == active.role)
        assert(retired.registeredHorseId == active.registeredHorseId)
    }

    @Test
    fun `retire は元の登録を変更しない（イミュータブル）`() {
        val active = BreedingFixture.breedingRegistration()

        active.retire(RetirementReason.USE_CHANGE, occurredOn).unwrap()

        assert(!active.isRetired)
        assert(active.retirement == null)
    }

    @Test
    fun `供用停止済みの登録への再停止は AlreadyRetired を返し 供用停止は変わらない`() {
        val retired =
            BreedingFixture.breedingRegistration()
                .retire(RetirementReason.DEATH, occurredOn)
                .unwrap()
        val again = LocalDate.of(2025, 1, 1)

        val result = retired.retire(RetirementReason.OTHER, again)

        assert(
            result.getError() ==
                AlreadyRetired(BreedingRetirement(RetirementReason.DEATH, occurredOn))
        )
        assert(retired.retirement == BreedingRetirement(RetirementReason.DEATH, occurredOn))
    }
}
