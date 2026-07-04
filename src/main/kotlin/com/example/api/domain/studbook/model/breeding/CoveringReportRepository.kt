package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.shared.UpdateConflict
import com.github.michaelbull.result.Result
import java.time.Year
import org.jmolecules.ddd.annotation.Repository

/**
 * 種付成績報告（[CoveringReport]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。提出で起こした報告の保存や、 「種牡馬 ×
 * 種付年」で提出は一度という集合制約の検証（同年の既存報告の引き当て）に用いる。
 */
@Repository
interface CoveringReportRepository {
    /** 種付成績報告IDで検索する。存在しなければ null。 */
    fun findById(id: CoveringReportId): CoveringReport?

    /**
     * 同一種牡馬（繁殖登録）・同一種付年の既存の種付成績報告を検索する。存在しなければ null。
     *
     * 種付成績報告は「種牡馬 × 種付年」で一意であり、年次提出の重複（同一年の二重提出）検出に用いる。
     */
    fun findByStallionRegistrationIdAndCoveringYear(
        stallionRegistrationId: BreedingRegistrationId,
        coveringYear: Year,
    ): CoveringReport?

    /**
     * 種付成績報告を永続化する。
     *
     * 集約の [CoveringReport.version] が null なら insert、非 null なら楽観ロック付き update になる （Spring Data JDBC の
     * version 判別）。update が読み取り時点から他の更新と競合していた （または行が並行削除されていた）場合は [UpdateConflict] を返す。
     */
    fun save(coveringReport: CoveringReport): Result<CoveringReport, UpdateConflict>
}
