package com.example.api.application.studbook.horse

import com.example.api.domain.shared.Command
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorse
import com.example.api.domain.studbook.model.horse.bloodhorse.BloodHorseRepository
import com.example.api.domain.studbook.model.horse.bloodhorse.BreedType
import com.example.api.domain.studbook.model.horse.bloodhorse.Breeder
import com.example.api.domain.studbook.model.horse.bloodhorse.CoatColor
import com.example.api.domain.studbook.model.horse.bloodhorse.DateOfBirth
import com.example.api.domain.studbook.model.horse.bloodhorse.ImportedHorseEntry
import com.example.api.domain.studbook.model.horse.bloodhorse.LandingDate
import com.example.api.domain.studbook.model.horse.bloodhorse.MicrochipNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.OriginCountry
import com.example.api.domain.studbook.model.horse.bloodhorse.PedigreeRegistrationNumber
import com.example.api.domain.studbook.model.horse.bloodhorse.Sex
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import java.time.LocalDate
import org.springframework.stereotype.Service

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

    /** マイクロチップ番号が 15 桁の数字でない。 */
    data object InvalidMicrochipNumber : RegisterImportedHorseUseCaseError

    /** 生産者名がブランク。 */
    data object BlankBreeder : RegisterImportedHorseUseCaseError

    /** 原産国名がブランク。 */
    data object BlankOriginCountry : RegisterImportedHorseUseCaseError
}

/**
 * 輸入馬血統登録ユースケース。
 *
 * 境界の生入力を VO に変換し（不正なら検証エラー）、生成ファクトリ [BloodHorse.createImported] で輸入馬の [BloodHorse] を生成して
 * 永続化する。父母が当システムに存在しないため、内国産馬の登録（[RegisterInStudBookUseCase]）のような父母の引き当て・前提条件検証は 行わない。Controller
 * 層は本クラスのみに依存し、ドメインの生成経路の詳細は知らない。
 *
 * @return 登録された [BloodHorse]、または業務ルール違反を表す [RegisterImportedHorseUseCaseError]
 */
@Service
class RegisterImportedHorseUseCase(private val bloodHorseRepository: BloodHorseRepository) {
    operator fun invoke(
        command: Command<RegisterImportedHorseCommand>
    ): Result<BloodHorse, RegisterImportedHorseUseCaseError> = binding {
        val input = command.payload

        val registrationNumber =
            PedigreeRegistrationNumber.create(input.registrationNumber)
                .mapError { RegisterImportedHorseUseCaseError.InvalidRegistrationNumber }
                .bind()
        val microchipNumber =
            MicrochipNumber.create(input.microchipNumber)
                .mapError { RegisterImportedHorseUseCaseError.InvalidMicrochipNumber }
                .bind()
        val breeder =
            Breeder.create(input.breeder)
                .mapError { RegisterImportedHorseUseCaseError.BlankBreeder }
                .bind()
        val originCountry =
            OriginCountry.create(input.originCountry)
                .mapError { RegisterImportedHorseUseCaseError.BlankOriginCountry }
                .bind()

        val entry =
            ImportedHorseEntry(
                sex = input.sex,
                coatColor = input.coatColor,
                breedType = input.breedType,
                dateOfBirth = DateOfBirth(input.dateOfBirth),
                breeder = breeder,
                microchipNumber = microchipNumber,
                originCountry = originCountry,
                landingDate = LandingDate(input.landingDate),
            )

        val bloodHorse = BloodHorse.createImported(entry, registrationNumber)

        bloodHorseRepository.save(bloodHorse)
    }
}
