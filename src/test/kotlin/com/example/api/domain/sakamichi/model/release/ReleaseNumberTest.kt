package com.example.api.domain.sakamichi.model.release

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** ReleaseNumber 値オブジェクトの不変条件（1 以上）のユニットテスト。 */
class ReleaseNumberTest {
    @Test
    fun `1以上なら生成できる`() {
        assert(ReleaseNumber.create(1).unwrap().value == 1)
        assert(ReleaseNumber.create(31).unwrap().value == 31)
    }

    @Test
    fun `0以下は InvalidReleaseNumber を返す`() {
        assert(ReleaseNumber.create(0).getError() == InvalidReleaseNumber)
        assert(ReleaseNumber.create(-1).getError() == InvalidReleaseNumber)
    }
}
