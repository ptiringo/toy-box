package com.example.api.domain.shared

import org.jmolecules.ddd.annotation.ValueObject

/**
 * 「誰が、どの世界で操作しているか」を運ぶパスポート（ADR-0067）。
 *
 * `can()` のような判断メソッドは持たない。この API の唯一の認可判断は「この世界はあなたのものか」で、それは [Actor]
 * が組めた時点で確定しているため、ユースケース側に判断は残らない。ユースケースが行うのは [worldId] をリポジトリと Queries
 * に渡すこと（＝データのスコープ）だけであって、認可の判断ではない。
 *
 * `Account` / `World` 集約そのものは `iam` コンテキストに居るが、この値は全コンテキストが読む横断的なもの なので共有カーネルに置く（[AccountId] /
 * [WorldId] と同じ理由）。
 *
 * @property accountId 操作しているアカウントのID
 * @property worldId 操作対象の世界のID
 */
@ValueObject data class Actor(val accountId: AccountId, val worldId: WorldId)
