package com.example.api.application.iam.world

import java.util.UUID
import org.jmolecules.architecture.cqrs.QueryModel

/**
 * 世界の読み取り専用ビュー（Read Model）。軽量 CQRS（L2）の読み取り側（ADR-0031）。
 *
 * 所有者のアカウントIDは含めない。一覧は常に「自分の世界」に絞って返すため、呼び出し側が既に知っている。
 *
 * @property id 世界の生 UUID
 * @property name 世界の名前
 */
@QueryModel data class WorldView(val id: UUID, val name: String)
