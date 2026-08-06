package com.example.api.controller.world.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 世界改名のリクエスト。
 *
 * @property name 新しい世界の名前
 */
@Schema(description = "世界改名リクエスト") data class RenameWorldRequest(val name: String)
