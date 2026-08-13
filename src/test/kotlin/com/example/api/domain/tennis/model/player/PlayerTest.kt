package com.example.api.domain.tennis.model.player

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import java.time.LocalDate
import org.junit.jupiter.api.Test

/** Player 集約の生成（登録）と不変条件（プロ転向日が生年月日より後）のユニットテスト。 */
class PlayerTest {
    private val name = PlayerName.create("Nishikori", "Kei").unwrap()
    private val country = Country.create("JPN").unwrap()
    private val dateOfBirth = DateOfBirth(LocalDate.of(1989, 12, 29))
    private val turnedProOn = TurnedProDate(LocalDate.of(2007, 9, 24))

    @Test
    fun `プロ転向日が生年月日より後なら登録できる`() {
        val player =
            Player.create(name, country, Handedness.RIGHT, dateOfBirth, turnedProOn).unwrap()

        assert(player.name == name)
        assert(player.country == country)
        assert(player.handedness == Handedness.RIGHT)
        assert(player.dateOfBirth == dateOfBirth)
        assert(player.turnedProOn == turnedProOn)
    }

    @Test
    fun `プロ転向日が生年月日より前なら TurnedProBeforeBirth を返す`() {
        val before = TurnedProDate(LocalDate.of(1989, 12, 28))

        val error = Player.create(name, country, Handedness.LEFT, dateOfBirth, before).getError()

        assert(error == TurnedProBeforeBirth(dateOfBirth, before))
    }

    @Test
    fun `プロ転向日が生年月日と同日なら TurnedProBeforeBirth を返す`() {
        val sameDay = TurnedProDate(dateOfBirth.value)

        val error = Player.create(name, country, Handedness.RIGHT, dateOfBirth, sameDay).getError()

        assert(error == TurnedProBeforeBirth(dateOfBirth, sameDay))
    }

    @Test
    fun `別々に登録した選手は同じ属性でも異なる ID を持ち等価ではない`() {
        val one = Player.create(name, country, Handedness.RIGHT, dateOfBirth, turnedProOn).unwrap()
        val other =
            Player.create(name, country, Handedness.RIGHT, dateOfBirth, turnedProOn).unwrap()

        assert(one.id != other.id)
        assert(one != other)
    }
}
