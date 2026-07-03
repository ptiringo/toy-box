# 0047. 楽観ロックの version は集約が保持し save を一本化する

- Status: Accepted
- Date: 2026-07-03
- Deciders: Matsui

## Context（背景・課題）

[ADR-0027](0027-persistence-spring-data-jdbc.md) は、永続化メタデータ（`@Version` 列）をドメイン集約へ漏らさない方針を採った。集約に直接 `@Version` を持たせるとオニオン規約（`domain..` は `org.springframework..` 依存禁止）に反するため、Spring Data アノテーションは infrastructure 層の `〜Row` に閉じ込め、集約とは手書きマッパーで相互変換する構成にした。その帰結として、アダプタの `save` は Row の version を常に `null` で組み立てることになり、Spring Data JDBC は常に **insert 判定**になる。ADR-0027 はこれを「更新系（version を進める save）はドメイン経由では未対応」という積み残しとして明記していた（#424）。

一方 [ADR-0041](0041-immutable-data-model-as-modeling-discipline.md) は、UPDATE してよいのは**リソース／ロングタームイベントの親行のみ**で、イベント（`HorseInspection` の審査等）は INSERT-only と整理した。つまり update 経路を実装すべき対象は限定されており、全集約に一律で入れる必要はない。

Task 1〜6（#424）の実装時点で、状態遷移を持つ 3 集約（`BloodHorse` の馬名登録、`BreedingRegistration` の廃用、`BreedingResult` の分娩結果記録）は「findById → 状態遷移 → save」で永続化を試みると、実 DB では既存行に対して insert が走り PK 重複で失敗する状態だった（application 層テストは mockk でポートを差すため未検出）。更新と、競合時の `OptimisticLockingFailureException` の `Result` 写像をドメイン経由で扱えるようにする必要があった。

## Decision（決定）

**集約自身が楽観ロックの version を保持し、リポジトリポートは `save` 一本に統合する。** insert / update の判別は Spring Data JDBC の version 判定に委ね、ポートを `findById` / `save` の 2 メソッドに保つ（`update` を別メソッドとして増設しない）。

### 集約側: `Entity.version`

共有カーネルの `Entity<ID>` 基底クラスに `version` プロパティを追加する。

```kotlin
abstract class Entity<ID : Any> {
    abstract val id: ID

    /**
     * 楽観ロックの version（永続化メタデータ）。null は「まだ永続化されていない」ことを表す。
     *
     * 永続化層（Spring Data JDBC）が insert / update の判別と競合検出に用いる。ドメインロジックは
     * この値で業務判断（分岐・比較）をしないこと。永続化される更新対象の集約だけが constructor
     * プロパティで override し、それ以外は既定の null のままでよい。
     */
    open val version: Long? = null

    // equals / hashCode は ID のみで判定（既存のまま）
}
```

`version` は既定で `null`（`open val` の初期値）。更新語彙（状態遷移メソッド）を持つ集約（`BloodHorse` / `BreedingRegistration` / `BreedingResult`）だけが constructor プロパティで override する。更新語彙を持たない `Jockey` や INSERT-only の `HorseInspection`（ADR-0041）は override せず、常に `null`（insert のみ）のままでよい。

生成経路ごとの `version` の扱いは以下で統一する。

- **`create`（新規・自己検証ファクトリ）**: `version` を渡さず既定の `null`。新規集約はまだ永続化されていない。
- **`reconstitute`（永続化層からの復元）**: 引数 `version: Long?` を取り、Row の DB 値をそのまま渡す。検証・採番は行わない（ADR-0027 の再構成口の方針を踏襲）。
- **状態遷移の `copy`**: 呼び出し元から明示された属性だけを差し替え、`version` は自身の値をそのまま引き継ぐ（読み取り時点の version を保ったまま save まで運ぶ）。

例（`BreedingResult`。実装は `src/main/kotlin/com/example/api/domain/studbook/model/breeding/BreedingResult.kt`）:

