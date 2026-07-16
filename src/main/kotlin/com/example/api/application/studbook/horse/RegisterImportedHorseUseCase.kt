package com.example.api.application.studbook.horse

import com.example.api.application.shared.AuthorizationError
import com.example.api.domain.shared.Actor
import com.example.api.domain.shared.Command
import com.example.api.domain.shared.Permission
import com.example.api.domain.studbook.model.StudbookPermissions
import com.example.api.domain.studbook.model.horse.bloodhorse.BlankBreeder
import com.example.api.domain.studbook.model.horse.bloodhorse.BlankOriginCountry
import com.example.api.domain.studbook.model.horse.bloodhorse.BlankPedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.Breeder
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.DateOfBirth
import com.example.api.domain.studbook.model.horse.bloodhorse.ImportedHorseEntry
import com.example.api.domain.studbook.model.horse.bloodhorse.LandingDate
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.example.api.domain.studbook.model.inspection.HorseInspection
import com.example.api.domain.studbook.model.inspection.HorseInspectionRepository
import com.example.api.domain.studbook.model.inspection.InvalidMicrochipNumber
import com.example.api.domain.studbook.model.inspection.MicrochipNumber
import com.example.api.domain.studbook.model.inspection.ParentageDetermination
import com.example.api.domain.studbook.service.horse.ensurePedigreeRegistrationNumberAvailable
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.mapError
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 輸入馬血統登録ユースケースの入力コマンド。
 *
 * 輸入馬・基礎輸入馬の登録申請書に相当する境界の生入力。内国産馬（[RegisterInStudBookCommand]）と異なり父母 ID・DNA 親子判定結果は
 * 持たず、代わりに原産国・揚陸日を持つ。VO で表す項目（番号・マイクロチップ・生産者・原産国）は素の文字列で受け取り、ユースケース内で 各 VO の `create` を通して検証する。
 *
 * @property sex 性
 * @property coatColor 毛色
 * @property breedType 品種
 * @property dateOfBirth 生年月日
 * @property breeder 生産者名
 * @property microchipNumber マイクロチップ番号
 * @property originCountry 原産国名
 * @property landingDate 揚陸日
 * @property registrationNumber 交付される血統登録番号
 */
@Suppress("LongParameterList") // 登録申請フォーム相当の境界入力であり、項目の分割はかえって意味を損なう
data class RegisterImportedHorseCommand(
    val sex: Sex,
    val coatColor: CoatColor,
    val breedType: BreedType,
    val dateOfBirth: LocalDate,
    val breeder: String,
    val microchipNumber: String,
    val originCountry: String,
    val landingDate: LocalDate,
    val registrationNumber: String,
)

/** 輸入馬血統登録時に発生しうる業務ルール違反。 */
sealed interface RegisterImportedHorseUseCaseError {
    /** 血統登録番号がブランク。 */
    data object InvalidRegistrationNumber : RegisterImportedHorseUseCaseError

    /** 血統登録番号が既に他の軽種馬に採番済み。 */
    data class RegistrationNumberAlreadyTaken(val registrationNumber: String) :
        RegisterImportedHorseUseCaseError

    /** マイクロチップ番号が 15 桁の数字でない。 */
    data object InvalidMicrochipNumber : RegisterImportedHorseUseCaseError

    /** 生産者名がブランク。 */
    data object BlankBreeder : RegisterImportedHorseUseCaseError

    /** 原産国名がブランク。 */
    data object BlankOriginCountry : RegisterImportedHorseUseCaseError

    /** 輸入馬血統登録に必要な権限を持たない。 */
    data class Forbidden(override val permission: Permission) :
        RegisterImportedHorseUseCaseError, AuthorizationError
}

/**
 * 輸入馬血統登録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、生成ファクトリ [BloodHorse.createImported] で輸入馬の [BloodHorse] を生成して
 * 永続化する。父母が当システムに存在しないため、内国産馬の登録（[RegisterInStudBookUseCase]）のような父母の引き当て・前提条件検証は 行わない。Controller
 * 層は本クラスのみに依存し、ドメインの生成経路の詳細は知らない。
 *
 * @return 登録された [RegisteredBloodHorse]、または業務ルール違反を表す [RegisterImportedHorseUseCaseError]
 */
@Service
class RegisterImportedHorseUseCase(
    private val bloodHorseRepository: BloodHorseRepository,
    private val horseInspectionRepository: HorseInspectionRepository,
) {
    @Transactional
    operator fun invoke(
        actor: Actor,
        command: Command<RegisterImportedHorseCommand>,
    ): Result<RegisteredBloodHorse, RegisterImportedHorseUseCaseError> {
        val permission = StudbookPermissions.HORSE_REGISTER_IMPORTED
        if (!actor.can(permission)) {
            return Err(RegisterImportedHorseUseCaseError.Forbidden(permission))
        }
        return binding {
            val input = command.payload

            val registrationNumber =
                PedigreeRegistrationNumber.create(input.registrationNumber)
                    .mapError { _: BlankPedigreeRegistrationNumber ->
                        RegisterImportedHorseUseCaseError.InvalidRegistrationNumber
                    }
                    .bind()
            ensurePedigreeRegistrationNumberAvailable(registrationNumber, bloodHorseRepository)
                .mapError {
                    RegisterImportedHorseUseCaseError.RegistrationNumberAlreadyTaken(
                        it.number.value
                    )
                }
                .bind()
            val microchipNumber =
                MicrochipNumber.create(input.microchipNumber)
                    .mapError { _: InvalidMicrochipNumber ->
                        RegisterImportedHorseUseCaseError.InvalidMicrochipNumber
                    }
                    .bind()
            val breeder =
                Breeder.create(input.breeder)
                    .mapError { _: BlankBreeder -> RegisterImportedHorseUseCaseError.BlankBreeder }
                    .bind()
            val originCountry =
                OriginCountry.create(input.originCountry)
                    .mapError { _: BlankOriginCountry ->
                        RegisterImportedHorseUseCaseError.BlankOriginCountry
                    }
                    .bind()

            val entry =
                ImportedHorseEntry(
                    sex = input.sex,
                    coatColor = input.coatColor,
                    breedType = input.breedType,
                    dateOfBirth = DateOfBirth(input.dateOfBirth),
                    breeder = breeder,
                    originCountry = originCountry,
                    landingDate = LandingDate(input.landingDate),
                )

            // 審査をメモリ内で組み立て、createImported 後に永続化する（登録系ユースケースで一貫した順序）。
            // createImported は失敗しないが、内国産馬登録との一貫性のため「組み立て → create → save」の順に揃える。
            val inspection =
                HorseInspection.create(
                    microchipNumber = microchipNumber,
                    parentage = ParentageDetermination.NotApplicable,
                )

            val bloodHorse = BloodHorse.createImported(entry, inspection, registrationNumber)

            // 審査と軽種馬の 2 集約書き込みは invoke の @Transactional 境界内で原子的に行う（#483）。
            horseInspectionRepository.save(inspection)
            val saved =
                bloodHorseRepository.save(bloodHorse).getOrElse {
                    error("新規の軽種馬の保存で楽観ロック競合はありえない: id=${bloodHorse.id.value}")
                }
            RegisteredBloodHorse(saved, inspection)
        }
    }
}
