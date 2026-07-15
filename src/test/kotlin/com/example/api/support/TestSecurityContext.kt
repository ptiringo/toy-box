package com.example.api.support

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

/**
 * `@WebMvcTest` slice テスト用の [SecurityContextHolder] セットアップ。
 *
 * slice テストは `@AutoConfigureMockMvc(addFilters = false)` で認証フィルタを無効化するが、`Actor` 引数を 取るハンドラでは
 * `ActorArgumentResolver` が動き、認証済み JWT の `sub` を要求する（未認証は設定漏れとして fail-loud で
 * 500）。本番と同じく「認証は済んでいる」状態を再現するため、ダミーの JWT 認証を差し込む。 MockMvc はリクエストを同一スレッドで同期実行し、フィルタ無効下では誰も
 * `SecurityContextHolder` を消さないため、 `@BeforeEach` で差した認証がハンドラ処理まで生きる。テスト間のリークを避けるため `@AfterEach` で
 * [clear] する。
 */
object TestSecurityContext {
    /** ダミーの JWT 認証を [SecurityContextHolder] に差す。`subject`（＝`sub`）は resolver が引く識別子。 */
    fun authenticate(subject: String = "test-subject") {
        val jwt =
            Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("sub", subject)
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    /** [SecurityContextHolder] を空にする（スレッド再利用によるリーク防止）。 */
    fun clear() {
        SecurityContextHolder.clearContext()
    }
}