```kotlin
@AggregateRoot
class BreedingResult
private constructor(
    @field:Identity override val id: BreedingResultId,
    val breedingRegistrationId: BreedingRegistrationId,
    val breedingYear: Year,
    val covering: Covering?,
    val outcome: FoalingOutcome?,
    override val version: Long? = null,
) : Entity<BreedingResultId>() {

    /** [id] と未指定の属性を引き継ぎ、指定された属性だけを差し替えた新しい [BreedingResult] を返す。 */
    private fun copy(outcome: FoalingOutcome? = this.outcome): BreedingResult =
        BreedingResult(id, breedingRegistrationId, breedingYear, covering, outcome, version = version)

    companion object {
        fun create(/* ... */): Result<BreedingResult, RecordCoveringError> =
            /* ... */ BreedingResult(id = BreedingResultId(generateId()), /* ... */ /* version は既定 null */)

        /** @param version DB の version 列の値（楽観ロック） */
        fun reconstitute(
            id: BreedingResultId,
            /* ... */
            version: Long?,
        ): BreedingResult = BreedingResult(id, /* ... */ version)
    }
}
```

### ポート側: `findById → T?` / `save(t): Result<T, UpdateConflict>`

リポジトリポートは読み取り・書き込みとも素の集約型を扱う。`findById` は単純 lookup として `T?` を返し（[error-handling.md](../../.claude/rules/error-handling.md) の方針どおり `Result` を強制しない）、書き込みは `save` 一本にする。`update` を別メソッドとして増設しない。

```kotlin
@Repository
interface BreedingResultRepository {
    fun findById(id: BreedingResultId): BreedingResult?

    /**
     * 繁殖成績を永続化する。
     *
     * 集約の [BreedingResult.version] が null なら insert、非 null なら楽観ロック付き update になる
     * （Spring Data JDBC の version 判別）。update が読み取り時点から他の更新と競合していた
     * （または行が並行削除されていた）場合は [UpdateConflict] を返す。
     */
    fun save(breedingResult: BreedingResult): Result<BreedingResult, UpdateConflict>
}
```

`UpdateConflict` は `data object`（`domain.shared.UpdateConflict`）として維持する。

```kotlin
/**
 * save の update 経路が読み取り時点から他の更新と競合した（または対象行が並行削除されていた）ことを表す。
 *
 * リポジトリポートの `save` の失敗側（[Entity.version] が非 null で Spring Data JDBC が update と
 * 判定した場合）。ユースケースは自分のエラー型（`〜UseCaseError` の `ConcurrentModification`
 * バリアント等）へ wrap し、Controller は 409 Conflict に描画する。
 */
data object UpdateConflict
```

### アダプタ側: `toRow` が集約の version をそのまま写し、例外を `Result` へ写像する

infrastructure 層のマッパー（`toRow`）は集約が保持する `version` をそのまま Row へ写す。`null` なら Spring Data JDBC が新規と判定して insert、非 `null` なら楽観ロック付き update になる。アダプタは `OptimisticLockingFailureException` を捕捉して `Err(UpdateConflict)` に写像する（infrastructure 層は例外の catch を許容する境界。[error-handling.md](../../.claude/rules/error-handling.md)）。

例（`JdbcBreedingResultRepository`。実装は `src/main/kotlin/com/example/api/infrastructure/studbook/breeding/JdbcBreedingResultRepository.kt`）:

```kotlin
@Repository
class JdbcBreedingResultRepository(private val rows: BreedingResultSpringDataRepository) :
    BreedingResultRepository {

    override fun findById(id: BreedingResultId): BreedingResult? =
        rows.findById(id.value).map { it.toDomain() }.orElse(null)

    override fun save(breedingResult: BreedingResult): Result<BreedingResult, UpdateConflict> =
        try {
            Ok(rows.save(breedingResult.toRow()).toDomain())
        } catch (_: OptimisticLockingFailureException) {
            // version 不一致（並行更新）または行の並行削除。どちらも「読み取り時点から競合した」として扱う
            Err(UpdateConflict)
        }

    /**
     * ドメイン集約を永続化モデルへ写す。
     *
     * version は集約が保持する値をそのまま写す（null なら Spring Data JDBC が新規と判定して insert、
     * 非 null なら楽観ロック付き update。ADR-0027 の落とし穴②③）。
     */
    private fun BreedingResult.toRow(): BreedingResultRow =
        BreedingResultRow(id = id.value, /* ... */ version = version)
}
```

