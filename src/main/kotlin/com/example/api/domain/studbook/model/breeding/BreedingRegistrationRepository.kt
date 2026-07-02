package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.Versioned
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.Repository

/**
 * 繁殖登録（[BreedingRegistration]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。 種付記録（recordCovering）の入力となる繁殖登録の取得や、繁殖登録の成立時の保存に
 * 用いる。
 */
@Repository
interface BreedingRegistrationRepository {
    /** 繁殖登録IDで検索する。存在しなければ null。更新に使う楽観ロック version を [Versioned] で同梱して返す。 */
    fun findById(id: BreedingRegistrationId): Versioned<BreedingRegistration>?

    /** 繁殖登録を新規に永続化する（insert 専用）。既存集約の更新（供用停止等）は [update] を使う。 */
    fun save(breedingRegistration: BreedingRegistration): BreedingRegistration

    /**
     * 既存の繁殖登録を楽観ロック付きで更新する（供用停止の反映等）。
     *
     * [Versioned.version]（読み取り時点の version）が現在行と一致するときだけ更新し、version を進めた
     * 新しい封筒を返す。読み取り後に他の更新が入っていた（または行が消えていた）場合は [UpdateConflict]。
     */
    fun update(
        versioned: Versioned<BreedingRegistration>
    ): Result<Versioned<BreedingRegistration>, UpdateConflict>
}
