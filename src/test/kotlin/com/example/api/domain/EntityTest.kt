package com.example.api.domain

import java.util.UUID
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Entity 基底クラスのユニットテスト */
class EntityTest {
    /** テスト用エンティティID */
    @JvmInline value class TestEntityId(val value: UUID)

    /** テスト用エンティティ */
    class TestEntity(override val id: TestEntityId) : Entity<TestEntityId>()

    /** TestEntity と同じ ID 型を持つ別のテスト用エンティティ */
    class AnotherEntity(override val id: TestEntityId) : Entity<TestEntityId>()

    @Nested
    inner class EqualsTest {
        @Test
        fun `同じインスタンスは等価である`() {
            val entity = TestEntity(TestEntityId(UUID.randomUUID()))
            assert(entity == entity)
        }

        @Test
        fun `同じIDを持つインスタンスは等価である`() {
            val id = TestEntityId(UUID.randomUUID())
            assert(TestEntity(id) == TestEntity(id))
        }

        @Test
        fun `異なるIDを持つインスタンスは等価でない`() {
            val entity1 = TestEntity(TestEntityId(UUID.randomUUID()))
            val entity2 = TestEntity(TestEntityId(UUID.randomUUID()))
            assert(entity1 != entity2)
        }

        @Test
        fun `同じIDでも型が異なれば等価でない`() {
            val id = TestEntityId(UUID.randomUUID())
            assert(!TestEntity(id).equals(AnotherEntity(id)))
        }

        @Test
        fun `null とは等価でない`() {
            val entity = TestEntity(TestEntityId(UUID.randomUUID()))
            val other: Any? = null
            assert(entity != other)
        }
    }

    @Nested
    inner class HashCodeTest {
        @Test
        fun `同じIDを持つインスタンスは同じハッシュコードを返す`() {
            val id = TestEntityId(UUID.randomUUID())
            assert(TestEntity(id).hashCode() == TestEntity(id).hashCode())
        }

        @Test
        fun `異なるIDを持つインスタンスは異なるハッシュコードを返す`() {
            val entity1 = TestEntity(TestEntityId(UUID.randomUUID()))
            val entity2 = TestEntity(TestEntityId(UUID.randomUUID()))
            assert(entity1.hashCode() != entity2.hashCode())
        }
    }
}
