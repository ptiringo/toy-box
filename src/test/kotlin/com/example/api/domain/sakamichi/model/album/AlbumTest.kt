package com.example.api.domain.sakamichi.model.album

import com.example.api.domain.sakamichi.model.group.GroupId
import com.example.api.domain.sakamichi.model.member.MemberId
import com.example.api.domain.sakamichi.model.release.Formation
import com.example.api.domain.sakamichi.model.release.FormationSlot
import com.example.api.domain.sakamichi.model.release.Position
import com.example.api.domain.sakamichi.model.release.ReleaseNumber
import com.github.michaelbull.result.getOrThrow
import java.util.UUID
import org.junit.jupiter.api.Test

class AlbumTest {
    private fun senbatsu(): Formation =
        Formation.create(listOf(FormationSlot(Position.Center, MemberId(UUID.randomUUID()))))
            .getOrThrow { AssertionError("fixture senbatsu should be valid") }

    private fun releaseNumber(n: Int): ReleaseNumber =
        ReleaseNumber.create(n).getOrThrow { AssertionError("fixture number should be valid") }

    @Test
    fun `create は渡した値をそのまま保持する`() {
        val groupId = GroupId(UUID.randomUUID())
        val number = releaseNumber(1)
        val title = AlbumTitle.create("Time flies").getOrThrow { AssertionError("valid title") }
        val senbatsu = senbatsu()

        val album =
            Album.create(groupId = groupId, number = number, title = title, senbatsu = senbatsu)

        assert(album.groupId == groupId)
        assert(album.number == number)
        assert(album.title == title)
        assert(album.senbatsu == senbatsu)
    }

    @Test
    fun `create は毎回異なる AlbumId を採番する`() {
        val groupId = GroupId(UUID.randomUUID())
        val title = AlbumTitle.create("A").getOrThrow { AssertionError("valid title") }
        val a = Album.create(groupId, releaseNumber(1), title, senbatsu())
        val b = Album.create(groupId, releaseNumber(2), title, senbatsu())
        assert(a.id != b.id)
    }

    @Test
    fun `同一性は AlbumId で判定される`() {
        val groupId = GroupId(UUID.randomUUID())
        val title = AlbumTitle.create("A").getOrThrow { AssertionError("valid title") }
        val album = Album.create(groupId, releaseNumber(1), title, senbatsu())
        assert(album == album)
    }
}
