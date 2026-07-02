package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.shared.UpdateConflict
import com.github.michaelbull.result.Result
import java.time.Year
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

    /**
     * 同一繁殖牝馬（繁殖登録）・同一繁殖年の既存の年次成績を検索する。存在しなければ null。
     *
     * 繁殖成績は「繁殖牝馬 × 繁殖年」で一意であり、種付記録・種付せず記録の重複（同一年の二重記録）検出に用いる。
     */
    fun findByBreedingRegistrationIdAndBreedingYear(
        breedingRegistrationId: BreedingRegistrationId,
        breedingYear: Year,
    ): BreedingResult?

    /**
     * 繁殖成績を永続化する。
     *
     * 集約の [BreedingResult.version] が null なら insert、非 null なら楽観ロック付き update になる （Spring Data JDBC の
     * version 判別）。update が読み取り時点から他の更新と競合していた （または行が並行削除されていた）場合は [UpdateConflict] を返す。
     */
    fun save(breedingResult: BreedingResult): Result<BreedingResult, UpdateConflict>
}
