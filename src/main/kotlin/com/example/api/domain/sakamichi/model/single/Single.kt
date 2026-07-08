package com.example.api.domain.sakamichi.model.single

import com.example.api.domain.sakamichi.model.group.GroupId
import com.example.api.domain.sakamichi.model.release.Formation
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
}

/**
 * シングル（グループが発表する作品）を表す集約ルート。
 *
 * 全収録曲を [Tracklist] として通し番号で内包し、そのうち見出し曲（表題曲）を [headlineTrackNumber] で指す。
 * 選抜（[Formation]）は見出し曲を歌う編成、非選抜（[nonSenbatsu]）はアンダー等の編成で、いずれも作品単位の 一時的編成のため本集約が VO
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
 * @property nonSenbatsu 非選抜編成（アンダー等）。無い場合は null（全員選抜）
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
    val nonSenbatsu: Formation?,
) : Entity<SingleId>() {
    /** 見出し曲（表題曲）の曲名。トラックリストから [headlineTrackNumber] で引く。 */
    val headlineTitle: TrackTitle
        get() = tracklist.tracks.first { it.number == headlineTrackNumber }.title

    companion object {
        /**
         * シングルを生成する。
         *
         * 集約不変条件「見出しトラックがトラックリストに存在する」を検証する（2 つの検証済み VO 間の関係で、 どちらの VO
         * 単体でも守れないため集約ファクトリが所有する。ADR-0014）。選抜対象メンバーの在籍検証は ドメインサービス（releaseSingle）が担う。
         *
         * @param groupId 発表元グループのID
         * @param number 作品番号（n 枚目）
         * @param tracklist 全収録曲
         * @param headlineTrackNumber 見出し曲のトラック番号
         * @param senbatsu 選抜
         * @param nonSenbatsu 非選抜編成（アンダー等）。全員選抜の場合は指定しない（null）
         * @return 生成した [Single]、または見出し不在を表す [SingleError]
         */
        fun create(
            groupId: GroupId,
            number: ReleaseNumber,
            tracklist: Tracklist,
            headlineTrackNumber: TrackNumber,
            senbatsu: Formation,
            nonSenbatsu: Formation? = null,
        ): Result<Single, SingleError> {
            val headlineExists = tracklist.tracks.any { it.number == headlineTrackNumber }
            return if (!headlineExists) {
                Err(SingleError.HeadlineTrackNotInTracklist)
            } else {
                Ok(
                    Single(
                        id = SingleId(generateId()),
                        groupId = groupId,
                        number = number,
                        tracklist = tracklist,
                        headlineTrackNumber = headlineTrackNumber,
                        senbatsu = senbatsu,
                        nonSenbatsu = nonSenbatsu,
                    )
                )
            }
        }
    }
}
