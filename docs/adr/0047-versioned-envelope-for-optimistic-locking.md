# 0047. 楽観ロックの version はドメイン封筒 Versioned でポート越しに運ぶ

- Status: Accepted
- Date: 2026-07-03
- Deciders: Matsui

## Context（背景・課題）

[ADR-0027](0027-persistence-spring-data-jdbc.md) は、永続化メタデータ（`@Version` 列）をドメイン集約へ漏らさない方針を採った。集約に直接 `@Version` を持たせるとオニオン規約（`domain..` は `org.springframework..` 依存禁止）に反するため、Spring Data アノテーションは infrastructure 層の `〜Row` に閉じ込め、集約とは手書きマッパーで相互変換する構成にした。その帰結として、アダプタの `save` は Row の version を常に `null` で組み立てることになり、Spring Data JDBC は常に **insert 判定**になる。ADR-0027 はこれを「更新系（version を進める save）はドメイン経由では未対応」という積み残しとして明記していた（#424）。

一方 [ADR-0041](0041-immutable-data-model-as-modeling-discipline.md) は、UPDATE してよいのは**リソース／ロングタームイベントの親行のみ**で、イベント（`HorseInspection` の審査等）は INSERT-only と整理した。つまり update 経路を実装すべき対象は限定されており、全集約に一律で入れる必要はない。

Task 1〜6（#424）の実装時点で、状態遷移を持つ 3 集約（`BloodHorse` の馬名登録、`BreedingRegistration` の廃用、`BreedingResult` の分娩結果記録）は「findById → 状態遷移 → save」で永続化を試みると、実 DB では既存行に対して insert が走り PK 重複で失敗する状態だった（application 層テストは mockk でポートを差すため未検出）。update と、競合時の `OptimisticLockingFailureException` の `Result` 写像をドメイン経由で扱えるようにする必要があった。

### 検討した代替案

- **保存時に version を再引き当てする**（`save` 直前に既存行の version を SELECT して詰める）: ポートの API 変更が不要だが、`findById` で読んだ後に他者がコミットした変更を検出できず lost update になる。楽観ロックが本来検出すべき「読んだ時点からの競合」を果たせないため不採用。
- **アダプタ内 identity map**（`findById` 時に `id → version` を `ThreadLocal` 等へ隠し持ち、`save` 時に引き当てる）: ポートの API は不変だが、隠れた可変状態と「同一リクエスト内で読んで保存する」という暗黙の前提を持ち込む。イミュータブル集約 + `Result` で明示性を貫くこのコードベースの流儀に合わないため不採用。
- **集約に version を持たせる**: ADR-0027 の「永続化メタデータをドメインへ漏らさない」方針に正面から反するため不採用。

## Decision（決定）

**永続化済み集約と楽観ロック version を運ぶ汎用の封筒 `Versioned<T>` を `domain.shared` に導入し、リポジトリポートの語彙とする。** 集約そのものは version を持たないまま、`findById` の戻り値がこの封筒で読み取り時点の version を同梱し、`update` まで運ぶ。

```kotlin
/** 永続化済み集約と楽観ロック version の封筒。[StateTransition] と同型の汎用キャリア。jMolecules アノテーションは付けない。 */
data class Versioned<out T>(val value: T, val version: Long) {
    fun <R> map(f: (T) -> R): Versioned<R> = Versioned(f(value), version)
}

/** update が読み取り時点から他の更新と競合した（または対象行が並行削除されていた）ことを表す。 */
data object UpdateConflict
```

対象 3 集約（`BloodHorse` / `BreedingRegistration` / `BreedingResult`）のリポジトリポートに以下を導入する。

- `findById(id): Versioned<T>?` — 読むだけの呼び出し側は `.value` で剥がす。複数 ID をまとめて読む `findAllById`（親馬参照の存在確認など、更新に使わない読み取り専用の場面）は素の集約 `Map` のままとし `Versioned` を持たせない。
- `save(aggregate: T): T` — **新規 insert 専用**として維持する。
- `update(versioned: Versioned<T>): Result<Versioned<T>, UpdateConflict>` — 新設。読み取り時点の `versioned.version` を使って `WHERE version = ?` 相当の楽観ロック更新を行い、version を進めた新しい封筒を返す。

