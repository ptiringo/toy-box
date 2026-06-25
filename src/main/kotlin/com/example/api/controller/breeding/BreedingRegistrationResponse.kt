package com.example.api.controller.breeding

import com.example.api.domain.studbook.model.breeding.BreedingRegistration
import com.example.api.domain.studbook.model.breeding.BreedingRetirement
import java.time.LocalDate
import java.util.UUID

/**
 * 繁殖登録リソースの表現（HTTP 契約）。
 *
 * 繁殖登録に対する操作（登録の Create、将来の供用停止 `:retire` カスタムメソッド #408）は、 [AIP-133](https://google.aip.dev/133) /
 * [AIP-136](https://google.aip.dev/136) に倣い一律でこのリソース表現全体を返す （[ADR-0008]
 * の単一リソース表現）。供用停止前は未設定になりうる属性（[retirement]）は null で表す。 ロール・事由はドメイン enum を晒さず wire enum
 * （[BreedingRoleDto] / [RetirementReasonDto]）で公開する（ADR-0007）。
 *
 * @property id 繁殖登録の生 UUID
 * @property registrationNumber 繁殖登録番号
 * @property registeredHorseId 繁殖登録した個体（血統登録済み）の軽種馬の生 UUID
 * @property role 繁殖登録によって付与されたロール（種牡馬／繁殖牝馬）
 * @property retirement 供用停止。供用中なら null、供用停止済みなら事由と発生日を持つ
 */
data class BreedingRegistrationResponse(
    val id: UUID,
    val registrationNumber: String,
    val registeredHorseId: UUID,
    val role: BreedingRoleDto,
    val retirement: BreedingRetirementResponse?,
)

/**
 * 繁殖供用停止の表現（[BreedingRegistrationResponse.retirement]）。
 *
 * @property reason 供用停止の事由
 * @property occurredOn 事由が発生した日
 */
data class BreedingRetirementResponse(val reason: RetirementReasonDto, val occurredOn: LocalDate)

/** [BreedingRegistration] を繁殖登録リソースの表現へ変換する。各操作の成功レスポンスはこのリソース表現を一律で返す。 */
fun BreedingRegistration.toResponse(): BreedingRegistrationResponse =
    BreedingRegistrationResponse(
        id = id.value,
        registrationNumber = registrationNumber.value,
        registeredHorseId = registeredHorseId.value,
        role = role.toApi(),
        retirement = retirement?.toResponse(),
    )

/** [BreedingRetirement] を供用停止の表現へ変換する。 */
fun BreedingRetirement.toResponse(): BreedingRetirementResponse =
    BreedingRetirementResponse(reason = reason.toApi(), occurredOn = occurredOn)
