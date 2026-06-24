package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.LocalDate
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 繁殖登録ID */
@ValueObject @JvmInline value class BreedingRegistrationId(val value: UUID)

/**
 * 既に供用停止済みの繁殖登録に対して、重ねて供用停止しようとした。
 *
 * 供用停止は繁殖登録ライフサイクルの終端であり一度きり。二重の供用停止は不変条件違反。
 *
 * @property current 既に記録されている供用停止
 */
data class AlreadyRetired(val current: BreedingRetirement)

/**
 * 繁殖登録を表す集約ルート。
 *
 * 繁殖登録（JAIRS）は「繁殖に供する馬の繁殖成績を明らかにする登録」で、血統登録・馬名登録を済ませた個体に対して
 * 行う追加登録（繁殖登録のながれの準備物に「その馬の血統登録証明書」がある）。**雄雌共通の単一の登録**で、繁殖登録証明書の `性` によって担う [BreedingRole]（雄=種牡馬
 * STALLION／雌=繁殖牝馬 BROODMARE）が決まる。これにより `BloodHorse` の繁殖ロールを実体化する。
 *
 * 繁殖供用する個体は別集約であり、参照は [BloodHorseId] 経由で表す。生成は登録対象の [BloodHorse] を引数で受け取る 生成ファクトリ [create]
 * に限り、ロールはその個体の性から定める。
 *
 * 状態はイミュータブルに扱う。繁殖登録は当初「供用中」（[retirement] は null）で、後日の「供用停止」で一度だけ終端する。 供用停止は [retire] が供用停止を記録した新しい
 * [BreedingRegistration] を返すことで表し、同一性（[id]）は引き継ぐ。元のインスタンスは変更しない （登録規程実施基準・第15条第1項(3)
 * は供用停止した繁殖登録馬に事由と発生日の記載を求める）。
 *
 * @property registrationNumber 繁殖登録番号
 * @property registeredHorseId 繁殖登録した個体（血統登録済み）の軽種馬ID
 * @property role 繁殖登録によって付与されたロール（性から定まる）
 * @property retirement 供用停止。供用中なら null、供用停止済みなら事由と発生日を持つ
 * @property id 繁殖登録ID（生成時に自動採番し、以後の写像でも引き継ぐ）
 */
@AggregateRoot
class BreedingRegistration
private constructor(
    @field:Identity override val id: BreedingRegistrationId,
    val registrationNumber: BreedingRegistrationNumber,
    val registeredHorseId: BloodHorseId,
    val role: BreedingRole,
    val retirement: BreedingRetirement?,
) : Entity<BreedingRegistrationId>() {
    /** 繁殖供用を停止済みか（供用停止が記録されているか）を返す。 */
    val isRetired: Boolean
        get() = retirement != null

    /**
     * 繁殖供用を停止した新しい [BreedingRegistration] を返す。
     *
     * 供用停止は繁殖登録ライフサイクルの終端であり一度だけ行える（登録規程実施基準・第15条第1項(3)）。 既に供用停止済みの登録への再停止は不変条件違反として
     * [AlreadyRetired] を返し、写像を行わない（元の [BreedingRegistration] も不変）。成功時は [retirement] のみ差し替え、[id]
     * を含む他の属性は引き継ぐ。
     *
     * @param reason 供用停止の事由
     * @param occurredOn 事由が発生した日
     * @return 供用停止済みの新しい [BreedingRegistration]、既に供用停止済みなら [AlreadyRetired]
     */
    fun retire(
        reason: RetirementReason,
        occurredOn: LocalDate,
    ): Result<BreedingRegistration, AlreadyRetired> {
        val current = retirement
        return if (current != null) Err(AlreadyRetired(current))
        else Ok(copy(retirement = BreedingRetirement(reason, occurredOn)))
    }

    /** [id] と未指定の属性を引き継ぎ、指定された属性だけを差し替えた新しい [BreedingRegistration] を返す。 */
    private fun copy(retirement: BreedingRetirement? = this.retirement): BreedingRegistration =
        BreedingRegistration(id, registrationNumber, registeredHorseId, role, retirement)

    companion object {
        /**
         * 血統登録済みの馬を繁殖の用に供するため繁殖登録し、[BreedingRegistration] を生成する。
         *
         * 繁殖登録は雄雌どちらも対象で、付与される [BreedingRole]（雄=種牡馬／雌=繁殖牝馬）は対象個体の性から定まる。 制度上の前提条件（②馬名登録済み
         * ③競走馬登録があれば抹消済み 等）は対応する集約が未モデル化のため現時点では 検証しない。検証すべき前提条件が増えたら戻り値を `Result` に変える。
         *
         * 生成直後は供用中（[retirement] は null）。
         *
         * @param registrationNumber 交付される繁殖登録番号
         * @param horse 繁殖登録する馬（血統登録済みの [BloodHorse]）
         * @return 生成された [BreedingRegistration]
         */
        fun create(
            registrationNumber: BreedingRegistrationNumber,
            horse: BloodHorse,
        ): BreedingRegistration =
            BreedingRegistration(
                id = BreedingRegistrationId(generateId()),
                registrationNumber = registrationNumber,
                registeredHorseId = horse.id,
                role = BreedingRole.from(horse.sex),
                retirement = null,
            )
    }
}
