package com.example.api.domain.sakamichi.model.group

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** GroupName 値オブジェクトの不変条件（非ブランク・100 文字以内）のユニットテスト。 */
class GroupNameTest {
    @Test
    fun `非ブランクなら生成でき trim される`() {
        val name = GroupName.create("  乃木坂46  ").unwrap()

        assert(name.value == "乃木坂46")
    }

    @Test
    fun `上限の100文字は許可される`() {
        val name = GroupName.create("あ".repeat(100)).unwrap()

        assert(name.value.length == 100)
    }

    @Test
    fun `101文字は長すぎて InvalidGroupName を返す`() {
        assert(GroupName.create("あ".repeat(101)).getError() == InvalidGroupName)
    }

    @Test
    fun `空文字は InvalidGroupName を返す`() {
        assert(GroupName.create("").getError() == InvalidGroupName)
    }

    @Test
    fun `空白のみは InvalidGroupName を返す`() {
        assert(GroupName.create("   ").getError() == InvalidGroupName)
    }
}
