package com.example.api.domain.sakamichi.model.album

import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import org.junit.jupiter.api.Test

class AlbumTitleTest {
    @Test
    fun `非ブランクなら作品リード曲名を生成できる`() {
        val result = AlbumTitle.create("僕は僕を好きになる")
        assert(result.get()?.value == "僕は僕を好きになる")
    }

    @Test
    fun `前後の空白はトリムされる`() {
        val result = AlbumTitle.create("  Time flies  ")
        assert(result.get()?.value == "Time flies")
    }

    @Test
    fun `ブランクは不変条件違反で弾く`() {
        val result = AlbumTitle.create("   ")
        assert(result.getError() == InvalidAlbumTitle)
    }

    @Test
    fun `100文字以内は許容し101文字は弾く`() {
        assert(AlbumTitle.create("あ".repeat(100)).get()?.value == "あ".repeat(100))
        assert(AlbumTitle.create("あ".repeat(101)).getError() == InvalidAlbumTitle)
    }
}
