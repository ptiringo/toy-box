package com.example.api.application.studbook.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRetirement
import com.example.api.domain.studbook.model.breeding.BreedingRole
import java.util.UUID
import org.jmolecules.architecture.cqrs.QueryModel

/**
 * 繁殖登録の単体照会の読み取りモデル（軽量 CQRS / L2。ADR-0031）。
 *
 * 書き込み集約 [com.example.api.domain.studbook.model.breeding.BreedingRegistration] を経由せず、
 * `studbook.breeding_registration` から直接組む平坦な DTO。不変条件を持たないため `data class` でよい。
 *
 * ロール [role] と供用停止 [retirement] はドメインの enum / 値オブジェクトをそのまま持つ（wire への公開は adapter 層が `〜Dto`
 * へ写す。ADR-0007）。読み取りモデルがドメイン型を参照するのは
 * [com.example.api.application.studbook.horse.BloodHorseDetailView] が sealed な `Origin` を持つのと同じ扱いで、
 * 判別子や共在列の意味づけを adapter 側で再実装しないための選択。
 *
 * @property id 繁殖登録の生 UUID
 * @property registrationNumber 繁殖登録番号
 * @property registeredHorseId 繁殖登録した個体（血統登録済み）の軽種馬の生 UUID
 * @property role 繁殖登録によって付与されたロール（種牡馬／繁殖牝馬）
 * @property retirement 供用停止。供用中なら null
 */
@QueryModel
data class BreedingRegistrationDetailView(
    val id: UUID,
    val registrationNumber: String,
    val registeredHorseId: UUID,
    val role: BreedingRole,
    val retirement: BreedingRetirement?,
)
