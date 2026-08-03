package com.example.api.application.studbook.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Origin
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import java.time.LocalDate
import java.util.UUID
import org.jmolecules.architecture.cqrs.QueryModel

/**
 * 単一軽種馬（by-id 照会）の読み取りモデル（軽量 CQRS / L2。ADR-0031）。
 *
 * 書き込み集約 [com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse] を経由せず、
 * `studbook.blood_horse` と `studbook.horse_inspection` の JOIN から直接組む平坦な DTO。一覧の [BloodHorseView]
 * と異なり、単一リソースの完全表現に要るマイクロチップ番号（審査側が保持）と出自 （sealed [Origin]）を持つ。一覧は件数が多くなりうるため軽量サマリのままとし、詳細はこの by-id
 * で返す。
 *
 * 不変条件を持たないため `data class` でよい。enum・sealed VO はドメイン型をそのまま保持する （wire 契約との decouple は controller 層の
 * `〜Dto` が担う。既存 [BloodHorseView] / `HorseInspectionView` と同じ流儀）。
 *
 * @property id 軽種馬の生 UUID
 * @property registrationNumber 血統登録番号
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号（審査から JOIN で引く）
 * @property origin 出自（内国産／輸入／移行取り込み）
 * @property name 馬名。未命名なら null
 */
@QueryModel
data class BloodHorseDetailView(
    val id: UUID,
    val registrationNumber: String,
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val microchipNumber: String,
    val origin: Origin,
    val name: String?,
)
