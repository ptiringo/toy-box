package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 種牡馬登録ID */
@ValueObject @JvmInline value class StallionRegistrationId(val value: UUID)

/**
 * 種牡馬登録を表す集約ルート。
 *
 * 繁殖登録（料金表上の「繁殖用 雄」）の雄側で、血統登録・馬名登録を済ませた雄馬を繁殖の用に供するための登録。 `BloodHorse`
 * の「種牡馬（Stallion）」としてのロールを実体化する。雌側の [BreedingRegistration]（繁殖牝馬= Broodmare
 * ロール）と対称な、同一コンテキスト（JAIRS）の繁殖登録である（経緯は ADR-0013）。
 *
 * 種牡馬は別集約であり、参照は [BloodHorseId] 経由で表す。前提条件（雄であること等）の検証は集約をまたぐため、 ドメインサービス registerStallion の責務とする。
 *
 * 繁殖登録番号は雄雌で同じ識別子体系（[BreedingRegistrationNumber]）を用いる。
 *
 * @property registrationNumber 繁殖登録番号
 * @property stallionId 種牡馬（血統登録済みの雄馬）の軽種馬ID
 * @property id 種牡馬登録ID（自動生成）
 */
@AggregateRoot
class StallionRegistration
private constructor(
    val registrationNumber: BreedingRegistrationNumber,
    val stallionId: BloodHorseId,
) : Entity<StallionRegistrationId>() {
    /** 種牡馬登録ID */
    @field:Identity override val id = StallionRegistrationId(generateId())

    companion object {
        /**
         * [StallionRegistration] を生成する。
         *
         * 種牡馬としての前提条件（雄であること等）はドメインサービス registerStallion が検証済みである
         * 前提のため、この生成口は同モジュールのドメインサービスからのみ呼べるよう internal とする。
         */
        internal fun of(
            registrationNumber: BreedingRegistrationNumber,
            stallionId: BloodHorseId,
        ): StallionRegistration = StallionRegistration(registrationNumber, stallionId)
    }
}
