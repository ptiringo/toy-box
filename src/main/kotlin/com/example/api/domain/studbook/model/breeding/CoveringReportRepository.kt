package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.Result
import java.time.Year
import org.jmolecules.ddd.annotation.Repository

/**
 * 種付成績報告（[CoveringReport]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。提出で起こした報告の保存や、 「種牡馬 ×
 * 種付年」で提出は一度という集合制約の検証（同年の既存報告の引き当て）に用いる。
 *
 * 全ての口が [WorldId] を要求する（#704 / ADR-0067）。データは世界（セーブデータ＝テナント）ごとに閉じており、 集約自身は世界を知らないため、スコープは引数で運ぶ。
 */
@Repository
interface CoveringReportRepository {
    /** 指定の世界の中から種付成績報告IDで検索する。その世界に無ければ null。 */
    fun findById(worldId: WorldId, id: CoveringReportId): CoveringReport?

    /**
     * 指定の世界の中で、同一種牡馬（繁殖登録）・同一種付年の既存の種付成績報告を検索する。無ければ null。
     *
     * 種付成績報告は「種牡馬 × 種付年」で一意であり、年次提出の重複（同一年の二重提出）検出に用いる。 一意性は世界の中で閉じる（別の世界の同年の報告は衝突しない）。
     */
    fun findByStallionRegistrationIdAndCoveringYear(
        worldId: WorldId,
        stallionRegistrationId: BreedingRegistrationId,
        coveringYear: Year,
    ): CoveringReport?

    /**
     * 種付成績報告を指定の世界に永続化する。
     *
     * 集約の [CoveringReport.version] が null なら insert、非 null なら楽観ロック付き update になる （Spring Data JDBC の
     * version 判別）。update が読み取り時点から他の更新と競合していた （または行が並行削除されていた）場合は [UpdateConflict] を返す。
     */
    fun save(
        worldId: WorldId,
        coveringReport: CoveringReport,
    ): Result<CoveringReport, UpdateConflict>
}
