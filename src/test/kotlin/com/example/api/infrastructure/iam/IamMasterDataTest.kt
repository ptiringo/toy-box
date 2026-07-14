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
    fun `iam role_permissionはロール別の権限割当までKotlin期待値と完全一致する`() {
        // DISTINCT で「相異なる権限文字列の集合」だけを見ると、権限が誤ったロールへ付け替わって
        // も（例: REGISTRAR 専用の権限が BREEDER に紛れ込んでも）集合としては不変なので検出できない。
        // ロール名ごとに畳み込み、ロール → 権限集合の対応そのものを突合する。
        val rowsInDb =
            jdbcClient
                .sql("SELECT role_name, permission FROM iam.role_permission")
                .query { rs, _ -> rs.getString("role_name") to rs.getString("permission") }
                .list()

        // キーは DB の行ではなく Role enum 全件から作る（VIEWER は行を持たないロールのため、DB 起点
        // だとキーそのものが現れず、期待値側で emptySet() を書いても比較が非対称になって空振りする）。
        val permissionsByRoleInDb: Map<Role, Set<String>> =
            Role.entries.associateWith { role ->
                rowsInDb.filter { it.first == role.name }.map { it.second }.toSet()
            }

        // REGISTRAR（登録機関職員）は全ての書き込みを行える = StudbookPermissions 全 11 定数 +
        // RacingPermissions.JOCKEY_REGISTER の 12 個。
        val registrarPermissions =
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
                    RacingPermissions.JOCKEY_REGISTER,
                )
                .map { it.value }
                .toSet()
        assert(registrarPermissions.size == 12)

        // BREEDER（生産者・種牡馬所有者）は届出系のみ。
        val breederPermissions =
            setOf(
                    StudbookPermissions.BREEDING_RESULT_RECORD_COVERING,
                    StudbookPermissions.BREEDING_RESULT_RECORD_UNCOVERED,
                    StudbookPermissions.BREEDING_RESULT_REPORT_FOALING,
                    StudbookPermissions.BREEDING_RESULT_SUBMIT_REPORT,
                    StudbookPermissions.COVERING_REPORT_SUBMIT,
                )
                .map { it.value }
                .toSet()
        assert(breederPermissions.size == 5)

        val permissionsByRoleExpected: Map<Role, Set<String>> =
            mapOf(
                Role.REGISTRAR to registrarPermissions,
                Role.BREEDER to breederPermissions,
                // VIEWER は書き込み権限を持たない（role_permission に行を持たない）ことを明示する。
                Role.VIEWER to emptySet(),
            )

        assert(permissionsByRoleInDb == permissionsByRoleExpected)
    }
}
