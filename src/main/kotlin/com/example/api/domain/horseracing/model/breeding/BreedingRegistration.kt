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
 * 血統登録・馬名登録を済ませた牝馬を繁殖の用に供するための登録で、`BloodHorse` の「繁殖牝馬
 * （Broodmare）」としてのロールを実体化する。登録後は毎年の繁殖成績報告（種付・分娩）や異動報告が この集約に紐づく想定。
 *
 * 繁殖牝馬は別集約であり、参照は [BloodHorseId] 経由で表す。前提条件（種雌馬であること等）の検証は 集約をまたぐため、ドメインサービス registerForBreeding
 * の責務とする。
 *
 * @property registrationNumber 繁殖登録番号
 * @property broodmareId 繁殖牝馬（血統登録済みの牝馬）の軽種馬ID
 * @property id 繁殖登録ID（自動生成）
 */
@AggregateRoot
class BreedingRegistration
private constructor(
    val registrationNumber: BreedingRegistrationNumber,
    val broodmareId: BloodHorseId,
) : Entity<BreedingRegistrationId>() {
    /** 繁殖登録ID */
    @field:Identity override val id = BreedingRegistrationId(generateId())

    companion object {
        /**
         * [BreedingRegistration] を生成する。
         *
         * 繁殖牝馬としての前提条件（種雌馬であること等）はドメインサービス registerForBreeding が
         * 検証済みである前提のため、この生成口は同モジュールのドメインサービスからのみ呼べるよう internal とする。
         */
        internal fun of(
            registrationNumber: BreedingRegistrationNumber,
            broodmareId: BloodHorseId,
        ): BreedingRegistration = BreedingRegistration(registrationNumber, broodmareId)
    }
}
