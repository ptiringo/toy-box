package com.example.api.controller.world.request

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 世界作成のリクエスト。
 *
 * VO の検証はドメイン層（`World.create`）が担うため、ここでは素の文字列で受ける（ADR-0026）。
 *
 * @property name 付けたい世界の名前
 */
@Schema(description = "世界作成リクエスト") data class CreateWorldRequest(val name: String)
