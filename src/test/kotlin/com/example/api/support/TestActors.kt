package com.example.api.support

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.Actor
import com.example.api.domain.studbook.model.StudbookPermissions
import java.util.UUID

/**
 * テスト用の [Actor] ファクトリ。
 *
 * `@WebMvcTest` の slice テストは HTTP 契約（シリアライズ・ステータス・ProblemDetail 描画）の検証が目的で、 認可分岐（権限不足で 403）は
 * UseCase 単体テストの責務。したがって slice テストは「権限が足りて素通りする
 * Actor」だけあればよく、必要権限のサブセットを各テストへ書き並べる必要はない。新しい書き込み権限が増えても ここ 1 箇所で追随できるよう、seed となる全権限 Actor を集約する。
 */
object TestActors {
    /** studbook の全書き込み権限を持つ Actor。slice テストの happy-path 用。 */
    fun studbookFullyPermitted(): Actor =
        Actor(
            AccountId(UUID.randomUUID()),
            setOf(
                StudbookPermissions.HORSE_REGISTER,
                StudbookPermissions.HORSE_REGISTER_IMPORTED,
                StudbookPermissions.HORSE_REGISTER_FOAL,
                StudbookPermissions.HORSE_NAME,
                StudbookPermissions.INSPECTION_RECORD,
                StudbookPermissions.BREEDING_REGISTRATION_REGISTER,
                StudbookPermissions.BREEDING_RESULT_RECORD_COVERING,
                StudbookPermissions.BREEDING_RESULT_RECORD_UNCOVERED,
                StudbookPermissions.BREEDING_RESULT_REPORT_FOALING,
                StudbookPermissions.BREEDING_RESULT_SUBMIT_REPORT,
                StudbookPermissions.COVERING_REPORT_SUBMIT,
            ),
        )
}
