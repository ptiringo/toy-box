package com.example.api.domain.tennis.model.player

import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/**
 * プロ転向日。
 *
 * 生年月日より後であることは 2 つの日付の関係であり、単独では検証できないため [Player] の生成時に検証する。 未来日でないことの検証は [DateOfBirth]
 * と同じ理由で行わない。
 *
 * @property value プロへ転向した暦日
 */
@ValueObject @JvmInline value class TurnedProDate(val value: LocalDate)
