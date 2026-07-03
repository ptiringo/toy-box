package com.example.api.application.studbook.inspection

import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.inspection.HorseInspection
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.model.inspection.IdentificationFeatures
import com.example.api.domain.studbook.model.inspection.InvalidMicrochipNumber
import com.example.api.domain.studbook.model.inspection.MicrochipNumber
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import org.springframework.stereotype.Service

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
 * 審査記録ユースケース。
 *
 * 血統登録の内部で審査を生成する経路（[com.example.api.application.studbook.horse.RegisterInStudBookUseCase]）とは
 * 独立に、確定済みの審査を単独で記録する対外 API 経路（#484。登録フロー側は不変）。境界の生入力を [MicrochipNumber]
 * に検証変換し、[HorseInspection.create] で採番・生成して保存する。
 *
 * 失敗のしかたはマイクロチップ形式不正の 1 種類のみのため、専用の sealed は作らずドメインの [InvalidMicrochipNumber]
 * をそのまま返す（error-handling.md「1 種類なら単一型」。増えたら sealed へ昇格）。 単一集約の save のみでトランザクション論点はなく、`create`
 * はドメインイベントを返さない。
 *
 * @return 保存後の [HorseInspection]、またはマイクロチップ形式不正を表す [InvalidMicrochipNumber]
 */
@Service
class RecordHorseInspectionUseCase(
    private val horseInspectionRepository: HorseInspectionRepository
) {
    operator fun invoke(
        command: Command<RecordHorseInspectionCommand>
    ): Result<HorseInspection, InvalidMicrochipNumber> {
        val input = command.payload
        return MicrochipNumber.create(input.microchipNumber).map { microchipNumber ->
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
