package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.horseracing.model.horse.bloodhorse.Sex
import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import java.time.LocalDate
import java.time.Year
import java.util.UUID
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Identity
import org.jmolecules.ddd.annotation.ValueObject

/** 繁殖成績ID */
@ValueObject @JvmInline value class BreedingResultId(val value: UUID)

/**
 * 既に分娩結果が報告済みの繁殖成績へ重ねて報告しようとした。
 *
 * 分娩結果の報告は種付年ごとに一度だけ行えるドメインイベントであり、二重報告は不変条件違反。
 *
 * @property current 既に報告されている分娩結果
 */
data class FoalingAlreadyRecorded(val current: FoalingOutcome)

/**
 * 種付記録（[BreedingResult.create]）の前提条件違反。
 *
 * 現時点では種牡馬が雄であることのみを検証するが、制度上は他の前提条件もありうる（例: 同一種付年の重複記録の禁止、 種牡馬の種牡馬登録の確認）。集約が揃い次第バリアントを追加できるよう
 * sealed interface としておく。
 */
sealed interface RecordCoveringError {
    /** 配合相手は種牡馬（雄）に限られるが、指定された馬が雄でない。 */
    data object StallionNotMale : RecordCoveringError
}

/**
 * 繁殖成績を表す集約ルート。種付年ごとの「種付〜分娩」の年次レコード。
 *
 * 繁殖登録済みの牝馬（[BreedingRegistration]）について、その年の種付（[Covering]）と分娩結果
 * （[FoalingOutcome]）を記録する。「繁殖成績報告書」（様式第14号）が報告する 1 行ぶんの年次成績に対応する。 繁殖登録は別集約であり、参照は
 * [BreedingRegistrationId] 経由で表す。種付年は種付日から導出する。
 *
 * 状態はイミュータブルに扱う。種付の記録で生成され（[outcome] は null＝分娩結果は未報告）、後日の分娩結果報告 で一度だけ [outcome] が確定する。報告は
 * [recordFoaling] が分娩結果を持つ新しい [BreedingResult] を返す ことで表し、同一性（[id]）は引き継ぐ。元のインスタンスは変更しない。
 *
 * 種牡馬が雄であることなど集約をまたぐ前提条件は集約をまたぐが、種牡馬を引数で受け取る生成ファクトリ [create] がその場で 自己検証する。コンストラクタは private とし、生成は
 * [create] のみに限る。
 *
 * @property id 繁殖成績ID（生成時に自動採番し、以後の写像でも引き継ぐ）
 * @property breedingRegistrationId この成績が紐づく繁殖登録（繁殖牝馬のロール）のID
 * @property covering その年の種付
 * @property outcome その年の分娩結果。未報告なら null。報告は [recordFoaling] でのみ行う
 */
@AggregateRoot
class BreedingResult
private constructor(
    @field:Identity override val id: BreedingResultId,
    val breedingRegistrationId: BreedingRegistrationId,
    val covering: Covering,
    val outcome: FoalingOutcome?,
) : Entity<BreedingResultId>() {
    /** 種付年。種付日から導出する（繁殖成績はこの年単位で集計・報告される）。 */
    val coveringYear: Year
        get() = Year.of(covering.coveringDate.year)

    /**
     * 分娩結果を報告した新しい [BreedingResult] を返す。
     *
     * 分娩結果の報告は種付年ごとに一度だけ行えるドメインイベント。既に報告済みの成績への再報告は 不変条件違反として [FoalingAlreadyRecorded]
     * を返し、写像を行わない（元の [BreedingResult] も不変）。 成功時は [outcome] のみ差し替え、[id] を含む他の属性は引き継ぐ。
     *
     * @param outcome 報告する分娩結果
     * @return 報告済みの新しい [BreedingResult]、既に報告済みなら [FoalingAlreadyRecorded]
     */
    fun recordFoaling(outcome: FoalingOutcome): Result<BreedingResult, FoalingAlreadyRecorded> {
        val current = this.outcome
        return if (current != null) {
            Err(FoalingAlreadyRecorded(current))
        } else {
            Ok(copy(outcome = outcome))
        }
    }

    /** [id] と未指定の属性を引き継ぎ、指定された属性だけを差し替えた新しい [BreedingResult] を返す。 */
    private fun copy(outcome: FoalingOutcome? = this.outcome): BreedingResult =
        BreedingResult(
            id = id,
            breedingRegistrationId = breedingRegistrationId,
            covering = covering,
            outcome = outcome,
        )

    companion object {
        /**
         * 繁殖牝馬に対するその年の種付を記録し、[BreedingResult] の年次レコードを生成する。
         *
         * 繁殖登録（[BreedingRegistration]）により対象が繁殖牝馬であることは担保される。本ファクトリは集約をまたぐ前提条件 として、配合相手の種牡馬が雄であることを
         * 自己検証してから生成する。検証を満たさなければ生成せず [RecordCoveringError] を返す。種牡馬は別個体（別集約）であり、生成物は種牡馬を
         * `BloodHorseId` 経由で参照する。生成直後は分娩結果が未報告（[outcome] は null）。
         *
         * @param breedingRegistration 種付対象の繁殖牝馬の繁殖登録
         * @param stallion 配合相手の種牡馬（雄の [BloodHorse]）
         * @param coveringDate 種付日
         * @param certificateNumber 種付の事実を証明する種付証明書の番号
         * @return 種付を記録した [BreedingResult]、または前提条件違反を表す [RecordCoveringError]
         */
        fun create(
            breedingRegistration: BreedingRegistration,
            stallion: BloodHorse,
            coveringDate: LocalDate,
            certificateNumber: CoveringCertificateNumber,
        ): Result<BreedingResult, RecordCoveringError> =
            if (stallion.sex != Sex.MALE) {
                Err(RecordCoveringError.StallionNotMale)
            } else {
                Ok(
                    BreedingResult(
                        id = BreedingResultId(generateId()),
                        breedingRegistrationId = breedingRegistration.id,
                        covering = Covering(stallion.id, coveringDate, certificateNumber),
                        outcome = null,
                    )
                )
            }
    }
}
