package com.example.api.jmolecules

import com.example.api.domain.horse_racing.jockey.Jockey
import com.example.api.domain.horse_racing.jockey.JockeyId
import com.example.api.domain.sakamichi.Member
import com.example.api.domain.sakamichi.MemberId
import com.example.api.handler.HelloHandler
import org.jmolecules.ddd.annotation.AggregateRoot
import org.jmolecules.ddd.annotation.Service
import org.jmolecules.ddd.annotation.ValueObject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * jMolecules DDD アノテーションの付与を確認するテスト
 */
class JMoleculesAnnotationTest {

    /** 値オブジェクトアノテーションのテスト */
    @Nested
    inner class ValueObjectTest {
        @Test
        fun JockeyIdは値オブジェクトアノテーションが付与されている() {
            val annotation = JockeyId::class.java.getAnnotation(ValueObject::class.java)
            assert(annotation != null) { "JockeyIdに@ValueObjectアノテーションが付与されていません" }
        }

        @Test
        fun MemberIdは値オブジェクトアノテーションが付与されている() {
            val annotation = MemberId::class.java.getAnnotation(ValueObject::class.java)
            assert(annotation != null) { "MemberIdに@ValueObjectアノテーションが付与されていません" }
        }

        @Test
        fun HelloResponseは値オブジェクトアノテーションが付与されている() {
            val annotation = HelloHandler.HelloResponse::class.java.getAnnotation(ValueObject::class.java)
            assert(annotation != null) { "HelloResponseに@ValueObjectアノテーションが付与されていません" }
        }
    }

    /** 集約ルートアノテーションのテスト */
    @Nested
    inner class AggregateRootTest {
        @Test
        fun Jockeyは集約ルートアノテーションが付与されている() {
            val annotation = Jockey::class.java.getAnnotation(AggregateRoot::class.java)
            assert(annotation != null) { "Jockeyに@AggregateRootアノテーションが付与されていません" }
        }

        @Test
        fun Memberは集約ルートアノテーションが付与されている() {
            val annotation = Member::class.java.getAnnotation(AggregateRoot::class.java)
            assert(annotation != null) { "Memberに@AggregateRootアノテーションが付与されていません" }
        }
    }

    /** サービスアノテーションのテスト */
    @Nested
    inner class ServiceTest {
        @Test
        fun HelloHandlerはサービスアノテーションが付与されている() {
            val annotation = HelloHandler::class.java.getAnnotation(Service::class.java)
            assert(annotation != null) { "HelloHandlerに@Serviceアノテーションが付与されていません" }
        }
    }
}