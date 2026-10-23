package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.ForeignSire
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 種付（種牡馬を繁殖牝馬に交配したという事実）を表す値オブジェクト。
 *
 * 繁殖成績の年次レコード（[BreedingResult]）における最初の節目。種付が国内で日本の繁殖登録を持つ種牡馬と行われたか
 * （[Domestic]）で、種牡馬の参照のしかたと種付の証明のしかたが異なるため sealed で分ける。どちらも種付日（[coveringDate]）を 持ち、繁殖年はその暦年で定まる。
 *
 * @property coveringDate 種付日
 */
@ValueObject
sealed interface Covering {
    val coveringDate: LocalDate

    /**
     * 国内種付。日本で繁殖登録された種牡馬との種付で、種付証明書で証明される。
     *
     * どの種牡馬と・いつ・どこで交配し・その事実をどの種付証明書で証明するかを束ねる。種牡馬は別個体（別集約）であり、参照は [BloodHorseId]
     * 経由で表す。種牡馬が雄であることなど集約をまたぐ前提条件の検証はドメインサービス recordCovering の責務とし、 検証を経た記録のみを許す。
     *
     * 種付場所（[coveringPlace]）は、種畜証明書（[StudCertificate]）の有効区域に対する種付の有効性検証に用いる。
     *
     * @property stallionId 種牡馬（雄の軽種馬）の軽種馬ID
     * @property coveringDate 種付日
     * @property coveringPlace 種付が行われた場所（有効性検証に用いる）
     * @property certificateNumber 種付の事実を証明する種付証明書の番号
     */
    @ValueObject
    data class Domestic(
        val stallionId: BloodHorseId,
        override val coveringDate: LocalDate,
        val coveringPlace: BreedingRegion?,
        val certificateNumber: CoveringCertificateNumber,
    ) : Covering

    /**
     * 海外種付。外国で、日本の繁殖登録を持たない外国種牡馬と行われた種付。
     *
     * この種付で受胎した繁殖牝馬が輸出国を出国した後に生まれた産駒は持込馬になる（登録規程 第6条第1号）。持込馬の血統登録は 種畜証明書を有する種雄馬の種付であることを要件としないため（同
     * 第15条柱書）、[Domestic] と異なり種畜証明書による 有効区域・有効期間の検証は掛けない。種付の事実は種付国の血統登録機関が発行した書類で証明する（同 第8条第1項第4号）。
     *
     * @property sire 外国種牡馬
     * @property coveringDate 種付日
     * @property coveringCountry 種付が行われた国
     * @property proofNumber 種付国の血統登録機関が発行した種付事実の証明の番号
     */
    @ValueObject
    data class Overseas(
        val sire: ForeignSire,
        override val coveringDate: LocalDate,
        val coveringCountry: OriginCountry,
        val proofNumber: ForeignCoveringProofNumber,
    ) : Covering
}
