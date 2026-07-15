package com.example.api.application.studbook.inspection

import com.example.api.application.shared.AuthorizationError
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.Permission
import com.example.api.domain.studbook.model.StudbookPermissions
import com.example.api.domain.studbook.model.inspection.HorseInspection
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.model.inspection.IdentificationFeatures
import com.example.api.domain.studbook.model.inspection.InvalidMicrochipNumber
import com.example.api.domain.studbook.model.inspection.MicrochipNumber
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 審査記録ユースケースの入力コマンド。
 *
 * マイクロチップ番号は VO で表すため素の文字列で受け取り、ユースケース内で [MicrochipNumber] の `create` を 通して検証する。親子判定・特徴記述子は検証を持たない
 * VO のためドメイン型のまま保持する（wire との変換は controller 境界の責務。ADR-0007 でドメイン enum をコマンドに保持する既存規約と同じ扱い）。
 *
 * @property microchipNumber マイクロチップ番号（15 桁数字）
 * @property parentage 親子判定
 * @property features 特徴記述子。未記録なら null
 */
data class RecordHorseInspectionCommand(
    val microchipNumber: String,
    val parentage: ParentageDetermination,
    val features: IdentificationFeatures?,
)

/**
 * 審査記録時に発生しうる失敗。
 *
 * 権限不足（#606）が加わり失敗のしかたが 2 種類になったため、単一型からここへ昇格させた （error-handling.md「2 種類以上なら sealed へ昇格」）。
 */
sealed interface RecordHorseInspectionUseCaseError {
    /** マイクロチップ番号が 15 桁の数字でない。 */
    data object InvalidMicrochip : RecordHorseInspectionUseCaseError

    /** 審査記録に必要な権限を持たない。 */
    data class Forbidden(override val permission: Permission) :
        RecordHorseInspectionUseCaseError, AuthorizationError
}

/**
 * 審査記録ユースケース。
 *
 * 血統登録の内部で審査を生成する経路（[com.example.api.application.studbook.horse.RegisterInStudBookUseCase]）とは
 * 独立に、確定済みの審査を単独で記録する対外 API 経路（#484。登録フロー側は不変）。境界の生入力を [MicrochipNumber]
 * に検証変換し、[HorseInspection.create] で採番・生成して保存する。単一集約の save のみで
 * トランザクション論点はないが、書き込みユースケースの規約一様性（ADR-0051）のため `@Transactional` を付与する。`create` はドメインイベントを返さない。
 *
 * @return 保存後の [HorseInspection]、または業務ルール違反を表す [RecordHorseInspectionUseCaseError]
 */
@Service
class RecordHorseInspectionUseCase(
    private val horseInspectionRepository: HorseInspectionRepository
) {
    @Transactional
    operator fun invoke(
        actor: Actor,
        command: Command<RecordHorseInspectionCommand>,
    ): Result<HorseInspection, RecordHorseInspectionUseCaseError> {
        val permission = StudbookPermissions.INSPECTION_RECORD
        if (!actor.can(permission)) {
            return Err(RecordHorseInspectionUseCaseError.Forbidden(permission))
        }
        val input = command.payload
        return MicrochipNumber.create(input.microchipNumber)
            .mapError { _: InvalidMicrochipNumber ->
                RecordHorseInspectionUseCaseError.InvalidMicrochip
            }
            .map { microchipNumber ->
                horseInspectionRepository.save(
                    HorseInspection.create(
                        microchipNumber = microchipNumber,
                        parentage = input.parentage,
                        features = input.features,
                    )
                )
            }
    }
}
