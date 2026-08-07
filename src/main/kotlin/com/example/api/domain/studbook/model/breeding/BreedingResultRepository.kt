package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.Result
import java.time.Year
import org.jmolecules.ddd.annotation.Repository

/**
 * 繁殖成績（[BreedingResult]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。 種付記録で起こした年次レコードの保存や、分娩結果報告のために対象成績を取得するのに用いる。
 *
 * 全ての口が [WorldId] を要求する（#704 / ADR-0067）。データは世界（セーブデータ＝テナント）ごとに閉じており、 集約自身は世界を知らないため、スコープは引数で運ぶ。
 */
@Repository
interface BreedingResultRepository {
    /** 指定の世界の中から繁殖成績IDで検索する。その世界に無ければ null。 */
    fun findById(worldId: WorldId, id: BreedingResultId): BreedingResult?

    /**
     * 指定の世界の中で、同一繁殖牝馬（繁殖登録）・同一繁殖年の既存の年次成績を検索する。無ければ null。
     *
     * 繁殖成績は「繁殖牝馬 × 繁殖年」で一意であり、種付記録・種付せず記録の重複（同一年の二重記録）検出に用いる。 一意性は世界の中で閉じる（別の世界の同年の記録は衝突しない）。
     */
    fun findByBreedingRegistrationIdAndBreedingYear(
        worldId: WorldId,
        breedingRegistrationId: BreedingRegistrationId,
        breedingYear: Year,
    ): BreedingResult?

    /**
     * 繁殖成績を指定の世界に永続化する。
     *
     * 集約の [BreedingResult.version] が null なら insert、非 null なら楽観ロック付き update になる （Spring Data JDBC の
     * version 判別）。update が読み取り時点から他の更新と競合していた （または行が並行削除されていた）場合は [UpdateConflict] を返す。
     */
    fun save(
        worldId: WorldId,
        breedingResult: BreedingResult,
    ): Result<BreedingResult, UpdateConflict>
}
