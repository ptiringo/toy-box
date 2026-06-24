package com.example.api.domain.studbook.model.horse.bloodhorse

import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.StateTransition
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
 * 血統登録（[BloodHorse.create]）の前提条件違反。
 *
 * 失敗のしかたが複数あるため sealed interface とし、`when` の網羅性で漏れを防ぐ。
 */
sealed interface RegisterInStudBookError {
    /** 父として指定された馬が雄でない。 */
    data object SireNotMale : RegisterInStudBookError

    /** 母として指定された馬が雌でない。 */
    data object DamNotFemale : RegisterInStudBookError

    /** 申告された父母との DNA 型による親子判定に矛盾がある、または未検査である。 */
    data object ParentageNotConfirmed : RegisterInStudBookError

    /** 仔の品種が父母の品種と整合しない。 */
    data object BreedMismatch : RegisterInStudBookError
}

/**
 * 軽種馬を表す集約ルート。
 *
 * 血統登録（血統及び個体識別を明らかにする登録）の成立によって誕生する個体であり、ライフサイクル全体（繁殖登録・競走馬登録など）を通じて 同一の [BloodHorse]
 * が各ロール（繁殖牝馬・種牡馬・競走馬）を担う。
 *
 * 父・母は別個体（別集約）であり、参照は [BloodHorseId] 経由で表す。父=雄・母=雌・親子の DNA 整合・親仔の品種整合といった前提条件は集約をまたぐが、
 * 父・母を引数で受け取る生成ファクトリ [create] がその場で自己検証する（Jockey.create と同じく、検証を満たさなければ生成しない）。 コンストラクタは private
 * とし、生成は [create] / [createImported] のみに限る。
 *
 * 出自（内国産か輸入か）は [origin] に集約する。内国産馬は父・母の軽種馬ID（[Origin.Domestic]）を、 父母が当システムに存在しない個体
 * （輸入馬・基礎輸入馬）は原産国・揚陸日（[Origin.Imported]）を持つ。両者は相互排他であり、sealed [Origin] で型として強制する（ADR-0020）。
 * 内国産馬の生成は [create]、輸入馬の生成は [createImported] が担い、経路を分ける。
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
 * @property origin 出自（内国産＝父母ID／輸入＝原産国・揚陸日）。相互排他を [Origin] で型強制する
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
    val origin: Origin,
    val name: HorseName?,
) : Entity<BloodHorseId>() {
    /**
     * 馬名を登録し、命名済みの新しい [BloodHorse] と発生した [HorseNamed] イベントを同梱して返す。
     *
     * 馬名登録は血統登録済みの個体に一度だけ行える状態遷移。成功時は [name] のみ差し替えた新インスタンスを作り （[id] を含む他の属性は引き継ぐ）、その遷移で「起きたこと」を表す
     * [HorseNamed] を併せて [StateTransition] で返す。イベントの発行は application 層に委ね、集約は純粋に保つ（収集・発行方式は
     * ADR-0029）。既に命名済みの個体への再命名は不変条件違反として [HorseAlreadyNamed] を返し、写像も イベント生成も行わない（元の [BloodHorse]
     * も不変）。
     *
     * @param horseName 付与する馬名
     * @return 命名済みの新しい [BloodHorse] と [HorseNamed] を同梱した [StateTransition]、既に命名済みなら
     *   [HorseAlreadyNamed]
     */
    fun assignName(
        horseName: HorseName
    ): Result<StateTransition<BloodHorse, HorseNamed>, HorseAlreadyNamed> {
        val current = name
        return if (current != null) {
            Err(HorseAlreadyNamed(current))
        } else {
            val named = copy(name = horseName)
            Ok(StateTransition(named, HorseNamed(named.id, horseName)))
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
            origin = origin,
            name = name,
        )

    companion object {
        /**
         * 血統及び個体識別を明らかにする血統登録を行い、[BloodHorse] を生成する。
         *
         * 父・母は既に血統登録済みの [BloodHorse] であり、本ファクトリは集約をまたぐ前提条件を自己検証してから生成する。 検証する前提条件:
         * - 父が雄であること
         * - 母が雌であること
         * - 申告された父母との DNA 型による親子判定に矛盾がないこと
         * - 仔の品種が父母の品種と整合すること
         *
         * 検証を満たさなければ生成せず [RegisterInStudBookError] を返す。生成された [BloodHorse] は父母を ID
         * （[BloodHorseId]）経由で参照する。生成直後は未命名（[name] は null）。
         *
         * @param sire 父（雄）の軽種馬
         * @param dam 母（雌）の軽種馬
         * @param entry 仔馬自身の個体識別情報と DNA 親子判定結果
         * @param registrationNumber 交付される血統登録番号
         * @return 生成された [BloodHorse]、または前提条件違反を表す [RegisterInStudBookError]
         */
        fun create(
            sire: BloodHorse,
            dam: BloodHorse,
            entry: StudBookEntry,
            registrationNumber: PedigreeRegistrationNumber,
        ): Result<BloodHorse, RegisterInStudBookError> =
            when {
                sire.sex != Sex.MALE -> Err(RegisterInStudBookError.SireNotMale)
                dam.sex != Sex.FEMALE -> Err(RegisterInStudBookError.DamNotFemale)
                entry.dnaParentage != DnaParentageResult.CONSISTENT ->
                    Err(RegisterInStudBookError.ParentageNotConfirmed)
                !entry.breedType.isConsistentWith(sire.breedType, dam.breedType) ->
                    Err(RegisterInStudBookError.BreedMismatch)
                else ->
                    Ok(
                        BloodHorse(
                            id = BloodHorseId(generateId()),
                            registrationNumber = registrationNumber,
                            sex = entry.sex,
                            coatColor = entry.coatColor,
                            breedType = entry.breedType,
                            dateOfBirth = entry.dateOfBirth,
                            breeder = entry.breeder,
                            microchipNumber = entry.microchipNumber,
                            origin = Origin.Domestic(sireId = sire.id, damId = dam.id),
                            name = null,
                        )
                    )
            }

        /**
         * 父母不明の輸入馬・基礎輸入馬として [BloodHorse] を生成する。
         *
         * 父母が当システムに存在しないため父母 ID は持たず（出自は [Origin.Imported]）、代わりに原産国・揚陸日を持つ。 内国産馬の前提条件（父=雄・母=雌・DNA
         * 親子整合・親仔の品種整合）は適用されないため、本ファクトリは検証を持たず生成する。
         * 輸入馬固有の審査（承認海外機関の血統書による品種確定、親子判定の血液型・海外機関フォールバック）は別途のモデリングに委ねる。 生成直後は未命名（[name] は null）。
         */
        fun createImported(
            entry: ImportedHorseEntry,
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
                origin =
                    Origin.Imported(
                        originCountry = entry.originCountry,
                        landingDate = entry.landingDate,
                    ),
                name = null,
            )
    }
}
