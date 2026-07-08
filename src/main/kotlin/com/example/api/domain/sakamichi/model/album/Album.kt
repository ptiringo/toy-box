package com.example.api.domain.sakamichi.model.album

import com.example.api.domain.sakamichi.model.group.GroupId
import com.example.api.domain.sakamichi.model.release.Formation
import com.example.api.domain.sakamichi.model.release.ReleaseNumber
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** アルバムID */
@ValueObject @JvmInline value class AlbumId(val value: UUID)

/**
 * アルバム（グループが発表する作品）を表す集約ルート。
 *
 * シングル（[com.example.api.domain.sakamichi.model.single.Single]）と構造は同一だが、
 * 集約としての同一性（[AlbumId]）・採番系列・ライフサイクルは別。 リード曲のフォーメーション（[Formation]）は アルバム単位の一時的編成のため VO
 * として内包し、発表元グループは [GroupId] 経由の ID 参照で保持する。
 *
 * 状態はイミュータブルに扱う（ADR-0009）。コンストラクタは private とし、生成は [create] に限る。
 *
 * @property id アルバムID（生成時に自動採番）
 * @property groupId 発表元グループのID
 * @property number 作品番号（n 枚目。シングルとは独立採番）
 * @property title リード曲名
 * @property senbatsu 選抜（リード曲を歌う編成）
 */
@AggregateRoot
class Album
private constructor(
    @field:Identity override val id: AlbumId,
    val groupId: GroupId,
    val number: ReleaseNumber,
    val title: AlbumTitle,
    val senbatsu: Formation,
) : Entity<AlbumId>() {
    companion object {
        /**
         * アルバムを生成する。
         *
         * 検証済みの VO を受け取るため失敗せず、[Album] をそのまま返す。選抜の構造的不変条件は [Formation.create]
         * が、選抜対象メンバーの在籍検証はドメインサービス（releaseAlbum）が担う。
         *
         * @param groupId 発表元グループのID
         * @param number 作品番号（n 枚目）
         * @param title リード曲名
         * @param senbatsu 選抜
         */
        fun create(
            groupId: GroupId,
            number: ReleaseNumber,
            title: AlbumTitle,
            senbatsu: Formation,
        ): Album =
            Album(
                id = AlbumId(generateId()),
                groupId = groupId,
                number = number,
                title = title,
                senbatsu = senbatsu,
            )
    }
}
