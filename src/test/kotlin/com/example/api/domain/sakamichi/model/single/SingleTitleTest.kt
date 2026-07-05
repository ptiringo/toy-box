package com.example.api.domain.sakamichi.model.single

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** SingleTitle 値オブジェクトの不変条件（非ブランク・100 文字以内）のユニットテスト。 */
class SingleTitleTest {
    @Test
    fun `非ブランクなら生成でき trim される`() {
        val title = SingleTitle.create("  ぐるぐるカーテン  ").unwrap()

        assert(title.value == "ぐるぐるカーテン")
    }

    @Test
    fun `上限の100文字は許可される`() {
        val title = SingleTitle.create("あ".repeat(100)).unwrap()

        assert(title.value.length == 100)
    }

    @Test
    fun `101文字は長すぎて InvalidSingleTitle を返す`() {
        assert(SingleTitle.create("あ".repeat(101)).getError() == InvalidSingleTitle)
    }

    @Test
    fun `空文字は InvalidSingleTitle を返す`() {
        assert(SingleTitle.create("").getError() == InvalidSingleTitle)
    }

    @Test
    fun `空白のみは InvalidSingleTitle を返す`() {
        assert(SingleTitle.create("   ").getError() == InvalidSingleTitle)
    }
}
