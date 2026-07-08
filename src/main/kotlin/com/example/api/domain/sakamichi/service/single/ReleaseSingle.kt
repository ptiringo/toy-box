package com.example.api.domain.sakamichi.service.single

import com.example.api.domain.sakamichi.model.group.Group
import com.example.api.domain.sakamichi.model.member.Member
import com.example.api.domain.sakamichi.model.member.MemberId
import com.example.api.domain.sakamichi.model.member.Membership
import com.example.api.domain.sakamichi.model.release.Formation
import com.example.api.domain.sakamichi.model.release.FormationError
import com.example.api.domain.sakamichi.model.release.FormationSlot
import com.example.api.domain.sakamichi.model.release.Position
import com.example.api.domain.sakamichi.model.release.ReleaseNumber
import com.example.api.domain.sakamichi.model.single.Single
import com.example.api.domain.sakamichi.model.single.SingleTitle
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError

/**
 * シングルを発売し、選抜を編成する（[Single] を成立させる入口）。
 *
 * **集約をまたぐ前提条件「選抜対象メンバーが当該グループに在籍中であること」を封じ込める**（studbook の registerFoal 型。#364 段階 4）。この前提は
 * [Member] 単体でも [Formation] 単体でも守れないため、協力集約 （[group] と選抜対象の [Member]
 * 群）を引数で受け取って検証し、[Formation.create] → [Single.create] へ
 * 橋渡しする。既存レコード集合への問い合わせは不要なのでリポジトリポートは受け取らない（ADR-0022 の例外に 該当しない）。
 *
 * 検証は (1) 全員が在籍中（[Membership.Active]）であること、(2) 全員の所属が [group] と一致すること、 (3)
 * 選抜の構造的不変条件（[Formation.create] へ委譲）の順で行い、違反したメンバーが複数いる場合は まとめて返す。検証済みメンバーの ID を [FormationSlot]
 * へ写すため、成立した選抜のメンバー参照は 必ず本検証を通過している。
 *
 * @param group 発売元のグループ（選抜対象メンバーの在籍先であること）
 * @param number 作品番号（n 枚目）
 * @param title 表題
 * @param lineup 選抜の編成（立ち位置 × メンバーの並び）
 * @return 成立した [Single]、または前提条件違反を表す [ReleaseSingleError]
 */
fun releaseSingle(
    group: Group,
    number: ReleaseNumber,
    title: SingleTitle,
    lineup: List<Pair<Position, Member>>,
): Result<Single, ReleaseSingleError> {
    val members = lineup.map { (_, member) -> member }
    val notActive = members.filter { it.membership != Membership.Active }.map { it.id }.toSet()
    val notInGroup = members.filter { it.groupId != group.id }.map { it.id }.toSet()
    return when {
        notActive.isNotEmpty() -> Err(ReleaseSingleError.MembersNotActive(notActive))
        notInGroup.isNotEmpty() -> Err(ReleaseSingleError.MembersNotInGroup(notInGroup))
        else ->
            Formation.create(
                    lineup.map { (position, member) -> FormationSlot(position, member.id) }
                )
                .mapError { ReleaseSingleError.InvalidSenbatsu(it) }
                .map { senbatsu ->
                    Single.create(
                        groupId = group.id,
                        number = number,
                        title = title,
                        senbatsu = senbatsu,
                    )
                }
    }
}

/**
 * シングル発売（選抜編成）の前提条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。
 */
sealed interface ReleaseSingleError {
    /**
     * 選抜対象メンバーに在籍中でない（卒業済みの）メンバーが含まれている。
     *
     * @property memberIds 在籍中でないメンバーのIDの集合
     */
    data class MembersNotActive(val memberIds: Set<MemberId>) : ReleaseSingleError

    /**
     * 選抜対象メンバーに当該グループへ所属していないメンバーが含まれている。
     *
     * @property memberIds 所属が一致しないメンバーのIDの集合
     */
    data class MembersNotInGroup(val memberIds: Set<MemberId>) : ReleaseSingleError

    /**
     * 委譲先の選抜編成（[Formation.create]）の不変条件違反を wrap したもの。
     *
     * 個別バリアントは [FormationError] を参照する。
     */
    data class InvalidSenbatsu(val cause: FormationError) : ReleaseSingleError
}
