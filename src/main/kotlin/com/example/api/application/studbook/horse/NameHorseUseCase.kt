package com.example.api.application.studbook.horse

import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseId
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.HorseName
import com.example.api.domain.studbook.model.horse.bloodhorse.NameHorseError
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.service.horse.nameHorse
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toResultOr
import java.util.UUID
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 馬名登録ユースケースの入力コマンド。
 *
 * 馬名登録は血統登録済みの個体を対象とするため、対象は登録済み軽種馬の ID で参照する。馬名は VO で表すため 素の文字列で受け取り、ユースケース内で [HorseName] の
 * `create` を通して検証する。
 *
 * @property bloodHorseId 命名対象の軽種馬ID
 * @property name 付与する馬名
 */
data class NameHorseCommand(val bloodHorseId: UUID, val name: String)

/** 馬名登録時に発生しうる業務ルール違反。 */
sealed interface NameHorseUseCaseError {
    /** 馬名が不変条件（カタカナ2〜9文字）を満たさない。 */
    data object InvalidName : NameHorseUseCaseError

    /** 命名対象として指定された軽種馬が存在しない。 */
    data class HorseNotFound(val bloodHorseId: UUID) : NameHorseUseCaseError

    /**
     * 対象の軽種馬が既に命名済みで、二重命名はできない。
     *
     * @property currentName 既に付与されている馬名
     */
    data class AlreadyNamed(val currentName: String) : NameHorseUseCaseError

    /** 申請された馬名が既に他の軽種馬で使用済み。 */
    data class NameAlreadyTaken(val name: String) : NameHorseUseCaseError

    /**
     * 命名対象の軽種馬に紐づく審査が存在しない。
     *
     * 血統登録時に審査を必ず生成・保存するため、命名フローでは通常起こりえない内部不整合相当の状態。
     *
     * @property inspectionId 引き当てに失敗した審査の ID
     */
    data class InspectionNotFound(val inspectionId: UUID) : NameHorseUseCaseError

    /**
     * 読み取りから命名確定までの間に、対象の軽種馬が他の更新と競合した。
     *
     * 楽観ロック（読み取り時点の version との不一致）による検出。最新の状態を取得して再操作すれば解消しうる。
     *
     * @property bloodHorseId 競合した軽種馬ID
     */
    data class ConcurrentModification(val bloodHorseId: UUID) : NameHorseUseCaseError
}

/**
 * 馬名登録ユースケース。
 *
 * 境界の生入力を [HorseName] に変換し（不正なら検証エラー）、対象の軽種馬を [BloodHorseRepository] で引き当て、 集約の `assignName`
 * で命名状態を遷移させてから楽観ロック付きで更新する（競合は [NameHorseUseCaseError.ConcurrentModification]）。血統登録 →
 * 馬名登録という順序関係は、対象が 既に永続化済みの [BloodHorse] であることを引当が要求することで自然に満たされる。
 *
 * 状態遷移が同梱して返すドメインイベント（`HorseNamed`）は、更新の保存後に [ApplicationEventPublisher] で発行する。 ユースケース全体を
 * `@Transactional` で 1 トランザクションに収めており、`AFTER_COMMIT` 購読者へはコミット確定後にのみ届く
 * （publish-after-commit。失敗遷移・業務エラー時はイベント自体を生成せず発行もされない）。決定経緯は ADR-0050。
 *
 * @return 命名された [RegisteredBloodHorse]、または業務ルール違反を表す [NameHorseUseCaseError]
 */
@Service
@Transactional
class NameHorseUseCase(
    private val bloodHorseRepository: BloodHorseRepository,
    private val horseInspectionRepository: HorseInspectionRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    operator fun invoke(
        command: Command<NameHorseCommand>
    ): Result<RegisteredBloodHorse, NameHorseUseCaseError> = binding {
        val input = command.payload

        val horseName =
            HorseName.create(input.name).mapError { NameHorseUseCaseError.InvalidName }.bind()

        val horse =
            bloodHorseRepository
                .findById(BloodHorseId(input.bloodHorseId))
                .toResultOr { NameHorseUseCaseError.HorseNotFound(input.bloodHorseId) }
                .bind()

        val transition =
            nameHorse(horse, horseName, bloodHorseRepository)
                .mapError { error ->
                    when (error) {
                        is NameHorseError.NameAlreadyTaken ->
                            NameHorseUseCaseError.NameAlreadyTaken(error.name.value)
                        is NameHorseError.AlreadyNamed ->
                            NameHorseUseCaseError.AlreadyNamed(error.currentName.value)
                    }
                }
                .bind()

        // response（マイクロチップを露出）の組み立てに審査が要るため、改名の保存前に引き当てる。
        // inspectionId は命名遷移で変わらないため save 前に引き当てて問題ない。
        // 審査が欠落（InspectionNotFound）なら改名を save せず返す（エラーなのに改名済みになるのを避ける）。
        // 血統登録時に審査を必ず生成・保存しているため、欠落は通常ありえない内部不整合相当。
        val inspection =
            horseInspectionRepository
                .findById(transition.aggregate.inspectionId)
                .toResultOr {
                    NameHorseUseCaseError.InspectionNotFound(
                        transition.aggregate.inspectionId.value
                    )
                }
                .bind()

        val named =
            bloodHorseRepository
                .save(transition.aggregate)
                .mapError { NameHorseUseCaseError.ConcurrentModification(input.bloodHorseId) }
                .bind()
        // 発行はトランザクション内で行うが、AFTER_COMMIT 購読者への配送はコミット確定後（ADR-0050）。
        eventPublisher.publishEvent(transition.event)

        RegisteredBloodHorse(named, inspection)
    }
}
