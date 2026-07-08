package com.example.api.domain.sakamichi.service.album

import com.example.api.domain.sakamichi.model.album.Album
import com.example.api.domain.sakamichi.model.album.AlbumTitle
import com.example.api.domain.sakamichi.model.group.Group
import com.example.api.domain.sakamichi.model.member.Member
import com.example.api.domain.sakamichi.model.member.MemberId
import com.example.api.domain.sakamichi.model.member.Membership
import com.example.api.domain.sakamichi.model.release.Formation
import com.example.api.domain.sakamichi.model.release.FormationError
import com.example.api.domain.sakamichi.model.release.FormationSlot
import com.example.api.domain.sakamichi.model.release.Position
import com.example.api.domain.sakamichi.model.release.ReleaseNumber
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError

/**
 * アルバムを発売し、選抜（リード曲フォーメーション）を編成する（[Album] を成立させる入口）。
 *
 * 集約をまたぐ前提条件「選抜対象メンバーが当該グループに在籍中であること」を封じ込める（[releaseSingle] と対称）。 検証は (1)
 * 全員が在籍中（[Membership.Active]）、(2) 全員の所属が [group] と一致、(3) 選抜の構造的不変条件 （[Formation.create]
 * へ委譲）の順で行い、違反者が複数いる場合はまとめて返す。
 *
 * @param group 発売元のグループ（選抜対象メンバーの在籍先であること）
 * @param number 作品番号（n 枚目。シングルとは独立採番）
 * @param title リード曲名
 * @param lineup 選抜の編成（立ち位置 × メンバーの並び）
 * @return 成立した [Album]、または前提条件違反を表す [ReleaseAlbumError]
 */
fun releaseAlbum(
    group: Group,
    number: ReleaseNumber,
    title: AlbumTitle,
    lineup: List<Pair<Position, Member>>,
): Result<Album, ReleaseAlbumError> {
    val members = lineup.map { (_, member) -> member }
    val notActive = members.filter { it.membership != Membership.Active }.map { it.id }.toSet()
    val notInGroup = members.filter { it.groupId != group.id }.map { it.id }.toSet()
    return when {
        notActive.isNotEmpty() -> Err(ReleaseAlbumError.MembersNotActive(notActive))
        notInGroup.isNotEmpty() -> Err(ReleaseAlbumError.MembersNotInGroup(notInGroup))
        else ->
            Formation.create(
                    lineup.map { (position, member) -> FormationSlot(position, member.id) }
                )
                .mapError { ReleaseAlbumError.InvalidSenbatsu(it) }
                .map { senbatsu ->
                    Album.create(
                        groupId = group.id,
                        number = number,
                        title = title,
                        senbatsu = senbatsu,
                    )
                }
    }
}

/**
 * アルバム発売（選抜編成）の前提条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。 [releaseSingle] の `ReleaseSingleError`
 * と同形だが、サービスを独立に保つため別型とする。
 */
sealed interface ReleaseAlbumError {
    /** 選抜対象メンバーに在籍中でない（卒業済みの）メンバーが含まれている。 */
    data class MembersNotActive(val memberIds: Set<MemberId>) : ReleaseAlbumError

    /** 選抜対象メンバーに当該グループへ所属していないメンバーが含まれている。 */
    data class MembersNotInGroup(val memberIds: Set<MemberId>) : ReleaseAlbumError

    /** 委譲先の選抜編成（[Formation.create]）の不変条件違反を wrap したもの。 */
    data class InvalidSenbatsu(val cause: FormationError) : ReleaseAlbumError
}
