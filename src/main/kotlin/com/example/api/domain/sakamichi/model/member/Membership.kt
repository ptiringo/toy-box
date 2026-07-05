package com.example.api.domain.sakamichi.model.member

import java.time.LocalDate
import org.jmolecules.ddd.annotation.ValueObject

/**
 * メンバーの在籍状態。
 *
 * 在籍中（[Active]）と卒業済み（[Graduated]）は相互排他であり、sealed interface で型として強制する （nullable
 * な卒業日の平置きでは「無効な組み合わせ」を型で防げない。`Origin` と同じ流儀・ADR-0020）。
 */
@ValueObject
sealed interface Membership {
    /** 在籍中。 */
    @ValueObject data object Active : Membership

    /**
     * 卒業済み。
     *
     * @property graduatedOn 卒業日
     */
    @ValueObject data class Graduated(val graduatedOn: LocalDate) : Membership
}
