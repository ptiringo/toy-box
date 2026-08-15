package com.example.api.domain.studbook.model.breeding

import com.example.api.domain.shared.UpdateConflict
import com.example.api.domain.shared.WorldId
import com.github.michaelbull.result.Result
import org.jmolecules.ddd.annotation.Repository

/**
 * 繁殖登録（[BreedingRegistration]）の永続化を担うポート。
 *
 * ドメイン層はこのインターフェースのみを参照する。実装は infrastructure 層に置く。 種付記録（recordCovering）の入力となる繁殖登録の取得や、繁殖登録の成立時・
 * 供用停止時の保存に用いる。
 *
 * 全ての口が [WorldId] を要求する（#704 / ADR-0067）。データは世界（セーブデータ＝テナント）ごとに閉じており、 集約自身は世界を知らないため、スコープは引数で運ぶ。
 */
@Repository
interface BreedingRegistrationRepository {
    /** 指定の世界の中から繁殖登録IDで検索する。その世界に無ければ null。 */
    fun findById(worldId: WorldId, id: BreedingRegistrationId): BreedingRegistration?

    /**
     * 繁殖登録を指定の世界に永続化する。
     *
     * 集約の [BreedingRegistration.version] が null なら insert、非 null なら楽観ロック付き update になる （Spring Data
     * JDBC の version 判別）。update が読み取り時点から他の更新と競合していた （または行が並行削除されていた）場合は [UpdateConflict] を返す。
     */
    fun save(
        worldId: WorldId,
        breedingRegistration: BreedingRegistration,
    ): Result<BreedingRegistration, UpdateConflict>

    /**
     * 指定の世界の中で、その繁殖登録番号が既に他の繁殖登録に採番されているかを判定する（繁殖登録番号の一意性照合用）。
     *
     * 繁殖登録原簿と血統登録原簿は別の採番空間のため（登録規程 第3〜5条）、本ポートが見るのは繁殖登録番号だけで、 血統登録番号は
     * [com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository] が受け持つ。
     */
    fun existsByRegistrationNumber(worldId: WorldId, number: BreedingRegistrationNumber): Boolean
}
