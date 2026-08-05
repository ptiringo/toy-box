package com.example.api.domain.iam.model.account

import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrThrow
import org.junit.jupiter.api.Test

class AccountTest {

    @Test
    fun `subject を与えるとアカウントを生成できる`() {
        val account =
            Account.create("firebase-uid-123").getOrThrow { AssertionError(it.toString()) }

        assert(account.subjectId == SubjectId("firebase-uid-123"))
    }

    @Test
    fun `生成時に一意な ID が採番される`() {
        val first = Account.create("sub-a").getOrThrow { AssertionError(it.toString()) }
        val second = Account.create("sub-b").getOrThrow { AssertionError(it.toString()) }

        assert(first.id != second.id)
    }

    @Test
    fun `生成直後の version は null`() {
        val account = Account.create("sub-new").getOrThrow { AssertionError(it.toString()) }

        assert(account.version == null)
    }

    @Test
    fun `subject がブランクならアカウントを生成できない`() {
        val error = Account.create("   ").getError()

        assert(error == BlankSubjectId)
    }

    @Test
    fun `ID が同じアカウントは等価とみなす`() {
        val original = Account.create("sub-same").getOrThrow { AssertionError(it.toString()) }
        val restored = Account.reconstitute(original.id, SubjectId("sub-different"), 3L)

        assert(original == restored)
    }
}
