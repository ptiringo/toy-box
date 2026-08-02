package com.example.api.controller.horse.problem

import com.example.api.application.studbook.horse.BloodHorseNotFound
import com.example.api.application.studbook.horse.NameHorseUseCaseError
import com.example.api.application.studbook.horse.RegisterCarriedOverHorseUseCaseError
import com.example.api.application.studbook.horse.RegisterImportedHorseUseCaseError
import com.example.api.application.studbook.horse.RegisterInStudBookUseCaseError
import com.example.api.controller.problem
import com.example.api.domain.studbook.model.horse.bloodhorse.RegisterInStudBookError
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/**
 * 軽種馬リソースの業務エラーを RFC 9457 (`application/problem+json`) の [ProblemDetail] へ変換するマッパー群。
 *
 * リソースに紐づく失敗バリアント（馬名登録・血統登録・輸入馬登録）ごとに、どのエラーをどの `status` / `errorCode` に描画するかの方針をここ（adapter 層の
 * `problem/` パッケージ）へ集約する。
 */

/**
 * [NameHorseUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail] に変換する。
 *
 * - 馬名の不変条件違反は入力不正として 400 Bad Request
 * - 対象軽種馬の不在は、URL で指し示したリソースが存在しないため 404 Not Found
 * - 既に命名済みは、リソースの状態と要求が衝突するため 409 Conflict
 * - 申請馬名が既に使用済みは、原簿の既存馬名と衝突するため 409 Conflict
 * - 命名後の審査欠落は、登録時に必ず生成する審査が見つからない内部不整合相当のため 422 Unprocessable Entity
 * - 更新競合（楽観ロック）は状態の競合として 409 Conflict
 */
fun NameHorseUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        NameHorseUseCaseError.InvalidName ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-horse-name",
                title = "Invalid horse name",
                detail = "name はカタカナ 2〜9 文字でなければなりません。",
            )
        is NameHorseUseCaseError.HorseNotFound ->
            problem(
                    status = HttpStatus.NOT_FOUND,
                    code = "horse-not-found",
                    title = "Horse not found",
                    detail = "命名対象として指定された軽種馬が存在しません。",
                )
                .apply { setProperty("blood_horse_id", bloodHorseId) }
        is NameHorseUseCaseError.AlreadyNamed ->
            problem(
                    status = HttpStatus.CONFLICT,
                    code = "horse-already-named",
                    title = "Horse already named",
                    detail = "対象の軽種馬は既に命名済みのため、再命名はできません。",
                )
                .apply { setProperty("current_name", currentName) }
        is NameHorseUseCaseError.NameAlreadyTaken ->
            problem(
                    status = HttpStatus.CONFLICT,
                    code = "horse-name-already-taken",
                    title = "Horse name already taken",
                    detail = "申請された馬名は既に他の軽種馬で使用されています。",
                )
                .apply { setProperty("name", name) }
        is NameHorseUseCaseError.InspectionNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_CONTENT,
                    code = "inspection-not-found",
                    title = "Inspection not found",
                    detail = "命名対象の軽種馬に紐づく審査が見つかりません。",
                )
                .apply { setProperty("inspection_id", inspectionId) }
        is NameHorseUseCaseError.ConcurrentModification ->
            problem(
                    status = HttpStatus.CONFLICT,
                    code = "concurrent-modification",
                    title = "Concurrent modification",
                    detail = "対象の軽種馬が他の更新と競合しました。最新の状態を取得してやり直してください。",
                )
                .apply { setProperty("blood_horse_id", bloodHorseId) }
    }

/**
 * [RegisterInStudBookUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail] に変換する。
 *
 * - VO 検証エラーは入力不正として 400 Bad Request
 * - 父母の不在（ボディ内 sire_id / dam_id の参照先不在）・ドメイン前提条件違反は、整った入力だが意味的に 処理できないため 422 Unprocessable
 *   Entity（判断基準は ADR-0018 / ADR-0021、api-design.md「404 vs 422」）
 */
fun RegisterInStudBookUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterInStudBookUseCaseError.InvalidRegistrationNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-registration-number",
                title = "Invalid registration number",
                detail = "registration_number は空であってはいけません。",
            )
        RegisterInStudBookUseCaseError.InvalidMicrochipNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-microchip-number",
                title = "Invalid microchip number",
                detail = "microchip_number は 15 桁の数字でなければなりません。",
            )
        RegisterInStudBookUseCaseError.BlankBreeder ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "blank-breeder",
                title = "Breeder is blank",
                detail = "breeder は空であってはいけません。",
            )
        is RegisterInStudBookUseCaseError.SireNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_CONTENT,
                    code = "sire-not-found",
                    title = "Sire not found",
                    detail = "父として指定された軽種馬が存在しません。",
                )
                .apply { setProperty("sire_id", sireId) }
        is RegisterInStudBookUseCaseError.DamNotFound ->
            problem(
                    status = HttpStatus.UNPROCESSABLE_CONTENT,
                    code = "dam-not-found",
                    title = "Dam not found",
                    detail = "母として指定された軽種馬が存在しません。",
                )
                .apply { setProperty("dam_id", damId) }
        is RegisterInStudBookUseCaseError.PreconditionViolated -> cause.toProblemDetail()
    }

