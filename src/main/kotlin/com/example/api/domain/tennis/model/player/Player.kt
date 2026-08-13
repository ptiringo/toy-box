package com.example.api.domain.tennis.model.player

import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 選手 ID を表す値クラス。
 *
 * @property value UUID 形式の ID 値
 */
@ValueObject @JvmInline value class PlayerId(val value: UUID)

/**
 * プロ転向日が生年月日より後でない（同日を含む）。
 *
 * @property dateOfBirth 生年月日
 * @property turnedProOn プロ転向日
 */
data class TurnedProBeforeBirth(val dateOfBirth: DateOfBirth, val turnedProOn: TurnedProDate)

/**
 * プロテニス選手を表す集約ルート。
 *
 * 不変条件:
 * - プロ転向日が生年月日より後であること（同日は認めない）
 *
 * 氏名・国籍の形式的な不変条件は [PlayerName] / [Country] が各自で守るため、この集約は複数の値オブジェクトに
 * またがる不変条件だけを検証する。不変条件を満たした上で生成するためにコンストラクタは private とし、 [Player.create] でのみ生成する。
 *
 * ランキング（時系列の派生概念）は本集約の関心ではない。
 *
 * @property id 選手 ID
 * @property name 氏名
 * @property country 国籍
 * @property handedness 利き手
 * @property dateOfBirth 生年月日
 * @property turnedProOn プロ転向日
 */
@AggregateRoot
class Player
private constructor(
    /** 選手 ID */
    @field:Identity override val id: PlayerId,
    /** 氏名 */
    val name: PlayerName,
    /** 国籍 */
    val country: Country,
    /** 利き手 */
    val handedness: Handedness,
    /** 生年月日 */
    val dateOfBirth: DateOfBirth,
    /** プロ転向日 */
    val turnedProOn: TurnedProDate,
) : Entity<PlayerId>() {

    companion object {
        /**
         * 不変条件を検証してから [Player] を新規生成（登録）する。生成時に一意な ID を自動採番する。
         *
         * @return 生成された [Player]、または不変条件違反を表す [TurnedProBeforeBirth]
         */
        fun create(
            name: PlayerName,
            country: Country,
            handedness: Handedness,
            dateOfBirth: DateOfBirth,
            turnedProOn: TurnedProDate,
        ): Result<Player, TurnedProBeforeBirth> =
            if (turnedProOn.value.isAfter(dateOfBirth.value)) {
                Ok(
                    Player(
                        id = PlayerId(generateId()),
                        name = name,
                        country = country,
                        handedness = handedness,
                        dateOfBirth = dateOfBirth,
                        turnedProOn = turnedProOn,
                    )
                )
            } else {
                Err(TurnedProBeforeBirth(dateOfBirth, turnedProOn))
            }
    }
}
