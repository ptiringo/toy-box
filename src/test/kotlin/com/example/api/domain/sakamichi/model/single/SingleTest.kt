package com.example.api.domain.sakamichi.model.single

import com.example.api.domain.sakamichi.model.group.Group
import com.example.api.domain.sakamichi.model.group.GroupName
import com.example.api.domain.sakamichi.model.member.MemberId
import com.example.api.domain.sakamichi.model.release.Formation
import com.example.api.domain.sakamichi.model.release.FormationSlot
import com.example.api.domain.sakamichi.model.release.Position
import com.example.api.domain.sakamichi.model.release.ReleaseNumber
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** Single 集約の生成のユニットテスト。 */
class SingleTest {
    private val group = Group.create(GroupName.create("乃木坂46").unwrap())
    private val number = ReleaseNumber.create(1).unwrap()
    private val title = SingleTitle.create("ぐるぐるカーテン").unwrap()
    private val senbatsu =
        Formation.create(listOf(FormationSlot(Position.Center, MemberId(generateId())))).unwrap()

    private fun createSingle(): Single =
        Single.create(groupId = group.id, number = number, title = title, senbatsu = senbatsu)

    @Test
    fun `生成するとグループ・作品番号・表題・選抜を保持する`() {
        val single = createSingle()

        assert(single.groupId == group.id)
        assert(single.number == number)
        assert(single.title == title)
        assert(single.senbatsu == senbatsu)
    }

    @Test
    fun `生成のたびに異なるIDが採番される`() {
        assert(createSingle().id != createSingle().id)
    }
}
