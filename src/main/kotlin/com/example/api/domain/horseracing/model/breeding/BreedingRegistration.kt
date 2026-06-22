package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 繁殖登録ID */
@ValueObject @JvmInline value class BreedingRegistrationId(val value: UUID)

/**
 * 繁殖登録（[BreedingRegistration.create]）の前提条件違反。
 *
 * 制度上の前提条件は複数ある（①血統登録済み ②馬名登録済み ③競走馬登録があれば抹消済み ④種雌馬であること）。このうち①血統登録済みは [BloodHorse]
 * の参照自体で担保され、②馬名登録・ ③競走馬登録抹消は対応する集約が未モデル化のため現時点では検証しない。集約が揃い次第バリアントを 追加できるよう sealed interface
 * としておく。
 */
sealed interface BreedingRegistrationError {
    /** 繁殖登録の対象は種雌馬（繁殖牝馬）に限られるが、対象馬が牝馬でない。 */
    data object NotFemale : BreedingRegistrationError
}

/**
 * 繁殖登録を表す集約ルート。
 *
 * 血統登録・馬名登録を済ませた牝馬を繁殖の用に供するための登録で、`BloodHorse` の「繁殖牝馬
 * （Broodmare）」としてのロールを実体化する。登録後は毎年の繁殖成績報告（種付・分娩）や異動報告が この集約に紐づく想定。
 *
 * 繁殖牝馬は別集約であり、参照は [BloodHorseId] 経由で表す。前提条件（種雌馬であること等）は集約をまたぐが、繁殖牝馬を 引数で受け取る生成ファクトリ [create]
 * がその場で自己検証する。
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
         * 血統登録済みの牝馬を繁殖の用に供するため繁殖登録し、[BreedingRegistration] を生成する。
         *
         * 繁殖登録は牝馬のみが対象であり（種雄馬は種付証明書で扱う）、対象馬が種雌馬であることを自己検証してから生成する。 検証を満たさなければ生成せず
         * [BreedingRegistrationError] を返す。
         *
         * @param registrationNumber 交付される繁殖登録番号
         * @param broodmare 繁殖登録する繁殖牝馬（血統登録済みの [BloodHorse]）
         * @return 生成された [BreedingRegistration]、または前提条件違反を表す [BreedingRegistrationError]
         */
        fun create(
            registrationNumber: BreedingRegistrationNumber,
            broodmare: BloodHorse,
        ): Result<BreedingRegistration, BreedingRegistrationError> =
            if (broodmare.sex != Sex.FEMALE) {
                Err(BreedingRegistrationError.NotFemale)
            } else {
                Ok(BreedingRegistration(registrationNumber, broodmare.id))
            }
    }
}
