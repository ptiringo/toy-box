package com.example.api.domain.sakamichi.model.single

import com.example.api.domain.sakamichi.model.group.GroupId
import com.example.api.domain.sakamichi.model.release.Formation
import com.example.api.domain.sakamichi.model.release.NonSenbatsuTrack
import com.example.api.domain.sakamichi.model.release.ReleaseNumber
import com.example.api.domain.sakamichi.model.release.TrackNumber
import com.example.api.domain.sakamichi.model.release.TrackTitle
import com.example.api.domain.sakamichi.model.release.Tracklist
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** シングルID */
@ValueObject @JvmInline value class SingleId(val value: UUID)

/**
 * シングル生成（[Single.create]）の不変条件違反。
 *
 * 失敗のしかたが増える余地を残すため sealed interface とする。
 */
sealed interface SingleError {
    /** 見出しトラック（表題曲）がトラックリストに存在しない。 */
    data object HeadlineTrackNotInTracklist : SingleError

    /** 非選抜曲のトラックがトラックリストに存在しない。 */
    data class NonSenbatsuTrackNotInTracklist(val trackNumbers: Set<TrackNumber>) : SingleError

    /** 非選抜曲のトラックが見出しトラックと一致している（非選抜曲は表題曲ではない）。 */
    data class NonSenbatsuTrackIsHeadline(val trackNumbers: Set<TrackNumber>) : SingleError

    /** 同一トラックが非選抜曲として重複指定されている。 */
    data class DuplicateNonSenbatsuTrack(val trackNumbers: Set<TrackNumber>) : SingleError
}

/**
 * シングル（グループが発表する作品）を表す集約ルート。
 *
 * 全収録曲を [Tracklist] として通し番号で内包し、そのうち見出し曲（表題曲）を [headlineTrackNumber] で指す。
 * 選抜（[Formation]）は見出し曲を歌う編成、非選抜曲（[nonSenbatsuTracks]）はアンダー等の編成で、いずれも作品単位の 一時的編成のため本集約が VO
 * として内包する（sakamichi-sources §4）。発表元グループは別集約のため [GroupId] 経由の ID 参照で保持する。
 *
 * 状態はイミュータブルに扱う（ADR-0009）。コンストラクタは private とし、生成は [create] に限る。
 *
 * @property id シングルID（生成時に自動採番）
 * @property groupId 発表元グループのID
 * @property number 作品番号（n 枚目。グループ内での連番）
 * @property tracklist 全収録曲（通し番号 1..n）
 * @property headlineTrackNumber 見出し曲（表題曲）のトラック番号
 * @property senbatsu 選抜（表題曲を歌う編成）
 * @property nonSenbatsuTracks 非選抜曲の編成の並び（アンダー等）。無い場合は空リスト（全員選抜）
 */
@AggregateRoot
class Single
private constructor(
    @field:Identity override val id: SingleId,
    val groupId: GroupId,
    val number: ReleaseNumber,
    val tracklist: Tracklist,
    val headlineTrackNumber: TrackNumber,
    val senbatsu: Formation,
    val nonSenbatsuTracks: List<NonSenbatsuTrack>,
) : Entity<SingleId>() {
    /** 見出し曲（表題曲）の曲名。トラックリストから [headlineTrackNumber] で引く。 */
    val headlineTitle: TrackTitle
        get() = tracklist.tracks.first { it.number == headlineTrackNumber }.title

    companion object {
        /**
         * シングルを生成する。
         *
         * 集約不変条件「見出しトラックがトラックリストに存在する」「非選抜曲のトラックがトラックリストに存在する」
         * 「非選抜曲のトラックが見出しトラックと重複しない」「非選抜曲のトラックが重複しない」を検証する（複数の 検証済み VO 間の関係で、どの VO
         * 単体でも守れないため集約ファクトリが所有する。ADR-0014）。 選抜対象メンバーの在籍検証はドメインサービス（releaseSingle）が担う。
         *
         * @param groupId 発表元グループのID
         * @param number 作品番号（n 枚目）
         * @param tracklist 全収録曲
         * @param headlineTrackNumber 見出し曲のトラック番号
         * @param senbatsu 選抜
         * @param nonSenbatsuTracks 非選抜曲の編成の並び（アンダー等）。全員選抜の場合は指定しない（空リスト）
         * @return 生成した [Single]、またはトラック整合違反を表す [SingleError]
         */
        fun create(
            groupId: GroupId,
            number: ReleaseNumber,
            tracklist: Tracklist,
            headlineTrackNumber: TrackNumber,
            senbatsu: Formation,
            nonSenbatsuTracks: List<NonSenbatsuTrack> = emptyList(),
        ): Result<Single, SingleError> {
            val trackNumbers = tracklist.tracks.map { it.number }.toSet()
            val nonSenbatsuNumbers = nonSenbatsuTracks.map { it.trackNumber }
            val notInTracklist = nonSenbatsuNumbers.filterNot { it in trackNumbers }.toSet()
            val headlineDuplicated = nonSenbatsuNumbers.filter { it == headlineTrackNumber }.toSet()
            val duplicated = nonSenbatsuNumbers.groupBy { it }.filterValues { it.size > 1 }.keys
            return when {
                headlineTrackNumber !in trackNumbers -> Err(SingleError.HeadlineTrackNotInTracklist)
                notInTracklist.isNotEmpty() ->
                    Err(SingleError.NonSenbatsuTrackNotInTracklist(notInTracklist))
                headlineDuplicated.isNotEmpty() ->
                    Err(SingleError.NonSenbatsuTrackIsHeadline(headlineDuplicated))
                duplicated.isNotEmpty() -> Err(SingleError.DuplicateNonSenbatsuTrack(duplicated))
                else ->
                    Ok(
                        Single(
                            id = SingleId(generateId()),
                            groupId = groupId,
                            number = number,
                            tracklist = tracklist,
                            headlineTrackNumber = headlineTrackNumber,
                            senbatsu = senbatsu,
                            nonSenbatsuTracks = nonSenbatsuTracks,
                        )
                    )
            }
        }
    }
}
