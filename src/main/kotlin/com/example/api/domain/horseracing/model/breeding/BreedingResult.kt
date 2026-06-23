package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.shared.Entity
import com.example.api.domain.shared.generateId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
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
 * 種付記録（ドメインサービス recordCovering）の前提条件違反。
 *
 * 種付記録の前提は3系統ある。(1) 配合の登録ロール（繁殖牝馬 × 種牡馬）＝単一インスタンスの構築時不変条件で、 ファクトリ [BreedingResult.create]
 * が検証する（[NotBroodmare] / [NotStallion]）。(2) 種付の有効性（種畜証明書の有効区域・有効期間内であること）＝協力与件
 * （種畜証明書）を引数で受け取れば構築時に検証できる前提条件で、これもファクトリ [BreedingResult.create] が自己検証する （[InvalidCovering]。検証本体は
 * [StudCertificate.authorizes]）。(3) 「繁殖牝馬 × 繁殖年」で 一意 （繁殖成績報告書
 * 様式第14号が報告する年次成績は種付年ごとに1行）という集合制約で、既存成績群をまたぐため ドメインサービス recordCovering が検証する
 * （[AlreadyRecordedForYear]）。共通の語彙として 1 つの sealed にまとめ、Controller 境界で一括して problem へ写す。
 */
sealed interface RecordCoveringError {
    /** 種付対象の繁殖登録のロールが繁殖牝馬（BROODMARE）でない。ファクトリ [BreedingResult.create] が検証する。 */
    data object NotBroodmare : RecordCoveringError

    /** 配合相手の繁殖登録のロールが種牡馬（STALLION）でない。ファクトリ [BreedingResult.create] が検証する。 */
    data object NotStallion : RecordCoveringError

    /**
     * 種付が種畜証明書の有効区域・有効期間の内側にない（産駒の血統登録要件を満たさない）。
     *
     * 種畜証明書を引数で受け取れる場合にファクトリ [BreedingResult.create] が自己検証する構築時前提条件。詳細な失敗内容 （有効期間外／有効区域外）は [cause]
     * が表す。
     *
     * @property cause 有効性検証の失敗内容
     */
    data class InvalidCovering(val cause: CoveringValidityError) : RecordCoveringError

    /**
     * 同一繁殖牝馬・同一繁殖年に既に年次の繁殖成績が存在する（種付した年・種付せずの年を問わない）。
     *
     * 繁殖成績は「繁殖牝馬 × 繁殖年」で一意であり、同一年への重複記録は不変条件違反。これは単一インスタンスの構築では 完結しない集合制約のため、ドメインサービス
     * recordCovering が検証する。既存レコードの引き当て（coordination）は アプリケーション層が行い、その結果をサービスへ渡す。
     *
     * @property year 重複した繁殖年
     * @property existingBreedingResultId 既に存在する同年の繁殖成績のID
     */
    data class AlreadyRecordedForYear(
        val year: Year,
        val existingBreedingResultId: BreedingResultId,
    ) : RecordCoveringError
}

/**
 * 種付せず（年次成績）記録（ドメインサービス recordUncovered）の前提条件違反。
 *
 * 種付せずも繁殖牝馬の年次成績であり、前提は2系統ある。(1) 対象の繁殖登録のロールが繁殖牝馬であること ＝単一インスタンスの構築時不変条件で、ファクトリ
 * [BreedingResult.createUncovered] が検証する（[NotBroodmare]）。 (2)「繁殖牝馬 × 繁殖年」で 一意
 * （同一年への重複記録の禁止。種付した年・種付せずの年を問わない）という集合制約で、既存成績群をまたぐため ドメインサービス recordUncovered が検証する
 * （[AlreadyRecordedForYear]）。種付記録の [RecordCoveringError] と対称に、 共通の語彙として 1 つの sealed にまとめ、Controller
 * 境界で一括して problem へ写す。
 */
