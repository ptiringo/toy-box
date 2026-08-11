package com.example.api.mcp

import com.example.api.application.iam.world.WorldQueries
import com.example.api.domain.iam.model.account.Account
import com.example.api.domain.iam.model.account.AccountRepository
import com.example.api.domain.iam.model.account.SubjectId
import com.example.api.domain.shared.AccountId
import com.example.api.domain.shared.WorldId
import com.example.api.domain.shared.generateId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [McpActorFactory] のユニットテスト。
 *
 * adapter リングのため永続化ポートは mockk でスタブし、「設定の subject ＋ ツール引数の worldId から
 * [com.example.api.domain.shared.Actor] を組む」ことと、その 4 つの失敗だけを検証する。
 */
class McpActorFactoryTest {
    private val accounts = mockk<AccountRepository>()
    private val worlds = mockk<WorldQueries>()

    private val subject = "local-dev-subject"
    private val accountId = AccountId(generateId())
    private val account = Account.reconstitute(accountId, SubjectId(subject), version = 0)

    private fun factory(subjectId: String = subject) =
        McpActorFactory(McpProperties(subjectId = subjectId), accounts, worlds)

    @Test
    fun `自分が所有する世界のIDならActorを組む`() {
        val worldId = generateId()
        every { accounts.findBySubjectId(SubjectId(subject)) } returns account
        every { worlds.existsOwnedBy(accountId, WorldId(worldId)) } returns true

        val actor = factory().actorFor(worldId.toString())

        assert(actor.accountId == accountId)
        assert(actor.worldId == WorldId(worldId))
    }

    @Test
    fun `subject-id が未設定なら設定ミスとして落とす`() {
        val ex = assertThrows<IllegalStateException> { factory(subjectId = "").resolveAccountId() }
        assert(ex.message!!.contains("toy-box.mcp.subject-id"))
    }

    @Test
    fun `subject に対応するアカウントが未登録なら落とす`() {
        every { accounts.findBySubjectId(SubjectId(subject)) } returns null

        val ex = assertThrows<IllegalStateException> { factory().resolveAccountId() }
        assert(ex.message!!.contains(subject))
    }

    @Test
    fun `所有していない世界のIDなら見つからない扱いにする`() {
        val worldId = generateId()
        every { accounts.findBySubjectId(SubjectId(subject)) } returns account
        every { worlds.existsOwnedBy(accountId, WorldId(worldId)) } returns false

        val ex = assertThrows<NoSuchElementException> { factory().actorFor(worldId.toString()) }
        assert(ex.message == "world not found: $worldId")
    }

    @Test
    fun `不正なUUID文字列なら例外を送出する`() {
        every { accounts.findBySubjectId(SubjectId(subject)) } returns account

        assertThrows<IllegalArgumentException> { factory().actorFor("not-a-uuid") }
    }
}
