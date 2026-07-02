package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.shared.UpdateConflict
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.Repository

/**
 * 繁殖登録（[BreedingRegistration]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。 種付記録（recordCovering）の入力となる繁殖登録の取得や、繁殖登録の成立時・
 * 供用停止時の保存に用いる。
 */
@Repository
interface BreedingRegistrationRepository {
    /** 繁殖登録IDで検索する。存在しなければ null。 */
    fun findById(id: BreedingRegistrationId): BreedingRegistration?

    /**
     * 繁殖登録を永続化する。
     *
     * 集約の [BreedingRegistration.version] が null なら insert、非 null なら楽観ロック付き update になる （Spring Data
     * JDBC の version 判別）。update が読み取り時点から他の更新と競合していた （または行が並行削除されていた）場合は [UpdateConflict] を返す。
     */
    fun save(
        breedingRegistration: BreedingRegistration
    ): Result<BreedingRegistration, UpdateConflict>
}
