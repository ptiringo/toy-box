package com.example.api.domain.horseracing.model.horse.bloodhorse

import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 軽種馬ID */
@ValueObject @JvmInline value class BloodHorseId(val value: UUID)

/** 性 */
enum class Sex {
    /** 雄 */
    MALE,

    /** 雌 */
    FEMALE,
}

/**
 * 軽種馬を表す集約ルート。
 *
 * 血統登録（血統及び個体識別を明らかにする登録）の成立によって誕生する個体であり、ライフサイクル全体（繁殖登録・競走馬登録など）を通じて 同一の [BloodHorse]
 * が各ロール（繁殖牝馬・種牡馬・競走馬）を担う。
 *
 * 父・母は別個体（別集約）であり、参照は [BloodHorseId] 経由で表す。父=雄・母=雌・親子の DNA 整合・親仔の品種整合といった前提条件の検証は集約をまたぐため、 ドメインサービス
 * registerInStudBook の責務とする。検証を経た生成のみを許すため、コンストラクタは private とし、生成口 [of] は同モジュールの ドメインサービスからのみ呼べるよう
 * internal とする。
 *
 * @property registrationNumber 血統登録番号
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者
 * @property microchipNumber マイクロチップ番号
 * @property sireId 父（雄）の軽種馬ID
 * @property damId 母（雌）の軽種馬ID
 * @property id 軽種馬ID（自動生成）
 */
@AggregateRoot
class BloodHorse
private constructor(
    val registrationNumber: PedigreeRegistrationNumber,
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: DateOfBirth,
    val breeder: Breeder,
    val microchipNumber: MicrochipNumber,
    val sireId: BloodHorseId,
    val damId: BloodHorseId,
) : Entity<BloodHorseId>() {
    /** 軽種馬ID */
    @field:Identity override val id = BloodHorseId(generateId())

    companion object {
        /**
         * [BloodHorse] を生成する。
         *
         * 父=雄・母=雌・親子の DNA 整合・親仔の品種整合といった前提条件はドメインサービス registerInStudBook が
         * 検証済みである前提のため、この生成口は同モジュールのドメインサービスからのみ呼べるよう internal とする。
         */
        internal fun of(
            entry: StudBookEntry,
            sireId: BloodHorseId,
            damId: BloodHorseId,
            registrationNumber: PedigreeRegistrationNumber,
        ): BloodHorse =
            BloodHorse(
                registrationNumber = registrationNumber,
                sex = entry.sex,
                coatColor = entry.coatColor,
                breedType = entry.breedType,
                dateOfBirth = entry.dateOfBirth,
                breeder = entry.breeder,
                microchipNumber = entry.microchipNumber,
                sireId = sireId,
                damId = damId,
            )
    }
}
