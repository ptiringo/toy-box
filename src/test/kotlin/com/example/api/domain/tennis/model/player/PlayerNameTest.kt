package com.example.api.domain.tennis.model.player

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** PlayerName 値オブジェクトの不変条件（姓・名とも非ブランク・50 文字以内）のユニットテスト。 */
class PlayerNameTest {
    @Test
    fun `姓・名とも非ブランクなら生成でき trim される`() {
        val name = PlayerName.create("  Nishikori  ", "  Kei  ").unwrap()

        assert(name.familyName == "Nishikori")
        assert(name.givenName == "Kei")
    }

    @Test
    fun `上限の50文字は許可される`() {
        val name = PlayerName.create("a".repeat(50), "b".repeat(50)).unwrap()

        assert(name.familyName.length == 50)
        assert(name.givenName.length == 50)
    }

    @Test
    fun `姓が51文字は長すぎて InvalidPlayerName を返す`() {
        assert(PlayerName.create("a".repeat(51), "Kei").getError() == InvalidPlayerName)
    }

    @Test
    fun `名が51文字は長すぎて InvalidPlayerName を返す`() {
        assert(PlayerName.create("Nishikori", "b".repeat(51)).getError() == InvalidPlayerName)
    }

    @Test
    fun `姓がブランクは InvalidPlayerName を返す`() {
        assert(PlayerName.create("  ", "Kei").getError() == InvalidPlayerName)
    }

    @Test
    fun `名がブランクは InvalidPlayerName を返す`() {
        assert(PlayerName.create("Nishikori", "").getError() == InvalidPlayerName)
    }
}
