package com.example.api.domain.horseracing.model.racing

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 競走馬登録ID */
@ValueObject @JvmInline value class RacingRegistrationId(val value: UUID)

/**
 * 競走馬登録を表す集約ルート。
 *
 * 血統登録・馬名登録を済ませた馬を競走の用に供するための登録で、`BloodHorse` の「競走馬（Racehorse）」 としてのロールを実体化する（ADR-0012
 * のロール統一方針）。種牡馬・繁殖牝馬と同じく、別個体ではなく 同一の `BloodHorse` が担うロールであり、対象馬は [BloodHorseId] 経由で参照する。
 *
 * 競走馬としての登録（出走資格）は血統登録・繁殖登録を司る JAIRS の管轄外であり、権威ソースは
 * JRA（中央）・地方競馬（NAR）にある（ADR-0012）。本集約は当面、当コンテキストで完結する最小限の前提条件
 * （血統登録済み・馬名登録済み）の下で競走馬登録を表すにとどめ、出走資格・競走成績など外部権威に属する 情報は射程外とする。
 *
 * 前提条件（馬名登録済みであること等）の検証は集約をまたぐため、ドメインサービス registerAsRacehorse の 責務とする。検証を経た生成のみを許すため生成口 [of]
 * は同モジュールのドメインサービスからのみ呼べるよう internal とする（ADR-0010）。
 *
 * @property registrationNumber 競走馬登録番号
 * @property racehorseId 競走馬（血統登録・馬名登録済みの馬）の軽種馬ID
 * @property id 競走馬登録ID（自動生成）
 */
@AggregateRoot
class RacingRegistration
private constructor(
    val registrationNumber: RacingRegistrationNumber,
    val racehorseId: BloodHorseId,
) : Entity<RacingRegistrationId>() {
    /** 競走馬登録ID */
    @field:Identity override val id = RacingRegistrationId(generateId())

    companion object {
        /**
         * [RacingRegistration] を生成する。
         *
         * 競走馬としての前提条件（馬名登録済みであること等）はドメインサービス registerAsRacehorse が
         * 検証済みである前提のため、この生成口は同モジュールのドメインサービスからのみ呼べるよう internal とする。
         */
        internal fun of(
            registrationNumber: RacingRegistrationNumber,
            racehorseId: BloodHorseId,
        ): RacingRegistration = RacingRegistration(registrationNumber, racehorseId)
    }
}
