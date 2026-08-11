package com.example.api.controller

import java.security.MessageDigest
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * リクエスト DTO から「同じリクエストか」を判定するための指紋を計算する（ADR-0072 / #750）。
 *
 * 冪等キーが別内容のリクエストで使い回されたことを検出するために使う。算出をアダプタ層に置くのは、 直列化フォーマット（DTO をどうバイト列へ落とすか）が転送レイヤの関心であり、ドメイン /
 * アプリケーション層の 業務ロジックには属さないためであって、機械強制のゲートがあるからではない。
 *
 * 直列化には Spring Boot が構成した [ObjectMapper] をそのまま使う。同じ DTO からは同じバイト列が出るので 指紋は決定的になる。
 *
 * 本プロジェクトは Spring Boot 4.1 の既定である Jackson 3 系（`tools.jackson.databind`）を採用している。
 * `com.fasterxml.jackson.databind.ObjectMapper`（Jackson 2 系）は `jackson-module-kotlin` 経由でクラスパスに
 * 乗ってはいるが、この Spring Boot バージョンではその型の Bean が定義されない（実測で `@SpringBootTest` 全体が
 * `NoSuchBeanDefinitionException` で落ちることを確認済み）。実際に構成されメッセージコンバータへ渡っているのは Jackson 3 系の
 * [ObjectMapper] のため、そちらへ注入先を合わせる。
 */
@Component
class RequestFingerprint(private val objectMapper: ObjectMapper) {

    /** [request] の SHA-256 を hex 64 文字で返す。 */
    fun of(request: Any): String =
        MessageDigest.getInstance("SHA-256")
            .digest(objectMapper.writeValueAsBytes(request))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
