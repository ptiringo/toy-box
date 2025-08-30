package com.example.api.domain.tennis

import org.junit.jupiter.api.Test

/**
 * Player クラスのユニットテスト
 */
class PlayerTest {
    @Test
    fun 同じIDのPlayer同士は等価判定でtrueとなる() {
        val id = PlayerId(1L)
        val playerA = Player(id)
        val playerB = Player(id)
        assert(playerA == playerB)
    }

    @Test
    fun 異なるIDのPlayer同士は等価判定でfalseとなる() {
        val playerA = Player(PlayerId(1L))
        val playerB = Player(PlayerId(2L))
        assert(playerA != playerB)
    }

    @Test
    fun Player自身との比較は常にtrueとなる() {
        val player = Player(PlayerId(3L))
        assert(player == player)
    }

    @Suppress("SENSELESS_COMPARISON")
    @Test
    fun Playerとnullの比較はfalseとなる() {
        val player = Player(PlayerId(4L))
        assert(player != null)
    }

    @Test
    fun 同じIDのPlayer同士はhashCodeも一致する() {
        val id = PlayerId(6L)
        val playerA = Player(id)
        val playerB = Player(id)
        assert(playerA.hashCode() == playerB.hashCode())
    }

    @Test
    fun 異なるIDのPlayer同士はhashCodeが異なる() {
        val playerA = Player(PlayerId(7L))
        val playerB = Player(PlayerId(8L))
        assert(playerA.hashCode() != playerB.hashCode())
    }
}

