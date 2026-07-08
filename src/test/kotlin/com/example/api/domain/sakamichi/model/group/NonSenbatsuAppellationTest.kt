package com.example.api.domain.sakamichi.model.group

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** NonSenbatsuAppellation 値オブジェクトの不変条件（非ブランク・100 文字以内）のユニットテスト。 */
class NonSenbatsuAppellationTest {
    @Test
    fun `非ブランクなら生成でき trim される`() {
        val appellation = NonSenbatsuAppellation.create("  アンダー  ").unwrap()

        assert(appellation.value == "アンダー")
    }

    @Test
    fun `上限の100文字は許可される`() {
        val appellation = NonSenbatsuAppellation.create("あ".repeat(100)).unwrap()

        assert(appellation.value.length == 100)
    }

    @Test
    fun `101文字は長すぎて InvalidNonSenbatsuAppellation を返す`() {
        assert(
            NonSenbatsuAppellation.create("あ".repeat(101)).getError() ==
                InvalidNonSenbatsuAppellation
        )
    }

    @Test
    fun `空文字は InvalidNonSenbatsuAppellation を返す`() {
        assert(NonSenbatsuAppellation.create("").getError() == InvalidNonSenbatsuAppellation)
    }

    @Test
    fun `空白のみは InvalidNonSenbatsuAppellation を返す`() {
        assert(NonSenbatsuAppellation.create("   ").getError() == InvalidNonSenbatsuAppellation)
    }
}
