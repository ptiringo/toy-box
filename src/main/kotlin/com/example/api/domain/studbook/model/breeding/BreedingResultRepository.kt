package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.Versioned
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
    /** 繁殖成績IDで検索する。存在しなければ null。更新に使う楽観ロック version を [Versioned] で同梱して返す。 */
    fun findById(id: BreedingResultId): Versioned<BreedingResult>?

    /**
     * 同一繁殖牝馬（繁殖登録）・同一繁殖年の既存の年次成績を検索する。存在しなければ null。
     *
     * 繁殖成績は「繁殖牝馬 × 繁殖年」で一意であり、種付記録・種付せず記録の重複（同一年の二重記録）検出に用いる。
     */
    fun findByBreedingRegistrationIdAndBreedingYear(
        breedingRegistrationId: BreedingRegistrationId,
        breedingYear: Year,
    ): BreedingResult?

    /** 繁殖成績を新規に永続化する（insert 専用）。既存集約の更新は [update] を使う。 */
    fun save(breedingResult: BreedingResult): BreedingResult

    /**
     * 既存の繁殖成績を楽観ロック付きで更新する。
     *
     * [Versioned.version]（読み取り時点の version）が現在行と一致するときだけ更新し、version を進めた
     * 新しい封筒を返す。読み取り後に他の更新が入っていた（または行が消えていた）場合は [UpdateConflict]。
     */
    fun update(
        versioned: Versioned<BreedingResult>
    ): Result<Versioned<BreedingResult>, UpdateConflict>
}
