package com.example.api.domain.shared

import java.util.UUID
import org.jmolecules.ddd.annotation.ValueObject

/**
 * アカウントID。この API の利用者を指す。
 *
 * `Account` 集約そのものは `iam` コンテキストに居るが、ID は全コンテキストが読む横断的な値なので共有カーネルに置く。
 * コンテキスト間依存が全面禁止されている以上、置ける先はここしかない。
 *
 * @property value 外部採番の UUIDv7
 */
@ValueObject @JvmInline value class AccountId(val value: UUID)

/**
 * 世界ID。プレイヤーごとの箱庭（セーブデータ）＝テナントの識別子。
 *
 * この API の唯一の認可判断は「この世界はあなたのものか」であり、以降のデータ操作はすべてこの ID による スコープになる。[AccountId] と同じ理由で共有カーネルに置く。
 *
 * @property value 外部採番の UUIDv7
 */
@ValueObject @JvmInline value class WorldId(val value: UUID)
