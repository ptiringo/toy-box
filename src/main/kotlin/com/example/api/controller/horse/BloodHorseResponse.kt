package com.example.api.controller.horse

import com.example.api.application.studbook.horse.BloodHorseDetailView
import com.example.api.application.studbook.horse.RegisteredBloodHorse
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

/**
 * 軽種馬リソースの表現（HTTP 契約）。
 *
 * 軽種馬リソースに対する操作（血統登録の Create、馬名登録の `:registerName` カスタムメソッドなど）は、
 * [AIP-133](https://google.aip.dev/133) / [AIP-136](https://google.aip.dev/136) に倣い
 * 一律でこのリソース表現全体を返す。父・母は登録済みの軽種馬IDで参照する。
 *
 * 出自（内国産か輸入か）は相互排他なので、入れ子オブジェクト [origin] に discriminated union（`type` 判別子つき）として
 * まとめる。リソース全体は単一表現（ADR-0008）を維持し、相互排他な部分だけを `oneOf` にする（ADR-0020）。
 *
 * @property id 軽種馬の生 UUID
 * @property registrationNumber 血統登録番号
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号
 * @property origin 出自（内国産＝父母 UUID／輸入＝原産国・揚陸日）
 * @property name 馬名。未命名なら null
 */
@Suppress("LongParameterList") // resource 全体を返すため項目数が多いのは必然
@Schema(description = "軽種馬リソースの表現")
data class BloodHorseResponse(
    val id: UUID,
    val registrationNumber: String,
    val sex: SexDto,
    val coatColor: CoatColorDto,
    val breedType: BreedTypeDto,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val microchipNumber: String,
    val origin: OriginDto,
    val name: String?,
)

/** [RegisteredBloodHorse]（軽種馬＋審査）を軽種馬リソースの表現へ変換する。マイクロチップは審査から引く。 */
fun RegisteredBloodHorse.toResponse(): BloodHorseResponse =
    BloodHorseResponse(
        id = bloodHorse.id.value,
        registrationNumber = bloodHorse.registrationNumber.value,
        sex = bloodHorse.sex.toApi(),
        coatColor = bloodHorse.coatColor.toApi(),
        breedType = bloodHorse.breedType.toApi(),
        dateOfBirth = bloodHorse.dateOfBirth.value,
        breeder = bloodHorse.breeder.name,
        microchipNumber = inspection.microchipNumber.value,
        origin = bloodHorse.origin.toApi(),
        name = bloodHorse.name?.value,
    )

/**
 * 読み取りモデル [BloodHorseDetailView] を軽種馬リソースの表現へ変換する（読み取り経路。Get の成功レスポンス）。
 *
 * 書き込み経路の [RegisteredBloodHorse] 版と同じ [BloodHorseResponse] を組む。全操作で単一のリソース表現を
 * 共用する方針（ADR-0008）に従い、Get 専用の DTO は作らない。
 */
fun BloodHorseDetailView.toResponse(): BloodHorseResponse =
    BloodHorseResponse(
        id = id,
        registrationNumber = registrationNumber,
        sex = sex.toApi(),
        coatColor = coatColor.toApi(),
        breedType = breedType.toApi(),
        dateOfBirth = dateOfBirth,
        breeder = breeder,
        microchipNumber = microchipNumber,
        origin = origin.toApi(),
        name = name,
    )
