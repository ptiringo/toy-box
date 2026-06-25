package com.example.api.application.racing.jockey

import com.example.api.domain.racing.model.jockey.JockeyId
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * 照会ユースケース [FindJockeyUseCase] の単体テスト（軽量 CQRS（L2）の読み取り側。ADR-0031）。
 *
 * 読み取りポート [JockeyQueries] を mockk でスタブし、ヒット時は [JockeyView] を、不在時は [JockeyNotFound] を
 * 返す分岐を検証する（testing.md: applicationService は Repository / Query ポート境界をモックする）。
 */
class FindJockeyUseCaseTest {
    private val jockeyQueries = mockk<JockeyQueries>()
    private val findJockey = FindJockeyUseCase(jockeyQueries)

    @Test
    fun `存在するIDなら対応するJockeyViewをOkで返す`() {
        val id = generateId()
        val view = JockeyView(id = id, firstName = "武", lastName = "豊")
        every { jockeyQueries.findById(JockeyId(id)) } returns view

        val result = findJockey(FindJockeyQuery(id))

        assert(result.get() == view)
    }

    @Test
    fun `存在しないIDならJockeyNotFoundをErrで返す`() {
        val id = generateId()
        every { jockeyQueries.findById(JockeyId(id)) } returns null

        val result = findJockey(FindJockeyQuery(id))

        assert(result.getError() == JockeyNotFound(id))
    }
}
