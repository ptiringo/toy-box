package com.example.api.domain.studbook.model.breeding

import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/**
 * 繁殖供用を停止した事由。
 *
 * 登録規程実施基準・第15条第1項(3)は、血統書に「供用停止した繁殖登録馬にあっては供用停止の事由とその事由が発生した日」を 記載すると定めるが、事由のカテゴリ自体は列挙していない（開放的）。
 * 繁殖成績報告書（様式第14号）は繁殖馬本体の異動として「死亡（死産・生後直死を除く）／用途変更」を名指しするため、 この2つを区分として持ち、第15条の開放性を受ける逃がしとして [OTHER]
 * を置く。
 *
 * なお「輸出」は第15条で供用停止とは別号（輸出先国・輸出年月日）として扱われるため、供用停止事由には含めない。
 */
enum class RetirementReason {
    /** 死亡（死産・生後直死を除く）。 */
    DEATH,

    /** 用途変更（乗用馬等への転用など、繁殖以外の用途への変更）。 */
    USE_CHANGE,

    /** その他の事由（第15条は事由を限定しないため、上記以外を受ける）。 */
    OTHER,
}

/**
 * 繁殖供用の停止（供用停止）を表す値オブジェクト。
 *
 * 繁殖登録（[BreedingRegistration]）のライフサイクル終端であり、供用停止の[事由][reason]と
 * その事由が[発生した日][occurredOn]を対で持つ（登録規程実施基準・第15条第1項(3)）。
 *
 * @property reason 供用停止の事由
 * @property occurredOn 事由が発生した日
 */
@ValueObject data class BreedingRetirement(val reason: RetirementReason, val occurredOn: LocalDate)