sealed interface RecordUncoveredError {
    /** 種付せずの記録対象の繁殖登録のロールが繁殖牝馬（BROODMARE）でない。ファクトリ [BreedingResult.createUncovered] が検証する。 */
    data object NotBroodmare : RecordUncoveredError

    /**
     * 同一繁殖牝馬・同一繁殖年に既に年次の繁殖成績が存在する（種付した年・種付せずの年を問わない）。
     *
     * 繁殖成績は「繁殖牝馬 × 繁殖年」で一意であり、同一年への重複記録は不変条件違反。これは単一インスタンスの構築では 完結しない集合制約のため、ドメインサービス
     * recordUncovered が検証する。既存レコードの引き当て（coordination）は アプリケーション層が行い、その結果をサービスへ渡す。
     *
     * @property year 重複した繁殖年
     * @property existingBreedingResultId 既に存在する同年の繁殖成績のID
     */
    data class AlreadyRecordedForYear(
        val year: Year,
        val existingBreedingResultId: BreedingResultId,
    ) : RecordUncoveredError
}

/**
 * 繁殖成績を表す集約ルート。繁殖年ごとの年次レコード。
 *
 * 繁殖登録済みの牝馬（[BreedingRegistration]）について、その年の繁殖成績を記録する。「繁殖成績報告書」 （様式第14号）が報告する 1
 * 行ぶんの年次成績に対応する。繁殖登録は別集約であり、参照は [BreedingRegistrationId] 経由で表す。
 *
 * 年次成績は2種類ある。**種付した年**は種付（[Covering]）を持ち、後日その年の分娩結果（[FoalingOutcome]
 * の種付後7区分のいずれか）が確定する。**種付しなかった年**（種付せず）は [Covering] を持たず、[outcome] は 生成時に
 * [FoalingOutcome.NotCovered] で確定する終端レコードとなる。繁殖年（[breedingYear]）は両者を通じて
 * 成績の集計・報告の単位であり、種付した年は種付日の年と一致する。
 *
 * 状態はイミュータブルに扱う。種付の記録（[create]）で生成された成績は [outcome] が null（分娩結果は未報告）で 始まり、後日の分娩結果報告で一度だけ [outcome]
 * が確定する。報告は [recordFoaling] が分娩結果を持つ新しい [BreedingResult]
 * を返すことで表し、同一性（[id]）は引き継ぐ。元のインスタンスは変更しない。種付せずの記録 （[createUncovered]）は最初から終端のため [recordFoaling]
 * の対象にならない。
 *
 * 登録ロール（繁殖牝馬・種牡馬）の検証など集約をまたぐ前提条件は、繁殖登録を引数で受け取る生成ファクトリ （[create] /
 * [createUncovered]）がその場で自己検証する。コンストラクタは private とし、生成はこれらの ファクトリのみに限る。「種付した年は covering を持ち outcome
 * は NotCovered でない」「種付せずの年は covering を 持たず outcome は NotCovered」という covering
 * と区分の整合は本クラスの不変条件として強制する。
 *
 * @property id 繁殖成績ID（生成時に自動採番し、以後の写像でも引き継ぐ）
 * @property breedingRegistrationId この成績が紐づく繁殖登録（繁殖牝馬のロール）のID
 * @property breedingYear 繁殖年（この成績を集計・報告する年単位）。種付した年は種付日の年と一致する
 * @property covering その年の種付。種付せずの年は null
 * @property outcome その年の分娩結果。種付した年は未報告なら null・報告は [recordFoaling] で行う。種付せずの年は
 *   [FoalingOutcome.NotCovered]
 */
