package com.example.api.domain.studbook.model.horse.bloodhorse

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** ForeignSireName 値オブジェクトのユニットテスト */
class ForeignSireNameTest {
    @Test
    fun `外国名をそのまま受け付けること`() {
        val name = ForeignSireName.create("Danehill").unwrap()

        assert(name.value == "Danehill")
    }

    @Test
    fun `前後の空白は trim されること`() {
        val name = ForeignSireName.create("  Danehill ").unwrap()

        assert(name.value == "Danehill")
    }

    @Test
    fun `空白のみならブランクエラーを返すこと`() {
        val result = ForeignSireName.create("   ")

        assert(result.getError() == BlankForeignSireName)
    }
}
