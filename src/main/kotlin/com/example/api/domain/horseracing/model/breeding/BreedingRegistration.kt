package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 繁殖登録ID */
@ValueObject @JvmInline value class BreedingRegistrationId(val value: UUID)

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
 * @property registrationNumber 繁殖登録番号
 * @property registeredHorseId 繁殖登録した個体（血統登録済み）の軽種馬ID
 * @property role 繁殖登録によって付与されたロール（性から定まる）
 * @property id 繁殖登録ID（自動生成）
 */
@AggregateRoot
class BreedingRegistration
private constructor(
    val registrationNumber: BreedingRegistrationNumber,
    val registeredHorseId: BloodHorseId,
    val role: BreedingRole,
) : Entity<BreedingRegistrationId>() {
    /** 繁殖登録ID */
    @field:Identity override val id = BreedingRegistrationId(generateId())

    companion object {
        /**
         * 血統登録済みの馬を繁殖の用に供するため繁殖登録し、[BreedingRegistration] を生成する。
         *
         * 繁殖登録は雄雌どちらも対象で、付与される [BreedingRole]（雄=種牡馬／雌=繁殖牝馬）は対象個体の性から定まる。 制度上の前提条件（②馬名登録済み
         * ③競走馬登録があれば抹消済み 等）は対応する集約が未モデル化のため現時点では 検証しない。検証すべき前提条件が増えたら戻り値を `Result` に変える。
         *
         * @param registrationNumber 交付される繁殖登録番号
         * @param horse 繁殖登録する馬（血統登録済みの [BloodHorse]）
         * @return 生成された [BreedingRegistration]
         */
        fun create(
            registrationNumber: BreedingRegistrationNumber,
            horse: BloodHorse,
        ): BreedingRegistration =
            BreedingRegistration(registrationNumber, horse.id, BreedingRole.from(horse.sex))
    }
}
