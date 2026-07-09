package com.example.api.domain.sakamichi.service.album

import com.example.api.domain.sakamichi.model.album.Album
import com.example.api.domain.sakamichi.model.album.AlbumError
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
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError

/** 編成 1 つ分の入力＝立ち位置つきメンバーの並び（選抜・非選抜曲共通）。 */
private typealias Lineup = List<Pair<Position, Member>>

/**
 * アルバムを発売し、選抜（リード曲フォーメーション）を編成する（[Album] を成立させる入口）。
 *
 * **集約をまたぐ前提条件「選抜対象メンバーが当該グループに在籍中であること」を封じ込める**（[releaseSingle] と対称）。 検証は (1)
 * 全員が在籍中（[Membership.Active]）、(2) 全員の所属が [group] と一致、(3) 選抜と非選抜曲が排他、 (4)
 * 各編成の構造的不変条件（[Formation.create] へ委譲）、(5) トラック整合（[Album.create] へ委譲）の順で行う。
 *
 * @param group 発売元のグループ（選抜対象メンバーの在籍先であること）
 * @param number 作品番号（n 枚目。シングルとは独立採番）
 * @param tracklist 全収録曲
 * @param headlineTrackNumber 見出し曲（リード曲）のトラック番号
 * @param lineup 選抜の編成（立ち位置 × メンバーの並び）
 * @param nonSenbatsuTracks 非選抜曲の編成入力の並び（トラック番号 to 立ち位置つきメンバーの並び）。空なら非選抜曲なし（全員選抜）
 * @return 成立した [Album]、または前提条件違反を表す [ReleaseAlbumError]
 */
fun releaseAlbum(
    group: Group,
    number: ReleaseNumber,
    tracklist: Tracklist,
    headlineTrackNumber: TrackNumber,
    lineup: Lineup,
    nonSenbatsuTracks: List<Pair<TrackNumber, Lineup>> = emptyList(),
): Result<Album, ReleaseAlbumError> {
    val nonSenbatsuLineups = nonSenbatsuTracks.flatMap { (_, lineup) -> lineup }
    val members = (lineup + nonSenbatsuLineups).map { (_, member) -> member }
    val notActive = members.filter { it.membership != Membership.Active }.map { it.id }.toSet()
    val notInGroup = members.filter { it.groupId != group.id }.map { it.id }.toSet()
    val senbatsuIds = lineup.map { (_, member) -> member.id }.toSet()
    val nonSenbatsuIds = nonSenbatsuLineups.map { (_, member) -> member.id }.toSet()
    val inBoth = senbatsuIds intersect nonSenbatsuIds
    return when {
        notActive.isNotEmpty() -> Err(ReleaseAlbumError.MembersNotActive(notActive))
        notInGroup.isNotEmpty() -> Err(ReleaseAlbumError.MembersNotInGroup(notInGroup))
        inBoth.isNotEmpty() -> Err(ReleaseAlbumError.MembersInBothFormations(inBoth))
        else ->
            Formation.create(
                    lineup.map { (position, member) -> FormationSlot(position, member.id) }
                )
                .mapError { ReleaseAlbumError.InvalidSenbatsu(it) }
                .andThen { senbatsu ->
                    buildNonSenbatsuTracks(nonSenbatsuTracks).andThen { built ->
                        Album.create(
                                groupId = group.id,
                                number = number,
                                tracklist = tracklist,
                                headlineTrackNumber = headlineTrackNumber,
                                senbatsu = senbatsu,
                                nonSenbatsuTracks = built,
                            )
                            .mapError { ReleaseAlbumError.InvalidTrackComposition(it) }
                    }
                }
    }
}

/**
 * 非選抜曲群の編成を構築する。各曲の [Formation.create] を順に適用し、最初の違反で打ち切る。
 *
 * @param inputs 非選抜曲の編成入力の並び（トラック番号 to 立ち位置つきメンバーの並び）
 * @return 構築済みの [NonSenbatsuTrack] の並び、または不変条件違反を wrap した [ReleaseAlbumError]
 */
private fun buildNonSenbatsuTracks(
    inputs: List<Pair<TrackNumber, Lineup>>
): Result<List<NonSenbatsuTrack>, ReleaseAlbumError> {
    val initial: Result<List<NonSenbatsuTrack>, ReleaseAlbumError> = Ok(emptyList())
    return inputs.fold(initial) { acc, (trackNumber, lineup) ->
        acc.andThen { built ->
            Formation.create(
                    lineup.map { (position, member) -> FormationSlot(position, member.id) }
                )
                .mapError { ReleaseAlbumError.InvalidNonSenbatsuTrack(trackNumber, it) }
                .map { formation -> built + NonSenbatsuTrack(trackNumber, formation) }
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

    /** 同一メンバーが選抜と非選抜曲の両方の編成に含まれている（同一作品で両立しない）。 */
    data class MembersInBothFormations(val memberIds: Set<MemberId>) : ReleaseAlbumError

    /** 委譲先の非選抜曲編成（[Formation.create]）の不変条件違反を、どの曲かと共に wrap したもの。 */
    data class InvalidNonSenbatsuTrack(val trackNumber: TrackNumber, val cause: FormationError) :
        ReleaseAlbumError

    /** 委譲先の [Album.create] のトラック整合違反（[AlbumError]）を wrap したもの。 */
    data class InvalidTrackComposition(val cause: AlbumError) : ReleaseAlbumError
}
