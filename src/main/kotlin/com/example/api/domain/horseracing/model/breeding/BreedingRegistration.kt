package com.example.api.domain.horseracing.model.breeding

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
 * 繁殖供用する個体は別集約であり、参照は [BloodHorseId] 経由で表す。前提条件（血統登録済み等）の検証は集約をまたぐため、 ドメインサービス registerForBreeding
 * の責務とする。
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
         * [BreedingRegistration] を生成する。
         *
         * 繁殖登録の前提条件（血統登録済み等）はドメインサービス registerForBreeding が検証済みである前提のため、
         * この生成口は同モジュールのドメインサービスからのみ呼べるよう internal とする。
         */
        internal fun of(
            registrationNumber: BreedingRegistrationNumber,
            registeredHorseId: BloodHorseId,
            role: BreedingRole,
        ): BreedingRegistration = BreedingRegistration(registrationNumber, registeredHorseId, role)
    }
}
