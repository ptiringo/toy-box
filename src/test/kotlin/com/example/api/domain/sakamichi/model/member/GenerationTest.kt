package com.example.api.domain.sakamichi.model.member

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** Generation 値オブジェクトの不変条件（1 以上）のユニットテスト。 */
class GenerationTest {
    @Test
    fun `下限の1期生は許可される`() {
        assert(Generation.create(1).unwrap().value == 1)
    }

    @Test
    fun `2以上も許可される`() {
        assert(Generation.create(5).unwrap().value == 5)
    }

    @Test
    fun `0は InvalidGeneration を返す`() {
        assert(Generation.create(0).getError() == InvalidGeneration)
    }

    @Test
    fun `負数は InvalidGeneration を返す`() {
        assert(Generation.create(-1).getError() == InvalidGeneration)
    }
}
