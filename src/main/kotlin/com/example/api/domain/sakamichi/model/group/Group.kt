package com.example.api.domain.sakamichi.model.group

import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** グループID */
@ValueObject @JvmInline value class GroupId(val value: UUID)

/**
 * 坂道シリーズのグループ（乃木坂46・櫻坂46・日向坂46 等）を表す集約ルート。
 *
 * メンバーの在籍はグループ側では保持しない。加入・卒業はメンバー個人のライフサイクルイベントであり、 在籍（所属・期生・在籍状態）は Member 集約が [GroupId] への ID
 * 参照で持つ（方向の判断はスペック `docs/superpowers/specs/2026-07-04-sakamichi-member-group-design.md`
 * を参照）。「グループの在籍者一覧」の ような読み取りは軽量 CQRS（ADR-0031）の Read Model で解決する。
 *
 * 非選抜活動体の呼称（[NonSenbatsuAppellation]）は任意属性で、呼称を持たないグループ/時期は null で許容する。
 * 呼称の有効期間や作品との結び付けは持たない（時間軸・結び付けは #583 の関心）。
 *
 * 改名（欅坂46→櫻坂46 等）の状態遷移は今回スコープ外。コンストラクタは private とし、生成は [create] に限る。
 *
 * @property id グループID（生成時に自動採番）
 * @property name グループ名
 * @property nonSenbatsuAppellation 非選抜活動体の呼称。持たない場合は null
 */
@AggregateRoot
class Group
private constructor(
    @field:Identity override val id: GroupId,
    val name: GroupName,
    val nonSenbatsuAppellation: NonSenbatsuAppellation?,
) : Entity<GroupId>() {
    companion object {
        /**
         * グループを生成する。
         *
         * 検証済みの [GroupName] / [NonSenbatsuAppellation] を受け取るため失敗せず、[Group] をそのまま返す。
         *
         * @param name グループ名
         * @param nonSenbatsuAppellation 非選抜活動体の呼称（任意）。持たない場合は指定しない（null）
         */
        fun create(name: GroupName, nonSenbatsuAppellation: NonSenbatsuAppellation? = null): Group =
            Group(
                id = GroupId(generateId()),
                name = name,
                nonSenbatsuAppellation = nonSenbatsuAppellation,
            )
    }
}
