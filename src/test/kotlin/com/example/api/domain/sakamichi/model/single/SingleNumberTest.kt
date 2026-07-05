package com.example.api.domain.sakamichi.model.single

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** SingleNumber 値オブジェクトの不変条件（1 以上）のユニットテスト。 */
class SingleNumberTest {
    @Test
    fun `1以上なら生成できる`() {
        assert(SingleNumber.create(1).unwrap().value == 1)
        assert(SingleNumber.create(31).unwrap().value == 31)
    }

    @Test
    fun `0以下は InvalidSingleNumber を返す`() {
        assert(SingleNumber.create(0).getError() == InvalidSingleNumber)
        assert(SingleNumber.create(-1).getError() == InvalidSingleNumber)
    }
}
