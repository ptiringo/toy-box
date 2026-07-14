package com.example.api.infrastructure.iam

import com.example.api.domain.iam.model.account.Role
import com.example.api.domain.racing.model.RacingPermissions
import com.example.api.domain.studbook.model.StudbookPermissions
import com.example.api.support.PostgresContainerSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.TestConstructor.AutowireMode

/**
 * [#606] `iam` スキーマのマスタデータ（V15）と Kotlin 側の定数／enum が一致することを検証する契約テスト。
 *
 * 権限文字列は「Kotlin の [Permission] 定数」と「V15 の SQL INSERT」に二重定義されている。片方の typo は 「権限を持つはずの利用者が静かに 403
 * になる」という気づきにくい壊れ方をするため、Kotlin 内で完結する
 * [com.example.api.domain.studbook.model.StudbookPermissionsTest] 等では検出できない。ここでは実際に Flyway が適用した
 * PostgreSQL（Testcontainers、[PostgresContainerSupport]）に対して SELECT し、DB の マスタと Kotlin 側の集合を突合する。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = AutowireMode.ALL)
class IamMasterDataTest(private val jdbcClient: JdbcClient) : PostgresContainerSupport() {

    @Test
    fun `iam roleの行はRole enumの名前集合と完全一致する`() {
        val rolesInDb =
            jdbcClient.sql("SELECT name FROM iam.role").query(String::class.java).list().toSet()
        val rolesInEnum = Role.entries.map { it.name }.toSet()

        assert(rolesInDb == rolesInEnum)
    }

    @Test
    fun `iam role_permissionの相異なるpermission集合はKotlin定数12個の集合と完全一致する`() {
        val permissionsInDb =
            jdbcClient
                .sql("SELECT DISTINCT permission FROM iam.role_permission")
                .query(String::class.java)
                .list()
                .toSet()

        val permissionsInConstants =
            setOf(
                StudbookPermissions.HORSE_REGISTER.value,
                StudbookPermissions.HORSE_REGISTER_IMPORTED.value,
                StudbookPermissions.HORSE_REGISTER_FOAL.value,
                StudbookPermissions.HORSE_NAME.value,
                StudbookPermissions.INSPECTION_RECORD.value,
                StudbookPermissions.BREEDING_REGISTRATION_REGISTER.value,
                StudbookPermissions.BREEDING_RESULT_RECORD_COVERING.value,
                StudbookPermissions.BREEDING_RESULT_RECORD_UNCOVERED.value,
                StudbookPermissions.BREEDING_RESULT_REPORT_FOALING.value,
                StudbookPermissions.BREEDING_RESULT_SUBMIT_REPORT.value,
                StudbookPermissions.COVERING_REPORT_SUBMIT.value,
                RacingPermissions.JOCKEY_REGISTER.value,
            )
        assert(permissionsInConstants.size == 12)

        assert(permissionsInDb == permissionsInConstants)
    }
}
