package com.example.api.domain.sakamichi.model.single

import com.example.api.domain.sakamichi.model.member.MemberId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 選抜の 1 枠。どの立ち位置（[position]）に誰（[memberId]）が立つか。
 *
 * @property position 立ち位置
 * @property memberId 立つメンバーのID
 */
@ValueObject data class SenbatsuSlot(val position: Position, val memberId: MemberId)

/**
 * 選抜編成（[Senbatsu.create]）の不変条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。
 */
sealed interface SenbatsuError {
    /**
     * 同一メンバーが複数の立ち位置に重複して選ばれている。
     *
     * @property memberIds 重複して選ばれているメンバーのIDの集合
     */
    data class DuplicateMember(val memberIds: Set<MemberId>) : SenbatsuError

    /**
     * 同一の立ち位置に複数のメンバーが割り当てられている（1 つの立ち位置に立てるのは 1 人）。
     *
     * @property positions 定員を超えて割り当てられた立ち位置の集合
     */
    data class PositionOverCapacity(val positions: Set<Position>) : SenbatsuError

    /** センターが不在（選抜にはセンターを 1 人以上置く）。 */
    data object CenterMissing : SenbatsuError

    /**
     * センターが 3 人以上いる（W センターまで＝上限 2 人）。
     *
     * @property memberIds センターに割り当てられたメンバー全員のIDの集合（違反の全体像）
     */
    data class TooManyCenters(val memberIds: Set<MemberId>) : SenbatsuError
}

/**
 * 選抜。シングル表題曲を歌う選ばれたメンバーの集合と、そのフォーメーション（立ち位置の割り当て）。
 *
 * 選抜はグループの恒久属性ではなくシングル単位の一時的編成であり（sakamichi-sources §4）、[Single] 集約が VO として内包する。メンバーは別集約のため
 * [MemberId] 経由の ID 参照で保持する。不変条件 （同一メンバーの重複なし・`Center` 以外の立ち位置の定員 1 人・センター 1〜2 人）は生成ファクトリ [create]
 * が検証する（ADR-0014）。
 *
 * 「選抜対象メンバーが当該グループに在籍中であること」は既存の Member 集約群をまたぐ前提条件のため 本 VO では守らない（ドメインサービスへ封じ込める。#551）。
 *
 * @property slots 選抜の枠（立ち位置 × メンバー）の並び
 */
@ValueObject
@ConsistentCopyVisibility
data class Senbatsu private constructor(val slots: List<SenbatsuSlot>) {
    /** センターに立つメンバーのIDの集合（W センター時は 2 人）。 */
    val centers: Set<MemberId>
        get() = slots.filter { it.position == Position.Center }.map { it.memberId }.toSet()

    /** 選抜されたメンバーのIDの集合。 */
    val memberIds: Set<MemberId>
        get() = slots.map { it.memberId }.toSet()

    companion object {
        /**
         * 不変条件（同一メンバーの重複なし・`Center` 以外の立ち位置の定員 1 人・センター 1〜2 人）を 検証して [Senbatsu] を生成する。
         *
         * @param slots 選抜の枠（立ち位置 × メンバー）の並び
         * @return 編成された [Senbatsu]、または不変条件違反を表す [SenbatsuError]
         */
        fun create(slots: List<SenbatsuSlot>): Result<Senbatsu, SenbatsuError> {
            val duplicateMembers = slots.groupBy { it.memberId }.filterValues { it.size > 1 }.keys
            val overCapacityPositions =
                slots
                    .filter { it.position != Position.Center }
                    .groupBy { it.position }
                    .filterValues { it.size > 1 }
                    .keys
            val centerMemberIds =
                slots.filter { it.position == Position.Center }.map { it.memberId }.toSet()
            return when {
                duplicateMembers.isNotEmpty() ->
                    Err(SenbatsuError.DuplicateMember(duplicateMembers))
                overCapacityPositions.isNotEmpty() ->
                    Err(SenbatsuError.PositionOverCapacity(overCapacityPositions))
                centerMemberIds.isEmpty() -> Err(SenbatsuError.CenterMissing)
                centerMemberIds.size > 2 -> Err(SenbatsuError.TooManyCenters(centerMemberIds))
                else -> Ok(Senbatsu(slots))
            }
        }
    }
}
