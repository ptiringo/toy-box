package com.example.api.domain.shared

/**
 * 集約の状態遷移の結果。遷移後の集約と、その遷移で「起きたこと」を表すドメインイベントを同梱する封筒。
 *
 * イミュータブル集約（[ADR-0009]）は自身の内部にイベントバッファをミュータブルに溜め込めない（`val` のみ・ 状態遷移は新インスタンス返却）。Spring/jMolecules
 * 定番の `AbstractAggregateRoot.registerEvent()` 方式は 集約を mutable
 * にする前提のため噛み合わない。そこで状態遷移メソッドは遷移後の集約とイベントをまとめて本型で返し、 イベントの発行（ログ・パブリッシュ等）は application
 * 層に委ねる。これにより集約は純粋なまま保たれる。 収集・発行方式の決定経緯は ADR-0029 を参照。
 *
 * 失敗しうる遷移は `Result<StateTransition<A, E>, エラー>` を返し、失敗時はイベントを生成しない （例:
 * `BloodHorse.assignName`）。発生時刻（[Command] の `issuedAt` に相当する横断メタデータ）や イベント ID
 * の付与は当面持たせない（最小スコープ。enrichment と Spring `ApplicationEventPublisher` への 接続は別イシュー送り）。
 *
 * @param A 遷移後の集約の型
 * @param E 生成されるドメインイベントの型（`@org.jmolecules.event.annotation.DomainEvent` を付与した型）
 * @property aggregate 遷移後の集約
 * @property event その遷移で起きたことを表すドメインイベント
 */
class StateTransition<out A, out E>(val aggregate: A, val event: E)
