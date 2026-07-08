package com.example.api.domain.sakamichi.model.group

import com.github.michaelbull.result.unwrap
import org.junit.jupiter.api.Test

/** Group 集約の生成のユニットテスト。 */
class GroupTest {
    private val name = GroupName.create("乃木坂46").unwrap()

    @Test
    fun `生成するとグループ名を保持しIDが採番される`() {
        val group = Group.create(name)

        assert(group.name == name)
    }

    @Test
    fun `生成のたびに異なるIDが採番される`() {
        val first = Group.create(name)
        val second = Group.create(name)

        assert(first.id != second.id)
    }

    @Test
    fun `非選抜活動体の呼称ありで生成すると属性に反映される`() {
        val appellation = NonSenbatsuAppellation.create("アンダー").unwrap()

        val group = Group.create(name, appellation)

        assert(group.nonSenbatsuAppellation == appellation)
    }

    @Test
    fun `呼称を省略すると null になる`() {
        val group = Group.create(name)

        assert(group.nonSenbatsuAppellation == null)
    }
}
