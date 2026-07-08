package com.example.api.domain.sakamichi.model.release

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** TrackTitle 値オブジェクトの不変条件（非ブランク・100 文字以内）のユニットテスト。 */
class TrackTitleTest {
    @Test
    fun `非ブランクなら生成でき trim される`() {
        val title = TrackTitle.create("  左胸の勇気  ").unwrap()

        assert(title.value == "左胸の勇気")
    }

    @Test
    fun `上限の100文字は許可される`() {
        val title = TrackTitle.create("あ".repeat(100)).unwrap()

        assert(title.value.length == 100)
    }

    @Test
    fun `101文字は長すぎて InvalidTrackTitle を返す`() {
        assert(TrackTitle.create("あ".repeat(101)).getError() == InvalidTrackTitle)
    }

    @Test
    fun `空文字は InvalidTrackTitle を返す`() {
        assert(TrackTitle.create("").getError() == InvalidTrackTitle)
    }

    @Test
    fun `空白のみは InvalidTrackTitle を返す`() {
        assert(TrackTitle.create("   ").getError() == InvalidTrackTitle)
    }
}
