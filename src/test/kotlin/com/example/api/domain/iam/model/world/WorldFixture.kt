package com.example.api.domain.iam.model.world

import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId

/** テスト用の [World] Object Mother。 */
object WorldFixture {
    fun world(
        id: WorldId = WorldId(generateId()),
        accountId: AccountId = AccountId(generateId()),
        name: String = "テストの世界",
        version: Long? = null,
    ): World = World.reconstitute(id, accountId, WorldName(name), version)
}
