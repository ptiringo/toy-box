package com.example.api.domain.horseracing.model.horse.bloodhorse

import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 軽種馬ID */
@ValueObject @JvmInline value class BloodHorseId(val value: UUID)

/**
 * 既に命名済みの軽種馬へ重ねて命名しようとした。
 *
 * 馬名登録は血統登録済みの個体に一度だけ行えるドメインイベントであり、二重命名は不変条件違反。
 *
 * @property currentName 既に付与されている馬名
 */
data class HorseAlreadyNamed(val currentName: HorseName)

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
 * 状態はイミュータブルに扱う。出生時は血統登録のみで馬名を持たず（[name] は null）、後日の「馬名登録」で一度だけ命名される。 命名は [assignName] が 馬名を持つ新しい
 * [BloodHorse] を返すことで表し、同一性（[id]）は引き継ぐ。元のインスタンスは変更しない。
 *
 * @property id 軽種馬ID（生成時に自動採番し、以後の写像でも引き継ぐ）
 * @property registrationNumber 血統登録番号
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者
 * @property microchipNumber マイクロチップ番号
 * @property sireId 父（雄）の軽種馬ID
 * @property damId 母（雌）の軽種馬ID
 * @property name 馬名。未命名なら null。命名は [assignName] でのみ行う
 */
@AggregateRoot
class BloodHorse
private constructor(
    @field:Identity override val id: BloodHorseId,
    val registrationNumber: PedigreeRegistrationNumber,
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: DateOfBirth,
    val breeder: Breeder,
    val microchipNumber: MicrochipNumber,
    val sireId: BloodHorseId,
    val damId: BloodHorseId,
    val name: HorseName?,
) : Entity<BloodHorseId>() {
    /**
     * 馬名を登録した新しい [BloodHorse] を返す。
     *
     * 馬名登録は血統登録済みの個体に一度だけ行えるドメインイベント。既に命名済みの個体への再命名は 不変条件違反として [HorseAlreadyNamed] を返し、写像を行わない（元の
     * [BloodHorse] も不変）。成功時は [name] のみ差し替え、[id] を含む他の属性は引き継ぐ。
     *
     * @param horseName 付与する馬名
     * @return 命名済みの新しい [BloodHorse]、既に命名済みなら [HorseAlreadyNamed]
     */
    fun assignName(horseName: HorseName): Result<BloodHorse, HorseAlreadyNamed> {
        val current = name
        return if (current != null) {
            Err(HorseAlreadyNamed(current))
        } else {
            Ok(copy(name = horseName))
        }
    }

    /** [id] と未指定の属性を引き継ぎ、指定された属性だけを差し替えた新しい [BloodHorse] を返す。 */
    private fun copy(name: HorseName? = this.name): BloodHorse =
        BloodHorse(
            id = id,
            registrationNumber = registrationNumber,
            sex = sex,
            coatColor = coatColor,
            breedType = breedType,
            dateOfBirth = dateOfBirth,
            breeder = breeder,
            microchipNumber = microchipNumber,
            sireId = sireId,
            damId = damId,
            name = name,
        )

    companion object {
        /**
         * [BloodHorse] を生成する。
         *
         * 父=雄・母=雌・親子の DNA 整合・親仔の品種整合といった前提条件はドメインサービス registerInStudBook が
         * 検証済みである前提のため、この生成口は同モジュールのドメインサービスからのみ呼べるよう internal とする。 生成直後は未命名（[name] は null）。
         */
        internal fun of(
            entry: StudBookEntry,
            sireId: BloodHorseId,
            damId: BloodHorseId,
            registrationNumber: PedigreeRegistrationNumber,
        ): BloodHorse =
            BloodHorse(
                id = BloodHorseId(generateId()),
                registrationNumber = registrationNumber,
                sex = entry.sex,
                coatColor = entry.coatColor,
                breedType = entry.breedType,
                dateOfBirth = entry.dateOfBirth,
                breeder = entry.breeder,
                microchipNumber = entry.microchipNumber,
                sireId = sireId,
                damId = damId,
                name = null,
            )
    }
}
