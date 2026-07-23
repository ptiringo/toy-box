package com.example.api.application.studbook.horse

import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import java.time.LocalDate
import java.util.UUID
import org.jmolecules.architecture.cqrs.QueryModel

/**
 * 軽種馬一覧の読み取りモデル（軽量 CQRS / L2。ADR-0031）。
 *
 * 書き込み集約 [com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse] を経由せず、
 * `studbook.blood_horse` から直接組む平坦な DTO。一覧に要る最小限の属性だけを持ち、マイクロチップ（審査側が保持）や
 * 出自（origin）の入れ子は持たない。不変条件を持たないため `data class` でよい。
 *
 * @property id 軽種馬の生 UUID
 * @property registrationNumber 血統登録番号
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者名
 * @property name 馬名。未命名なら null
 */
@QueryModel
data class BloodHorseView(
    val id: UUID,
    val registrationNumber: String,
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val name: String?,
)