private fun RegisterInStudBookError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterInStudBookError.SireNotMale ->
            problem(
                status = HttpStatus.UNPROCESSABLE_CONTENT,
                code = "sire-not-male",
                title = "Sire is not male",
                detail = "父として指定された軽種馬が雄ではありません。",
            )
        RegisterInStudBookError.DamNotFemale ->
            problem(
                status = HttpStatus.UNPROCESSABLE_CONTENT,
                code = "dam-not-female",
                title = "Dam is not female",
                detail = "母として指定された軽種馬が雌ではありません。",
            )
        RegisterInStudBookError.ParentageNotConfirmed ->
            problem(
                status = HttpStatus.UNPROCESSABLE_CONTENT,
                code = "parentage-not-confirmed",
                title = "Parentage not confirmed",
                detail = "申告された父母との DNA 型による親子判定が確認できません。",
            )
        RegisterInStudBookError.BreedMismatch ->
            problem(
                status = HttpStatus.UNPROCESSABLE_CONTENT,
                code = "breed-mismatch",
                title = "Breed mismatch",
                detail = "仔の品種が父母の品種と整合しません。",
            )
        RegisterInStudBookError.GrayFoalFromNonGrayParents ->
            problem(
                status = HttpStatus.UNPROCESSABLE_CONTENT,
                code = "gray-foal-from-non-gray-parents",
                title = "Gray foal from non-gray parents",
                detail = "芦毛でない父母の間に生まれた仔を芦毛として登録することはできません。",
            )
        RegisterInStudBookError.NonChestnutFoalFromChestnutParents ->
            problem(
                status = HttpStatus.UNPROCESSABLE_CONTENT,
                code = "non-chestnut-foal-from-chestnut-parents",
                title = "Non-chestnut foal from chestnut parents",
                detail = "栗毛（栃栗毛を含む）の父母の間に生まれた仔は栗毛でなければなりません。",
            )
    }

/**
 * [RegisterImportedHorseUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail] に
 * 変換する。
 *
 * 輸入馬登録は父母の引き当てを行わないため、失敗は VO 検証エラー（入力不正）のみで、すべて 400 Bad Request とする。
 */
fun RegisterImportedHorseUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterImportedHorseUseCaseError.InvalidRegistrationNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-registration-number",
                title = "Invalid registration number",
                detail = "registration_number は空であってはいけません。",
            )
        RegisterImportedHorseUseCaseError.InvalidMicrochipNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-microchip-number",
                title = "Invalid microchip number",
                detail = "microchip_number は 15 桁の数字でなければなりません。",
            )
        RegisterImportedHorseUseCaseError.BlankBreeder ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "blank-breeder",
                title = "Breeder is blank",
                detail = "breeder は空であってはいけません。",
            )
        RegisterImportedHorseUseCaseError.BlankOriginCountry ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "blank-origin-country",
                title = "Origin country is blank",
                detail = "origin_country は空であってはいけません。",
            )
    }

/**
 * [RegisterCarriedOverHorseUseCaseError] を RFC 9457 (`application/problem+json`) の [ProblemDetail]
 * に変換する。
 *
 * 移行取り込みは父母の引き当てを行わないため、失敗は VO 検証エラー（入力不正）のみで、すべて 400 Bad Request とする。
 */
fun RegisterCarriedOverHorseUseCaseError.toProblemDetail(): ProblemDetail =
    when (this) {
        RegisterCarriedOverHorseUseCaseError.InvalidRegistrationNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-registration-number",
                title = "Invalid registration number",
                detail = "registration_number は空であってはいけません。",
            )
        RegisterCarriedOverHorseUseCaseError.InvalidMicrochipNumber ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "invalid-microchip-number",
                title = "Invalid microchip number",
                detail = "microchip_number は 15 桁の数字でなければなりません。",
            )
        RegisterCarriedOverHorseUseCaseError.BlankBreeder ->
            problem(
                status = HttpStatus.BAD_REQUEST,
                code = "blank-breeder",
                title = "Breeder is blank",
                detail = "breeder は空であってはいけません。",
            )
    }

/**
 * [BloodHorseNotFound]（照会対象の軽種馬不在）を 404 Not Found の [ProblemDetail] に変換する。
 *
 * URL パス上の操作対象が不在のケースなので 404（api-design.md「リソース不在のステータス（404 vs 422）」）。
 * 馬名登録のパス対象不在（`NameHorseUseCaseError.HorseNotFound`）と意味もステータスも同じであるため `errorCode` を共用し、detail
 * だけ照会用の文言にする。クライアントから見た分類軸を 1 つに保つのが狙いで、 発生箇所ごとに type を割らない。
 *
 * なお `blood-horse-not-found` は繁殖登録のボディ内参照先不在（422）で既に使われている。同一 type が 404 と 422
 * の両方を持つと場合分けを濁すため、こちらへは流用しない。
 */
fun BloodHorseNotFound.toProblemDetail(): ProblemDetail =
    problem(
            status = HttpStatus.NOT_FOUND,
            code = "horse-not-found",
            title = "Horse not found",
            detail = "指定された ID の軽種馬は存在しません。",
        )
        .apply { setProperty("blood_horse_id", id) }
