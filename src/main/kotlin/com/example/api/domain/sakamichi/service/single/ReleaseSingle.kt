package com.example.api.domain.sakamichi.service.single

import com.example.api.domain.sakamichi.model.group.Group
import com.example.api.domain.sakamichi.model.member.Member
import com.example.api.domain.sakamichi.model.member.MemberId
import com.example.api.domain.sakamichi.model.member.Membership
import com.example.api.domain.sakamichi.model.release.Formation
import com.example.api.domain.sakamichi.model.release.FormationError
import com.example.api.domain.sakamichi.model.release.FormationSlot
import com.example.api.domain.sakamichi.model.release.NonSenbatsuTrack
import com.example.api.domain.sakamichi.model.release.Position
import com.example.api.domain.sakamichi.model.release.ReleaseNumber
import com.example.api.domain.sakamichi.model.release.TrackNumber
import com.example.api.domain.sakamichi.model.release.Tracklist
import com.example.api.domain.sakamichi.model.single.Single
import com.example.api.domain.sakamichi.model.single.SingleError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError

/**
 * シングルを発売し、選抜を編成する（[Single] を成立させる入口）。
 *
 * **集約をまたぐ前提条件「選抜対象メンバーが当該グループに在籍中であること」を封じ込める**（studbook の registerFoal 型。#364 段階 4）。 検証は (1)
 * 全員が在籍中（[Membership.Active]）、(2) 全員の所属が [group] と一致、(3) 選抜と非選抜曲が排他、 (4)
 * 各編成の構造的不変条件（[Formation.create] へ委譲）、(5) トラック整合（[Single.create] へ委譲）の順で行う。
 *
 * トラックリスト自体は集約内で完結するデータ（曲名＋番号）のため、検証済みの [Tracklist] を引数で受け取る。
 *
 * @param group 発売元のグループ（選抜対象メンバーの在籍先であること）
 * @param number 作品番号（n 枚目）
 * @param tracklist 全収録曲
 * @param headlineTrackNumber 見出し曲（表題曲）のトラック番号
 * @param lineup 選抜の編成（立ち位置 × メンバーの並び）
 * @param nonSenbatsuTracks 非選抜曲の編成入力の並び（トラック番号 to 立ち位置つきメンバーの並び）。空なら非選抜曲なし（全員選抜）
 * @return 成立した [Single]、または前提条件違反を表す [ReleaseSingleError]
 */
fun releaseSingle(
    group: Group,
    number: ReleaseNumber,
    tracklist: Tracklist,
    headlineTrackNumber: TrackNumber,
    lineup: List<Pair<Position, Member>>,
    nonSenbatsuTracks: List<Pair<TrackNumber, List<Pair<Position, Member>>>> = emptyList(),
): Result<Single, ReleaseSingleError> {
    val nonSenbatsuLineups = nonSenbatsuTracks.flatMap { (_, lineup) -> lineup }
    val members = (lineup + nonSenbatsuLineups).map { (_, member) -> member }
    val notActive = members.filter { it.membership != Membership.Active }.map { it.id }.toSet()
    val notInGroup = members.filter { it.groupId != group.id }.map { it.id }.toSet()
    val senbatsuIds = lineup.map { (_, member) -> member.id }.toSet()
    val nonSenbatsuIds = nonSenbatsuLineups.map { (_, member) -> member.id }.toSet()
    val inBoth = senbatsuIds intersect nonSenbatsuIds
    return when {
        notActive.isNotEmpty() -> Err(ReleaseSingleError.MembersNotActive(notActive))
        notInGroup.isNotEmpty() -> Err(ReleaseSingleError.MembersNotInGroup(notInGroup))
        inBoth.isNotEmpty() -> Err(ReleaseSingleError.MembersInBothFormations(inBoth))
        else ->
            Formation.create(
                    lineup.map { (position, member) -> FormationSlot(position, member.id) }
                )
                .mapError { ReleaseSingleError.InvalidSenbatsu(it) }
                .andThen { senbatsu ->
                    buildNonSenbatsuTracks(nonSenbatsuTracks).andThen { built ->
                        Single.create(
                                groupId = group.id,
                                number = number,
                                tracklist = tracklist,
                                headlineTrackNumber = headlineTrackNumber,
                                senbatsu = senbatsu,
                                nonSenbatsuTracks = built,
                            )
                            .mapError { ReleaseSingleError.InvalidTrackComposition(it) }
                    }
                }
    }
}

/**
 * 非選抜曲群の編成を構築する。各曲の [Formation.create] を順に適用し、最初の違反で打ち切る。
 *
 * @param inputs 非選抜曲の編成入力の並び（トラック番号 to 立ち位置つきメンバーの並び）
 * @return 構築済みの [NonSenbatsuTrack] の並び、または不変条件違反を wrap した [ReleaseSingleError]
 */
private fun buildNonSenbatsuTracks(
    inputs: List<Pair<TrackNumber, List<Pair<Position, Member>>>>
): Result<List<NonSenbatsuTrack>, ReleaseSingleError> {
    val initial: Result<List<NonSenbatsuTrack>, ReleaseSingleError> = Ok(emptyList())
    return inputs.fold(initial) { acc, (trackNumber, lineup) ->
        acc.andThen { built ->
            Formation.create(
                    lineup.map { (position, member) -> FormationSlot(position, member.id) }
                )
                .mapError { ReleaseSingleError.InvalidNonSenbatsuTrack(trackNumber, it) }
                .map { formation -> built + NonSenbatsuTrack(trackNumber, formation) }
        }
    }
}

/**
 * シングル発売（選抜編成）の前提条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。
 */
sealed interface ReleaseSingleError {
    /** 選抜対象メンバーに在籍中でない（卒業済みの）メンバーが含まれている。 */
    data class MembersNotActive(val memberIds: Set<MemberId>) : ReleaseSingleError

    /** 選抜対象メンバーに当該グループへ所属していないメンバーが含まれている。 */
    data class MembersNotInGroup(val memberIds: Set<MemberId>) : ReleaseSingleError

    /** 委譲先の選抜編成（[Formation.create]）の不変条件違反を wrap したもの。 */
    data class InvalidSenbatsu(val cause: FormationError) : ReleaseSingleError

    /** 同一メンバーが選抜と非選抜曲の両方の編成に含まれている（同一作品で両立しない）。 */
    data class MembersInBothFormations(val memberIds: Set<MemberId>) : ReleaseSingleError

    /** 委譲先の非選抜曲編成（[Formation.create]）の不変条件違反を、どの曲かと共に wrap したもの。 */
    data class InvalidNonSenbatsuTrack(val trackNumber: TrackNumber, val cause: FormationError) :
        ReleaseSingleError

    /** 委譲先の [Single.create] のトラック整合違反（[SingleError]）を wrap したもの。 */
    data class InvalidTrackComposition(val cause: SingleError) : ReleaseSingleError
}