アダプタ（infrastructure 層）は `toRow()` に `version: Long? = null` 引数を追加し、insert 経路はデフォルト `null` のまま、`update` 実装では読み取り時点の version を明示的に渡す。`update` は `OptimisticLockingFailureException`（version 不一致、または行の並行削除）を捕捉し `Err(UpdateConflict)` に写像する（infrastructure 層は例外の catch を許容する境界）。

```kotlin
override fun update(versioned: Versioned<BloodHorse>): Result<Versioned<BloodHorse>, UpdateConflict> =
    try {
        Ok(rows.save(versioned.value.toRow(version = versioned.version)).toVersioned())
    } catch (_: OptimisticLockingFailureException) {
        Err(UpdateConflict)
    }
```

ユースケース（`ReportFoalingUseCase` / `NameHorseUseCase`）は `findById → .value で状態遷移 → update` の順に書き換え、`UpdateConflict` を自身のエラー型の新バリアント（例: `NameHorseUseCaseError.ConcurrentModification(bloodHorseId)`）へ `mapError` で写像する。Controller 境界はこのバリアントを **HTTP 409 Conflict**（`error_code: concurrent-modification`）の `ProblemDetail` に描画する（既存の `toProblemDetail()` パターンに追加するのみで、エラー描画 funnel の構成自体は変えない）。

**対象は状態遷移を持つ集約のみに限定する**（ADR-0041 の R/E 分類に従う）。

| 集約 | 分類 | update 対象 | 理由 |
|---|---|---|---|
| BloodHorse | リソース | 対象 | `assignName`（馬名登録）の状態遷移がある |
| BreedingRegistration | リソース | 対象 | `retire`（廃用）の状態遷移がある |
| BreedingResult | ロングタームイベント | 対象 | `recordFoaling` で親行（現在ステータス）を UPDATE する |
| Jockey | リソース | 対象外 | 状態遷移メソッドが無く、update ポートを追加しても dead code になる（YAGNI） |
| HorseInspection | イベント | 対象外 | INSERT-only（ADR-0041）。update 経路自体を持たない |

Jockey / HorseInspection に将来更新の語彙（状態遷移メソッド）が生まれた時点で、同じ `Versioned` 方式で `update` を追加する。

## Consequences（結果・影響）

- **得られるもの**: ドメイン集約は引き続き永続化メタデータ（version）を持たず ADR-0027 の方針と両立したまま、楽観ロックが「読んだ時点からの競合検出」という本来の意味で機能する。`findById → 状態遷移 → update` という定型で 3 集約すべてを同じ形に揃えられ、`StateTransition<A, E>` と対になる「封筒」語彙が `domain.shared` に増えるだけでドメインモデル自体への影響はない。競合は例外ではなく `Result` の失敗値として業務エラーの延長線上で扱え、HTTP 409 への写像も既存の `toProblemDetail()` パターンに一貫して乗る。
- **引き受けるトレードオフ**: `findById` の戻り値型が `T?` から `Versioned<T>?` に変わるため、呼び出し側（読み取り専用の利用箇所を含む）は `.value` を明示的に剥がす必要がある。`update` を呼ぶ経路とただ読むだけの経路が型上は区別されないため、更新意図のない呼び出しでも封筒を受け取る（実害はないが認知コストはある）。対象外の Jockey / HorseInspection は今後語彙が増えたときに同じ変更を再度行うことになる。
- **既存 ADR との関係**: [ADR-0027](0027-persistence-spring-data-jdbc.md) の積み残し（「更新系はドメイン経由では未対応」）を解消する。対象範囲の絞り込みは [ADR-0041](0041-immutable-data-model-as-modeling-discipline.md) の R/E 分類（リソース／ロングタームイベントのみ UPDATE、イベントは INSERT-only）に従う。
- **スコープ外**: `retire` の API 配線（別イシュー）、複数集約書き込みのトランザクション境界、ドメインイベント発行基盤、Jockey / HorseInspection への update 追加は本決定の対象外。
