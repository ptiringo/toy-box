package com.example.api.domain.sakamichi.model.release

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** TrackNumber 値オブジェクトの不変条件（1 以上）のユニットテスト。 */
class TrackNumberTest {
    @Test
    fun `1以上なら生成できる`() {
        assert(TrackNumber.create(1).unwrap().value == 1)
        assert(TrackNumber.create(12).unwrap().value == 12)
    }

    @Test
    fun `0以下は InvalidTrackNumber を返す`() {
        assert(TrackNumber.create(0).getError() == InvalidTrackNumber)
        assert(TrackNumber.create(-1).getError() == InvalidTrackNumber)
    }
}
