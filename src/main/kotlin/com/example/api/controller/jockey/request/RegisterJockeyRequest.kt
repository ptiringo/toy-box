package com.example.api.controller.jockey.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * `POST /api/worlds/{worldId}/jockeys` のリクエストボディ。
 *
 * @property firstName 名
 * @property lastName 姓
 */
@Schema(description = "ジョッキー登録リクエスト")
data class RegisterJockeyRequest(val firstName: String, val lastName: String)
