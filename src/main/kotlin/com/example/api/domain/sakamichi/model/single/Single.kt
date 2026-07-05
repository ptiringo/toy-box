package com.example.api.domain.sakamichi.model.single

import com.example.api.domain.sakamichi.model.group.GroupId
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** シングルID */
@ValueObject @JvmInline value class SingleId(val value: UUID)

/**
 * シングル（グループが発表する作品）を表す集約ルート。
 *
 * 選抜（[Senbatsu]）はグループの恒久属性ではなくシングル単位の一時的編成のため、本集約が VO として 内包する（sakamichi-sources
 * §4）。発表元のグループは別集約のため [GroupId] 経由の ID 参照で保持する。
 *
 * 状態はイミュータブルに扱う（ADR-0009）。コンストラクタは private とし、生成は [create] に限る。
 * 選抜の入れ替え（発表後の編成変更）等の状態遷移は今回スコープ外（必要になった時点でモデリングする）。
 *
 * @property id シングルID（生成時に自動採番）
 * @property groupId 発表元グループのID
 * @property number 作品番号（n 枚目。グループ内での連番）
 * @property title 表題（表題曲の曲名）
 * @property senbatsu 選抜（表題曲を歌う編成）
 */
@AggregateRoot
class Single
private constructor(
    @field:Identity override val id: SingleId,
    val groupId: GroupId,
    val number: SingleNumber,
    val title: SingleTitle,
    val senbatsu: Senbatsu,
) : Entity<SingleId>() {
    companion object {
        /**
         * シングルを生成する。
         *
         * 検証済みの VO を受け取るため失敗せず、[Single] をそのまま返す。選抜の構造的不変条件は [Senbatsu.create]
         * が、選抜対象メンバーの在籍検証はドメインサービス（#551）が担う。
         *
         * @param groupId 発表元グループのID
         * @param number 作品番号（n 枚目）
         * @param title 表題
         * @param senbatsu 選抜
         */
        fun create(
            groupId: GroupId,
            number: SingleNumber,
            title: SingleTitle,
            senbatsu: Senbatsu,
        ): Single =
            Single(
                id = SingleId(generateId()),
                groupId = groupId,
                number = number,
                title = title,
                senbatsu = senbatsu,
            )
    }
}
