package com.example.api.controller.jockey

import com.example.api.application.racing.jockey.JockeyView
import com.example.api.domain.racing.model.jockey.Jockey
import java.util.UUID

/**
 * ジョッキーリソースの表現（HTTP 契約）。
 *
 * ジョッキーリソースに対する操作（登録の Create など）は、[AIP-133](https://google.aip.dev/133) /
 * [AIP-136](https://google.aip.dev/136) に倣い一律でこのリソース表現全体を返す（ADR-0008）。
 *
 * @property id ジョッキーの生 UUID
 * @property firstName 名
 * @property lastName 姓
 */
data class JockeyResponse(val id: UUID, val firstName: String, val lastName: String)

/** [Jockey] をジョッキーリソースの表現へ変換する。各操作の成功レスポンスはこのリソース表現を一律で返す。 */
fun Jockey.toResponse(): JockeyResponse =
    JockeyResponse(id = id.value, firstName = firstName, lastName = lastName)

/**
 * 読み取りビュー [JockeyView] をジョッキーリソースの表現へ変換する。
 *
 * 軽量 CQRS（L2）の読み取り経路（Get）も、書き込み経路（Create）と**同一の単一リソース表現**を返す （ADR-0008）。読みモデルと wire
 * 表現の双方が平坦な同型のため写像は素直。
 */
fun JockeyView.toResponse(): JockeyResponse =
    JockeyResponse(id = id, firstName = firstName, lastName = lastName)
