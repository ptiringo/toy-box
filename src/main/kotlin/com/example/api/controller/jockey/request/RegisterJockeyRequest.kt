package com.example.api.controller.jockey.request

/**
 * `POST /api/jockeys` のリクエストボディ。
 *
 * @property firstName 名
 * @property lastName 姓
 */
data class RegisterJockeyRequest(val firstName: String, val lastName: String)
