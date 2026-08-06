package com.example.api.controller.world

import com.example.api.application.iam.world.WorldView
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * 世界リソースの単一表現。全操作（List / Create / Update）で共用する（ADR-0008）。
 *
 * @property id 世界の生 UUID
 * @property name 世界の名前
 */
@Schema(description = "プレイヤーごとの世界（セーブデータ）") data class WorldResponse(val id: UUID, val name: String)

/** 読み取りビューを HTTP のリソース表現へ写す。 */
fun WorldView.toResponse(): WorldResponse = WorldResponse(id, name)
