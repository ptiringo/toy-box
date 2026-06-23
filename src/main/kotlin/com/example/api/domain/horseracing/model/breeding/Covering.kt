package com.example.api.domain.horseracing.model.breeding

import com.example.api.domain.horseracing.model.horse.bloodhorse.BloodHorseId
import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 種付（種牡馬を繁殖牝馬に交配したという事実）を表す値オブジェクト。
 *
 * 繁殖成績の年次レコード（[BreedingResult]）における最初の節目で、どの種牡馬と・いつ・どこで交配し・その事実を
 * どの種付証明書で証明するかを束ねる。種牡馬は別個体（別集約）であり、参照は [BloodHorseId] 経由で表す。 種牡馬が雄であることなど 集約をまたぐ前提条件の検証はドメインサービス
 * recordCovering の責務とし、 検証を経た記録のみを許す。
 *
 * 種付場所（[coveringPlace]）は、種畜証明書（[StudCertificate]）の有効区域に対する種付の有効性検証に用いる。現状は API 入口が場所・種畜証明書を供給しないため
 * nullable とし、供給された記録でのみ場所を保持する（段階導入。供給経路が 整い次第、必須化する）。
 *
 * @property stallionId 種牡馬（雄の軽種馬）の軽種馬ID
 * @property coveringDate 種付日
 * @property coveringPlace 種付が行われた場所（有効性検証に用いる）。未供給の記録では null
 * @property certificateNumber 種付の事実を証明する種付証明書の番号
 */
@ValueObject
data class Covering(
    val stallionId: BloodHorseId,
    val coveringDate: LocalDate,
    val coveringPlace: BreedingRegion?,
    val certificateNumber: CoveringCertificateNumber,
)