更新語彙を持たない `Jockey`（`JdbcJockeyRepository`）と INSERT-only の `HorseInspection`（`JdbcHorseInspectionRepository`、ADR-0041）は、集約側が `version` を override しないため常に `null` を写し、insert のみを扱う。将来これらに更新の語彙（状態遷移メソッド）が生まれた時点で、集約に `version` を override して同じ save 一本方式に乗せる。

### ユースケース側: 更新系は `mapError`、新規系は `getOrElse`

更新系ユースケース（`ReportFoalingUseCase` / `NameHorseUseCase` / 廃用系）は `findById → 状態遷移 → save` の順に書き、`save` の失敗（`UpdateConflict`）を自身のエラー型の新バリアント（例: `NameHorseUseCaseError.ConcurrentModification(bloodHorseId)`）へ `mapError` で写像する。Controller 境界はこのバリアントを **HTTP 409 Conflict**（`error_code: concurrent-modification`）の `ProblemDetail` に描画する（既存の `toProblemDetail()` パターンに追加するのみで、エラー描画 funnel の構成自体は変えない）。

```kotlin
val named =
    bloodHorseRepository
        .save(transition.aggregate)
        .mapError { NameHorseUseCaseError.ConcurrentModification(input.bloodHorseId) }
        .bind()
```

新規系ユースケース（`RegisterInStudBookUseCase` / `RegisterImportedHorseUseCase` / `RegisterFoalUseCase` / `RegisterBreedingRegistrationUseCase` / `RecordCoveringUseCase` / `RecordUncoveredUseCase`）は、生成直後の集約（`version` は常に `null`）を保存するため insert しか起こらず、`UpdateConflict` は理論上発生しない。この経路は `getOrElse` でプログラミングエラー相当として扱う（`error(...)` で明示的に失敗させる）。

```kotlin
bloodHorseRepository.save(bloodHorse).getOrElse {
    error("新規登録直後の保存で楽観ロック競合は起こり得ない: $it")
}
```

対象は**状態遷移を持つ集約のみ**（ADR-0041 の R/E 分類に従う）。

| 集約 | 分類 | version override | 理由 |
|---|---|---|---|
| BloodHorse | リソース | 対象 | `assignName`（馬名登録）の状態遷移がある |
| BreedingRegistration | リソース | 対象 | `retire`（廃用）の状態遷移がある |
| BreedingResult | ロングタームイベント | 対象 | `recordFoaling` で親行（現在ステータス）を UPDATE する |
| Jockey | リソース | 対象外 | 状態遷移メソッドが無く、version を override しても dead code になる（YAGNI） |
| HorseInspection | イベント | 対象外 | INSERT-only（ADR-0041）。version を持たない |

### ADR-0027 との関係

ADR-0027 の「永続化メタデータ（version）をドメインへ漏らさない」sub-decision は、本 ADR が**部分的に上書き**する。集約は `Entity.version` を通じて永続化メタデータを保持するが、`open val ... = null` かつ「業務判断に使わない」制約を維持することで、ドメインロジック自体は version に依存しない（純粋性は保つ）。ADR-0027 が確立した他の方針（別 Row + 手書きマッパー、`reconstitute` による復元専用ファクトリ、value class ID のマッピング方式）はそのまま有効。

## 検討した代替案

### ① Versioned 封筒（本 PR 中で一度フル実装 → 撤回）

永続化済み集約と楽観ロック version を運ぶ汎用の封筒 `Versioned<T>` を `domain.shared` に導入し、リポジトリポートの語彙とする案。集約そのものは version を持たないまま、`findById(id): Versioned<T>?` が読み取り時点の version を同梱し、新設の `update(versioned: Versioned<T>): Result<Versioned<T>, UpdateConflict>` まで運ぶ（`save` は新規 insert 専用のまま維持）。

