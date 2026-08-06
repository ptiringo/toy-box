package com.example.api.controller.me

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * 利用者自身のリソース表現。
 *
 * @property accountId この API が採番したアカウントID
 */
@Schema(description = "この API における利用者自身") data class MeResponse(val accountId: UUID)