@AggregateRoot
class BreedingResult
private constructor(
    @field:Identity override val id: BreedingResultId,
    val breedingRegistrationId: BreedingRegistrationId,
    val breedingYear: Year,
    val covering: Covering?,
    val outcome: FoalingOutcome?,
) : Entity<BreedingResultId>() {
    init {
        if (covering == null) {
            require(outcome == FoalingOutcome.NotCovered) {
                "種付せずの繁殖成績（covering が無い）の区分は NotCovered でなければならない。"
            }
        } else {
            require(breedingYear.value == covering.coveringDate.year) {
                "種付した年の繁殖年は種付日の年と一致しなければならない。"
            }
            require(outcome != FoalingOutcome.NotCovered) { "種付した繁殖成績の区分は NotCovered であってはならない。" }
        }
    }

    /**
     * 分娩結果を報告した新しい [BreedingResult] を返す。
     *
     * 分娩結果の報告は繁殖年ごとに一度だけ行えるドメインイベント。既に報告済みの成績（種付せずを含む）への 再報告は不変条件違反として [FoalingAlreadyRecorded]
     * を返し、写像を行わない（元の [BreedingResult] も不変）。 成功時は [outcome] のみ差し替え、[id] を含む他の属性は引き継ぐ。
     *
     * 種付せず（[FoalingOutcome.NotCovered]）は種付を伴わない年次成績の区分であり分娩結果ではないため、報告区分 として渡してはならない（生成は
     * [createUncovered] に限る）。
     *
     * @param outcome 報告する分娩結果
     * @return 報告済みの新しい [BreedingResult]、既に報告済みなら [FoalingAlreadyRecorded]
     */
    fun recordFoaling(outcome: FoalingOutcome): Result<BreedingResult, FoalingAlreadyRecorded> {
        require(outcome != FoalingOutcome.NotCovered) { "種付せず（NotCovered）は分娩結果として報告できない。" }
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
            breedingYear = breedingYear,
            covering = covering,
            outcome = outcome,
        )

    companion object {
        /**
         * 繁殖牝馬に対するその年の種付を記録し、[BreedingResult] の年次レコードを生成する。
         *
         * 種付は「繁殖登録済みの繁殖牝馬」と「繁殖登録済みの種牡馬」の配合であり、両者が繁殖登録（[BreedingRegistration]）
         * を持つことを前提とする（種牡馬も繁殖登録の対象＝繁殖登録証明書の `性` が雄）。本ファクトリは集約をまたぐ前提条件
         * として、牝側の登録ロールが繁殖牝馬・雄側の登録ロールが種牡馬であることを自己検証してから生成する。検証を満たさなければ 生成せず [RecordCoveringError]
         * を返す。生成物は種牡馬を `BloodHorseId` 経由で参照する。生成直後は分娩結果が 未報告（[outcome] は null）。
         *
         * 本ファクトリが守るのは「単一の繁殖成績インスタンスの構築時不変条件」（登録ロール）と、協力与件を引数で受け取れる 構築時前提条件（種畜証明書による種付の有効性）に限る。後者は
         * [studCertificate] が渡されたときだけ検証する段階導入で、 渡されなければ従来どおりロール検証のみで生成する（API
         * 入口が種畜証明書・種付場所を供給するようになり次第、必須化する）。 「繁殖牝馬 ×
         * 繁殖年」で一意という集合制約（同一年の重複記録の禁止）は単一インスタンスの構築では完結しないため、 既存成績群をまたぐ ドメインサービス recordCovering
         * が担い、本ファクトリはその検証を経た上で呼び出される。
         *
         * @param broodmareRegistration 種付対象の繁殖牝馬の繁殖登録（ロールが繁殖牝馬であること）
         * @param stallionRegistration 配合相手の種牡馬の繁殖登録（ロールが種牡馬であること）
         * @param coveringDate 種付日
         * @param certificateNumber 種付の事実を証明する種付証明書の番号
         * @param studCertificate 種牡馬の種畜証明書。渡された場合のみ種付の有効性（有効区域・有効期間）を検証する
         * @param coveringPlace 種付場所。[studCertificate] を渡す場合は必須（有効区域の整合検証に用いる）
         * @return 種付を記録した [BreedingResult]、または前提条件違反を表す [RecordCoveringError]
         */
        fun create(
            broodmareRegistration: BreedingRegistration,
            stallionRegistration: BreedingRegistration,
            coveringDate: LocalDate,
            certificateNumber: CoveringCertificateNumber,
            studCertificate: StudCertificate? = null,
            coveringPlace: BreedingRegion? = null,
        ): Result<BreedingResult, RecordCoveringError> =
            when {
                broodmareRegistration.role != BreedingRole.BROODMARE ->
                    Err(RecordCoveringError.NotBroodmare)
                stallionRegistration.role != BreedingRole.STALLION ->
                    Err(RecordCoveringError.NotStallion)
                else ->
                    validateCovering(studCertificate, coveringDate, coveringPlace)
                        .map {
                            BreedingResult(
                                id = BreedingResultId(generateId()),
                                breedingRegistrationId = broodmareRegistration.id,
                                breedingYear = Year.of(coveringDate.year),
                                covering =
                                    Covering(
                                        stallionRegistration.registeredHorseId,
                                        coveringDate,
                                        coveringPlace,
                                        certificateNumber,
                                    ),
                                outcome = null,
                            )
                        }
                        .mapError { RecordCoveringError.InvalidCovering(it) }
            }

        /**
         * 種畜証明書が渡された場合に種付の有効性（有効区域・有効期間）を検証する。渡されなければ検証なし（段階導入）。
         *
         * [studCertificate] を渡す場合、有効区域の整合検証のため [coveringPlace] は必須（プログラミングエラーとして表明）。
         */
        private fun validateCovering(
            studCertificate: StudCertificate?,
            coveringDate: LocalDate,
            coveringPlace: BreedingRegion?,
        ): Result<Unit, CoveringValidityError> =
            if (studCertificate == null) {
                Ok(Unit)
            } else {
                require(coveringPlace != null) { "種畜証明書を渡す場合は種付場所も必須。" }
                studCertificate.authorizes(coveringDate, coveringPlace)
            }

        /**
         * 繁殖牝馬のその年の「種付せず」（種付しなかった年次成績）を記録し、終端の [BreedingResult] を生成する。
         *
         * 種付せずも繁殖牝馬の年次成績であり、対象の繁殖登録のロールが繁殖牝馬であることを前提とする。本ファクトリは その前提を自己検証してから生成する。満たさなければ生成せず
         * [RecordUncoveredError.NotBroodmare] を返す。生成物は種付 （[covering]）を持たず、[outcome] は
         * [FoalingOutcome.NotCovered] で確定する終端レコードであり、分娩結果報告 （[recordFoaling]）の対象にはならない。
         *
         * 本ファクトリが守るのは「単一の繁殖成績インスタンスの構築時不変条件」（登録ロール）に限る。「繁殖牝馬 × 繁殖年」で
         * 一意という集合制約（同一年の重複記録の禁止）は単一インスタンスの構築では完結しないため、既存成績群をまたぐ ドメインサービス recordUncovered
         * が担い、本ファクトリはその検証を経た上で呼び出される（種付記録の [create] と対称）。
         *
         * @param broodmareRegistration 種付せずの記録対象の繁殖牝馬の繁殖登録（ロールが繁殖牝馬であること）
         * @param breedingYear 種付しなかった繁殖年
         * @return 種付せずを記録した [BreedingResult]、または登録ロールの前提条件違反を表す [RecordUncoveredError.NotBroodmare]
         */
        fun createUncovered(
            broodmareRegistration: BreedingRegistration,
            breedingYear: Year,
        ): Result<BreedingResult, RecordUncoveredError> =
            if (broodmareRegistration.role != BreedingRole.BROODMARE) {
                Err(RecordUncoveredError.NotBroodmare)
            } else {
                Ok(
                    BreedingResult(
                        id = BreedingResultId(generateId()),
                        breedingRegistrationId = broodmareRegistration.id,
                        breedingYear = breedingYear,
                        covering = null,
                        outcome = FoalingOutcome.NotCovered,
                    )
                )
            }
    }
}
