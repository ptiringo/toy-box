package com.example.api.domain.horseracing.model.breeding

import org.jmolecules.ddd.annotation.Repository

/**
 * 繁殖成績（[BreedingResult]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。 種付記録で起こした年次レコードの保存や、分娩結果報告のために対象成績を取得するのに用いる。
 */
@Repository
interface BreedingResultRepository {
    /** 繁殖成績IDで検索する。存在しなければ null。 */
    fun findById(id: BreedingResultId): BreedingResult?

    /** 繁殖成績を永続化する。 */
    fun save(breedingResult: BreedingResult): BreedingResult
}