```kotlin
data class Versioned<out T>(val value: T, val version: Long) {
    fun <R> map(f: (T) -> R): Versioned<R> = Versioned(f(value), version)
}
```

本 PR（#424）の前半コミットで対象 3 集約に実際にフル実装した（`findById → Versioned<T>?`・`update(versioned): Result<Versioned<T>, UpdateConflict>`・アダプタの `toRow(version = ...)` 引数化・ユースケースの `.value` 剥がし・`ConcurrentModification` の `mapError` 配線まで一通り通った）。実装後にレビューした結果、**`save`（新規）と `update`（更新）がポートで分かれる人間工学**に抵抗があるとして撤回した。呼び出し側は対象の永続化状態（新規か既存か）に応じてどちらを呼ぶか判断しなければならず、`findById` の戻り値型が `T?` から `Versioned<T>?` に変わることで読み取り専用の呼び出し箇所（父・母の存在確認など）まで封筒を受け取る認知コストも生じていた。

「読み取り時点の version を保って更新まで運ぶ」という封筒方式の利点自体は、**集約が version を保持する現方式でも同等に得られる**（`reconstitute` で読み取り時点の version を集約に積み、状態遷移の `copy` がそれを引き継ぐ）。ポートを `save` 一本に保ったまま同じ効果を得られるため、封筒という追加の語彙を持ち込む必要はないと判断した。

### ② 保存時に version を再引き当てする

`save` 直前に既存行の version を SELECT して詰める案。ポートの API 変更が不要だが、`findById` で読んだ後に他者がコミットした変更を検出できず lost update になる。楽観ロックが本来検出すべき「読んだ時点からの競合」を果たせないため不採用。

### ③ アダプタ内 identity map

`findById` 時に `id → version` を `ThreadLocal` 等へ隠し持ち、`save` 時に引き当てる案。ポートの API は不変だが、隠れた可変状態と「同一リクエスト内で読んで保存する」という暗黙の前提を持ち込む。イミュータブル集約 + `Result` で明示性を貫くこのコードベースの流儀に合わないため不採用。

## Consequences（結果・影響）

- **得られるもの**: `findById → 状態遷移 → save` という定型で 3 集約すべてを同じ形に揃えられ、読み取り専用の呼び出し側は素の `T?` のまま扱えて封筒の認知コストを負わない。ポートは `findById` / `save` の 2 メソッドのまま増設せず、insert / update の判別は Spring Data JDBC に委ねきる。競合は例外ではなく `Result` の失敗値として業務エラーの延長線上で扱え、HTTP 409 への写像も既存の `toProblemDetail()` パターンに一貫して乗る。
- **引き受けるトレードオフ**: 集約が永続化メタデータ（`version`）を持つため、ADR-0027 が掲げた「ドメインは永続化を知らない」という純度は部分的に譲る（ただし業務判断には使わない制約で実害は抑える）。新規系ユースケースは「insert しか起こらないはずの save」でも型上は `Result<T, UpdateConflict>` を受け取るため、`getOrElse { error(...) }` という定型のボイラープレートが個々のユースケースに散る。対象外の Jockey / HorseInspection は今後語彙が増えたときに version の override を追加することになる。
- **既存 ADR との関係**: [ADR-0027](0027-persistence-spring-data-jdbc.md) の積み残し（「更新系はドメイン経由では未対応」）を解消し、「永続化メタデータをドメインへ漏らさない」sub-decision を部分的に上書きする（上記「ADR-0027 との関係」）。対象範囲の絞り込みは [ADR-0041](0041-immutable-data-model-as-modeling-discipline.md) の R/E 分類（リソース／ロングタームイベントのみ UPDATE、イベントは INSERT-only）に従う。
- **スコープ外**: `retire` の API 配線（別イシュー）、複数集約書き込みのトランザクション境界、ドメインイベント発行基盤、Jockey / HorseInspection への version 追加は本決定の対象外。
