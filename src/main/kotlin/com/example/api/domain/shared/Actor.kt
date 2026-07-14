package com.example.api.domain.shared

import java.util.UUID
import org.jmolecules.ddd.annotation.ValueObject

/** アカウントの識別子。IdP の `sub` ではなく、この API 自身が採番する ID（ADR-0005 の UUIDv7）。 */
@ValueObject @JvmInline value class AccountId(val value: UUID)

/**
 * 個々の操作を許す権限。`<context>:<resource>:<action>` 形式の文字列で表す（例: `studbook:horse:name`）。
 *
 * 定数（語彙そのもの）は各境界づけられたコンテキストが持つ（`StudbookPermissions` /
 * `RacingPermissions`）。共有カーネルに置くのはこの値クラスだけで、全コンテキストの語彙をここへ 集めると共有カーネルが腐るため。
 */
@ValueObject @JvmInline value class Permission(val value: String)

/**
 * 「誰で、何ができるか」だけを運ぶパスポート。
 *
 * `Account` 集約は `iam` コンテキストに置く一方で `Actor` を共有カーネルに置くのは意図的な非対称。 アカウントのライフサイクル（作成・ロール付与）は `iam`
 * の関心事だが、`Actor` は全コンテキストの ユースケースが読む値であり、コンテキスト間依存の禁止（ArchUnit）の下ではここにしか置けない。
 *
 * リクエストごとに DB から組み立て、キャッシュしない（権限剥奪の即時反映を優先する）。
 */
@ValueObject
data class Actor(val accountId: AccountId, val permissions: Set<Permission>) {
    fun can(permission: Permission): Boolean = permission in permissions
}
