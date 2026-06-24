package com.example.api.domain.studbook.model.breeding

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** [StudCertificate] のユニットテスト（生成不変条件と [StudCertificate.authorizes] による有効性検証） */
class StudCertificateTest {
    private val number = StudCertificateNumber.create("S-2024-0001").unwrap()
    private val hokkaido = BreedingRegion.create("北海道").unwrap()
    private val aomori = BreedingRegion.create("青森").unwrap()
    private val period =
        ValidityPeriod.create(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)).unwrap()

    @Test
    fun `有効区域が1つ以上あれば生成できること`() {
        val certificate = StudCertificate.create(number, setOf(hokkaido), period).unwrap()

        assert(certificate.validRegions == setOf(hokkaido))
        assert(certificate.validPeriod == period)
    }

    @Test
    fun `有効区域が空だと NoValidRegion を返すこと`() {
        val result = StudCertificate.create(number, emptySet(), period)

        assert(result.getError() == InvalidStudCertificate.NoValidRegion)
    }

    @Test
    fun `有効区域内かつ有効期間内の種付は authorizes が成功すること`() {
        val certificate = StudCertificate.create(number, setOf(hokkaido), period).unwrap()

        val result = certificate.authorizes(LocalDate.of(2024, 4, 1), hokkaido)

        assert(result.getError() == null)
    }

    @Test
    fun `有効期間外の種付日は OutsideValidPeriod を返すこと`() {
        val certificate = StudCertificate.create(number, setOf(hokkaido), period).unwrap()
        val outOfPeriod = LocalDate.of(2025, 1, 1)

        val result = certificate.authorizes(outOfPeriod, hokkaido)

        assert(result.getError() == CoveringValidityError.OutsideValidPeriod(outOfPeriod, period))
    }

    @Test
    fun `有効区域外の種付場所は OutsideValidRegion を返すこと`() {
        val certificate = StudCertificate.create(number, setOf(hokkaido), period).unwrap()

        val result = certificate.authorizes(LocalDate.of(2024, 4, 1), aomori)

        assert(
            result.getError() == CoveringValidityError.OutsideValidRegion(aomori, setOf(hokkaido))
        )
    }

    @Test
    fun `有効期間外かつ有効区域外なら有効期間を優先して OutsideValidPeriod を返すこと`() {
        val certificate = StudCertificate.create(number, setOf(hokkaido), period).unwrap()
        val outOfPeriod = LocalDate.of(2025, 1, 1)

        val result = certificate.authorizes(outOfPeriod, aomori)

        assert(result.getError() == CoveringValidityError.OutsideValidPeriod(outOfPeriod, period))
    }
}
