package com.example.api.domain.shared

import com.fasterxml.uuid.Generators
import java.util.UUID

/**
 * エンティティ識別子用の UUID を生成する。
 *
 * 生成方式はタイムベース（UUIDv7 相当の [Generators.timeBasedEpochRandomGenerator]）に統一する。生成値が時刻順にソート可能で、永続化時の
 * B-Tree インデックスの局所性に優れるため、ランダム（UUIDv4）ではなくこちらを標準とする。
 *
 * UUID 生成戦略をエンティティごとに書き分けると意図が読み取れなくなるため、全エンティティの ID 生成はこの関数に集約する。新しいエンティティの ID
 * 値クラスもこの関数を用いて生成すること。
 *
 * @return タイムベースで生成された [UUID]
 */
fun generateId(): UUID = Generators.timeBasedEpochRandomGenerator().generate()
