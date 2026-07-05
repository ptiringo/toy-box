package com.example.api.domain.sakamichi.model.member

import com.example.api.domain.sakamichi.model.group.GroupId
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.LocalDate
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** メンバーID */
@ValueObject @JvmInline value class MemberId(val value: UUID)

/**
 * 卒業（[Member.graduate]）の不変条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。
 */
sealed interface GraduateError {
    /**
     * 既に卒業済みのメンバーへ重ねて卒業しようとした。
     *
     * @property graduatedOn 既に記録されている卒業日
     */
    data class AlreadyGraduated(val graduatedOn: LocalDate) : GraduateError

    /** 卒業日が加入日より前で、時間軸として成立しない。 */
    data object GraduatedBeforeJoined : GraduateError
}

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
    /**
     * グループを卒業し、卒業済みの新しい [Member] を返す。
     *
     * 在籍中→卒業済みの状態遷移。成功時は [membership] のみ [Membership.Graduated] に差し替えた 新インスタンスを作り（[id]
     * を含む他の属性は引き継ぐ）、元のインスタンスは変更しない（ADR-0009）。 既に卒業済みなら
     * [GraduateError.AlreadyGraduated]、卒業日が加入日（[joinedOn]）より前なら
     * [GraduateError.GraduatedBeforeJoined] を返し、写像しない。加入日当日の卒業は許す。
     *
     * @param graduatedOn 卒業日（加入日以降であること）
     * @return 卒業済みの新しい [Member]、または不変条件違反を表す [GraduateError]
     */
    fun graduate(graduatedOn: LocalDate): Result<Member, GraduateError> {
        val current = membership
        return when {
            current is Membership.Graduated ->
                Err(GraduateError.AlreadyGraduated(current.graduatedOn))
            graduatedOn.isBefore(joinedOn) -> Err(GraduateError.GraduatedBeforeJoined)
            else -> Ok(copy(membership = Membership.Graduated(graduatedOn)))
        }
    }

    /** [id] と未指定の属性を引き継ぎ、指定された属性だけを差し替えた新しい [Member] を返す。 */
    private fun copy(membership: Membership = this.membership): Member =
        Member(
            id = id,
            name = name,
            groupId = groupId,
            generation = generation,
            joinedOn = joinedOn,
            membership = membership,
        )

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
