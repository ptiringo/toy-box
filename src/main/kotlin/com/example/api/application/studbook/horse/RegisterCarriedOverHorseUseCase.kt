package com.example.api.application.studbook.horse

import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.horse.bloodhorse.BlankBreeder
import com.example.api.domain.studbook.model.horse.bloodhorse.BlankPedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.Breeder
import com.example.api.domain.studbook.model.horse.bloodhorse.CarriedOverHorseEntry
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.DateOfBirth
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.domain.studbook.model.inspection.HorseInspection
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.model.inspection.InvalidMicrochipNumber
import com.example.api.domain.studbook.model.inspection.MicrochipNumber
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 移行取り込み血統登録ユースケースの入力コマンド。
 *
 * 先行する登録原簿に血統登録済みの馬をシステム境界で取り込む際の生入力（JAIRS への新規登録申請ではない。 #633 /
 * ADR-0069）。輸入馬（[RegisterImportedHorseCommand]）と異なり原産国・揚陸日を持たず、 内国産馬と異なり父母 ID・DNA 親子判定結果も持たない。VO
 * で表す項目（番号・マイクロチップ・生産者）は 素の文字列で受け取り、ユースケース内で各 VO の `create` を通して検証する。
 *
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種（先行原簿の記録に基づく）
 * @property dateOfBirth 生年月日（先行原簿の記録に基づく）
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号
 * @property registrationNumber 血統登録番号（先行原簿で交付済みの番号を引き継ぐ）
 */
data class RegisterCarriedOverHorseCommand(
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val microchipNumber: String,
    val registrationNumber: String,
)

/** 移行取り込み血統登録時に発生しうる業務ルール違反。 */
sealed interface RegisterCarriedOverHorseUseCaseError {
    /** 血統登録番号がブランク。 */
    data object InvalidRegistrationNumber : RegisterCarriedOverHorseUseCaseError

    /** マイクロチップ番号が 15 桁の数字でない。 */
    data object InvalidMicrochipNumber : RegisterCarriedOverHorseUseCaseError

    /** 生産者名がブランク。 */
    data object BlankBreeder : RegisterCarriedOverHorseUseCaseError
}

/**
 * 移行取り込み血統登録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、生成ファクトリ [BloodHorse.createCarriedOver] で 移行取り込みの [BloodHorse]
 * を生成して永続化する。父母・血統は先行原簿に記録済みで当システムに存在しない
 * ため、父母の引き当て・前提条件検証は行わず、親子判定も実施しない（[ParentageDetermination.NotApplicable]）。 Controller
 * 層は本クラスのみに依存し、ドメインの生成経路の詳細は知らない。
 *
 * @return 登録された [RegisteredBloodHorse]、または業務ルール違反を表す [RegisterCarriedOverHorseUseCaseError]
 */
@Service
class RegisterCarriedOverHorseUseCase(
    private val bloodHorseRepository: BloodHorseRepository,
    private val horseInspectionRepository: HorseInspectionRepository,
) {
    @Transactional
    operator fun invoke(
        actor: Actor,
        command: Command<RegisterCarriedOverHorseCommand>,
    ): Result<RegisteredBloodHorse, RegisterCarriedOverHorseUseCaseError> = binding {
        val input = command.payload

        val registrationNumber =
            PedigreeRegistrationNumber.create(input.registrationNumber)
                .mapError { _: BlankPedigreeRegistrationNumber ->
                    RegisterCarriedOverHorseUseCaseError.InvalidRegistrationNumber
                }
                .bind()
        val microchipNumber =
            MicrochipNumber.create(input.microchipNumber)
                .mapError { _: InvalidMicrochipNumber ->
                    RegisterCarriedOverHorseUseCaseError.InvalidMicrochipNumber
                }
                .bind()
        val breeder =
            Breeder.create(input.breeder)
                .mapError { _: BlankBreeder -> RegisterCarriedOverHorseUseCaseError.BlankBreeder }
                .bind()

        val entry =
            CarriedOverHorseEntry(
                sex = input.sex,
                coatColor = input.coatColor,
                breedType = input.breedType,
                dateOfBirth = DateOfBirth(input.dateOfBirth),
                breeder = breeder,
            )

        // 審査をメモリ内で組み立て、createCarriedOver 後に永続化する（登録系ユースケースで一貫した順序）。
        // createCarriedOver は失敗しないが、他の登録経路との一貫性のため「組み立て → create → save」の順に揃える。
        val inspection =
            HorseInspection.create(
                microchipNumber = microchipNumber,
                parentage = ParentageDetermination.NotApplicable,
            )

        val bloodHorse = BloodHorse.createCarriedOver(entry, inspection, registrationNumber)

        // 審査と軽種馬の 2 集約書き込みは invoke の @Transactional 境界内で原子的に行う（#483）。
        horseInspectionRepository.save(actor.worldId, inspection)
        val saved =
            bloodHorseRepository.save(actor.worldId, bloodHorse).getOrElse {
                error("新規の軽種馬の保存で楽観ロック競合はありえない: id=${bloodHorse.id.value}")
            }
        RegisteredBloodHorse(saved, inspection)
    }
}
