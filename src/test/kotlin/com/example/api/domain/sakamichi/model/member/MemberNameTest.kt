package com.example.api.domain.sakamichi.model.member

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** MemberName 値オブジェクトの不変条件（姓・名とも非ブランク・50 文字以内）のユニットテスト。 */
class MemberNameTest {
    @Test
    fun `姓・名とも非ブランクなら生成でき trim される`() {
        val name = MemberName.create("  齋藤  ", "  飛鳥  ").unwrap()

        assert(name.familyName == "齋藤")
        assert(name.givenName == "飛鳥")
    }

    @Test
    fun `上限の50文字は許可される`() {
        val name = MemberName.create("あ".repeat(50), "い".repeat(50)).unwrap()

        assert(name.familyName.length == 50)
        assert(name.givenName.length == 50)
    }

    @Test
    fun `姓が51文字は長すぎて InvalidMemberName を返す`() {
        assert(MemberName.create("あ".repeat(51), "飛鳥").getError() == InvalidMemberName)
    }

    @Test
    fun `名が51文字は長すぎて InvalidMemberName を返す`() {
        assert(MemberName.create("齋藤", "あ".repeat(51)).getError() == InvalidMemberName)
    }

    @Test
    fun `姓がブランクは InvalidMemberName を返す`() {
        assert(MemberName.create("  ", "飛鳥").getError() == InvalidMemberName)
    }

    @Test
    fun `名がブランクは InvalidMemberName を返す`() {
        assert(MemberName.create("齋藤", "").getError() == InvalidMemberName)
    }
}
