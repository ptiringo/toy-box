package com.example.api.domain.sakamichi.model.member

import com.example.api.domain.sakamichi.model.group.GroupId
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import java.time.LocalDate
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** メンバーID */
@ValueObject @JvmInline value class MemberId(val value: UUID)

/**
 * 坂道グループのメンバーを表す集約ルート。
 *
 * 生成（[create]）＝グループへの加入であり、所属グループ（[groupId]）・期生（[generation]）・
 * 加入日（[joinedOn]）は加入時に固定される。在籍状態（[membership]）は加入直後は [Membership.Active] で、卒業（graduate）で
 * [Membership.Graduated] へ遷移する。
 *
 * 「メンバーは常に 1 グループのみ所属」を前提に、在籍を本集約の属性として持つ（現行 3 グループ体制の スナップショット。兼任の歴史的事例と Membership
 * 独立集約への昇格判断はスペック `docs/superpowers/specs/2026-07-04-sakamichi-member-group-design.md` を参照）。所属グループは
 * 別集約のため [GroupId] 経由の ID 参照で保持する。
 *
 * 状態はイミュータブルに扱う（ADR-0009）。コンストラクタは private とし、生成は [create] に限る。
 *
 * @property id メンバーID（生成時に自動採番し、以後の写像でも引き継ぐ）
 * @property name 氏名
 * @property groupId 所属グループのID（加入時に固定）
 * @property generation 期生（加入時に固定。グループごとに独立採番）
 * @property joinedOn 加入日
 * @property membership 在籍状態（在籍中／卒業済み）
 */
@AggregateRoot
class Member
private constructor(
    @field:Identity override val id: MemberId,
    val name: MemberName,
    val groupId: GroupId,
    val generation: Generation,
    val joinedOn: LocalDate,
    val membership: Membership,
) : Entity<MemberId>() {
    companion object {
        /**
         * グループへ加入したメンバーを生成する。
         *
         * 生成＝加入であり、在籍状態は [Membership.Active] で始まる。期生・加入日はここで固定される。 検証済みの VO を受け取るため失敗せず、[Member]
         * をそのまま返す。
         *
         * @param name 氏名
         * @param groupId 加入先グループのID
         * @param generation 期生
         * @param joinedOn 加入日
         */
        fun create(
            name: MemberName,
            groupId: GroupId,
            generation: Generation,
            joinedOn: LocalDate,
        ): Member =
            Member(
                id = MemberId(generateId()),
                name = name,
                groupId = groupId,
                generation = generation,
                joinedOn = joinedOn,
                membership = Membership.Active,
            )
    }
}
