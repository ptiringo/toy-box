package com.example.api.controller

import com.example.api.controller.jockey.request.RegisterJockeyRequest
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

/**
 * [RequestFingerprint] のユニットテスト。
 *
 * 単一の協力者（[tools.jackson.databind.ObjectMapper]）を直接構築して渡すだけなので、Spring コンテキストは要らない （プロジェクトは distinct
 * なコンテキスト構成を増やさない方針。testing.md）。
 */
class RequestFingerprintTest {
    private val fingerprint = RequestFingerprint(JsonMapper())

    @Test
    fun `同じ内容なら別インスタンスでも同じ指紋になること（決定性）`() {
        val first = RegisterJockeyRequest(firstName = "武", lastName = "豊")
        val second = RegisterJockeyRequest(firstName = "武", lastName = "豊")

        assert(fingerprint.of(first) == fingerprint.of(second))
    }

    @Test
    fun `フィールドの値が異なれば指紋も異なること（感度）`() {
        val original = RegisterJockeyRequest(firstName = "武", lastName = "豊")
        val changed = RegisterJockeyRequest(firstName = "武", lastName = "彰")

        assert(fingerprint.of(original) != fingerprint.of(changed))
    }

    @Test
    fun `フィールドの値を入れ替えただけでも指紋が異なること（感度・区切りの検出）`() {
        val original = RegisterJockeyRequest(firstName = "武", lastName = "豊")
        val swapped = RegisterJockeyRequest(firstName = "豊", lastName = "武")

        assert(fingerprint.of(original) != fingerprint.of(swapped))
    }

    @Test
    fun `指紋は 64 文字の小文字 16 進数であること（形式）`() {
        val request = RegisterJockeyRequest(firstName = "武", lastName = "豊")

        assert(fingerprint.of(request).matches(Regex("^[0-9a-f]{64}$")))
    }
}
