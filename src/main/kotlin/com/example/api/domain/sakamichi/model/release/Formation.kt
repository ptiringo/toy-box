package com.example.api.domain.sakamichi.model.release

import com.example.api.domain.sakamichi.model.member.MemberId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 編成の 1 枠。どの立ち位置（[position]）に誰（[memberId]）が立つか。
 *
 * @property position 立ち位置
 * @property memberId 立つメンバーのID
 */
@ValueObject data class FormationSlot(val position: Position, val memberId: MemberId)

/**
 * 編成（[Formation.create]）の不変条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。
 */
sealed interface FormationError {
    /**
     * 同一メンバーが複数の立ち位置に重複して立っている。
     *
     * @property memberIds 重複して立っているメンバーのIDの集合
     */
    data class DuplicateMember(val memberIds: Set<MemberId>) : FormationError

    /**
     * 同一の立ち位置に複数のメンバーが割り当てられている（1 つの立ち位置に立てるのは 1 人）。
     *
     * @property positions 定員を超えて割り当てられた立ち位置の集合
     */
    data class PositionOverCapacity(val positions: Set<Position>) : FormationError

    /** センターが不在（編成にはセンターを 1 人以上置く）。 */
    data object CenterMissing : FormationError

    /**
     * センターが 3 人以上いる（W センターまで＝上限 2 人）。
     *
     * @property memberIds センターに割り当てられたメンバー全員のIDの集合（違反の全体像）
     */
    data class TooManyCenters(val memberIds: Set<MemberId>) : FormationError
}

/**
 * 編成。作品（シングル/アルバム）でメンバーが立つ立ち位置の割り当てとセンター構成を表す値オブジェクト。
 *
 * 選抜（表題曲/リード曲）にも非選抜（アンダー/BACKS/ひなた坂）にも使う中立の構造で、作品集約（`Single` / `Album`）が
 * 「どのロールの編成か」をフィールド（`senbatsu` / `nonSenbatsu`）で表して内包する。編成はグループの恒久属性ではなく
 * 作品単位の一時的編成であり（sakamichi-sources §4）、メンバーは別集約のため [MemberId] 経由の ID 参照で保持する。
 * 不変条件（同一メンバーの重複なし・`Center` 以外の立ち位置の定員 1 人・センター 1〜2 人）は生成ファクトリ [create] が検証する（ADR-0014）。
 *
 * 「編成対象メンバーが当該グループに在籍中であること」「選抜と非選抜が排他であること」は集約をまたぐ前提条件のため 本 VO では守らない（ドメインサービスへ封じ込める。#551 / #556）。
 *
 * @property slots 編成の枠（立ち位置 × メンバー）の並び
 */
@ValueObject
@ConsistentCopyVisibility
data class Formation private constructor(val slots: List<FormationSlot>) {
    /** センターに立つメンバーのIDの集合（W センター時は 2 人）。 */
    val centers: Set<MemberId>
        get() = slots.filter { it.position == Position.Center }.map { it.memberId }.toSet()

    /** 編成されたメンバーのIDの集合。 */
    val memberIds: Set<MemberId>
        get() = slots.map { it.memberId }.toSet()

    companion object {
        /**
         * 不変条件（同一メンバーの重複なし・`Center` 以外の立ち位置の定員 1 人・センター 1〜2 人）を 検証して [Formation] を生成する。
         *
         * @param slots 編成の枠（立ち位置 × メンバー）の並び
         * @return 編成された [Formation]、または不変条件違反を表す [FormationError]
         */
        fun create(slots: List<FormationSlot>): Result<Formation, FormationError> {
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
                    Err(FormationError.DuplicateMember(duplicateMembers))
                overCapacityPositions.isNotEmpty() ->
                    Err(FormationError.PositionOverCapacity(overCapacityPositions))
                centerMemberIds.isEmpty() -> Err(FormationError.CenterMissing)
                centerMemberIds.size > 2 -> Err(FormationError.TooManyCenters(centerMemberIds))
                else -> Ok(Formation(slots))
            }
        }
    }
}
