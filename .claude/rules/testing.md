# テスト戦略

オニオンアーキテクチャの 4 リングに、テストピラミッドをそのまま写像する。**内側ほど数多く・速く・隔離して、外側ほど少数・統合寄りに**テストする。リングごとに手法が決まっているので、新しいコードはそのリングの流儀に従う。

## リング × テスト手法

| リング | パッケージ | テスト手法 | 参考実装 |
|-------|-----------|-----------|---------|
| domainModel | `domain.*.model` / `domain.shared` | 純粋ユニット（DI なし・Power Assert）。VO の不変条件は `create()` の `Result` を検証 | `MicrochipNumberTest` / `JockeyTest` / `EntityTest` |
| domainService | `domain.*.service` | 純粋ユニット。集約はテスト用 Fixture で組む（モック不要） | `RegisterInStudBookTest` |
| applicationService | `application` | ユニット。Repository ポートを mockk でスタブし、ユースケースの分岐と失敗バリアントを検証 | `RegisterInStudBookUseCaseTest` / `JockeyRegistrationUseCaseTest` |
| adapter (rest) | `controller` | `@WebMvcTest` + `MockMvcTester` の slice テスト。HTTP 入出力と ProblemDetail 描画を検証 | `BloodHorseControllerTest` / `GlobalExceptionHandlerTest` |
| 横断 | — | ArchUnit（規約）／ OpenAPI 契約／ `@SpringBootTest` 統合（最小限） | `ArchitectureTest` / `OpenApiTest` / `HealthEndpointTest` |

方針:

- **モックは applicationService の Repository ポート境界に限る**。ドメイン層は Fixture で実物を組み、モックしない（純粋関数なので隔離コスト不要）。
- ドメインサービスのテスト用 Fixture は対象コンテキストの `model` パッケージ配下にテストコードとして置く（例: `BloodHorseFixture`）。
- 統合テスト（`@SpringBootTest`）は配線確認の最小限に留める。ロジックの網羅は内側のリングで済ませる（ピラミッドの底を厚く）。
- テスト規約（JUnit 5 / Power Assert / `@WebMvcTest` / 日本語ケース名）の詳細は CLAUDE.md「テスト規約」を参照。

## カバレッジハーネス（Kover）

カバレッジは [Kover](https://github.com/Kotlin/kotlinx-kover) で計測する（JaCoCo ではない。理由は [ADR-0006](../../docs/adr/0006-kover-over-jacoco.md)）。設定は `build.gradle.kts` の `kover {}` ブロック。

### 2 つのレポート variant

| variant | 目的 | 対象 | タスク |
|---------|------|------|-------|
| `total` | **穴の可視化**。探索領域も含めた全体像を見せる | 全 main コード（エントリーポイント除く） | `koverHtmlReport` / `koverXmlReport` / `koverLog` |
| `mature` | **リグレッション防止ゲート**。成熟領域だけを検証 | 下記「ゲート対象」のみ | `koverVerifyMature` / `koverLogMature` |

Kover 0.9 の検証ルールはパッケージ単位のフィルタを持てないため、`copyVariant` で `total` を複製した `mature` variant に includes フィルタを掛けてゲート対象を絞っている。

### ゲートの考え方（ラチェット）

- **成熟パッケージのみゲート**: レイヤーごとのテストが揃った領域だけに行カバレッジ下限を課す。探索段階のモデル（`tennis` / `sakamichi` / `breeding` / `race` / `racehorse` / `stallion` 等）は `total` レポートには出すが、ゲートからは外して CI をノイズで赤くしない。
- **現状の下限は行 85%**（実測 88.3% を少し下げてロック）。これは**ラチェット**であり、カバレッジが上がったら下限も引き上げて後戻りを防ぐ。下げるのは設計判断を伴うときだけ。
- ゲート対象に新コードを足すなら、テストも添えて 85% を割らないこと。割ると `./gradlew check`（および CI の `koverVerifyMature`）が失敗する。

ゲート対象パッケージ（`build.gradle.kts` の `variant("mature")` の includes が唯一の出所。ここは要約）:
`domain.shared` / `domain.horseracing.model.jockey` / `model.horse.bloodhorse` / `service.horse` / `application.horseracing` / `controller`。

### 実行

```bash
./gradlew koverHtmlReport      # build/reports/kover/html/index.html で穴を目視（total）
./gradlew koverVerifyMature    # 成熟ゲートの検証（check にも組み込み済み）
./gradlew check                # ktfmt + detekt + test + koverVerifyMature を一括実行
```

CI（`api-tests.yml`）は test 後に `koverVerifyMature` でゲートを掛け、`koverLog` / `koverLogMature` の数値を PR の Job Summary に出す（外部サービス不使用）。

## 当面の宿題（カバレッジの穴）

`total` レポートで 0% に見える領域は、成熟させるときにテストを添える。優先度は実装の成熟度に従う:

- `infrastructure.*`（InMemory リポジトリ）: ポート実装の契約テストが未整備
- `domain.horseracing.service`（`ConfirmRaceResult` / `RegisterForBreeding`）: サービスだがテスト無し
- `domain.horseracing.model`（`breeding` / `race` / `racehorse` / `stallion`）・`sakamichi` / `tennis`: 探索段階のモデル

これらは成熟してゲート対象に昇格する時点で `variant("mature")` の includes に追加する。
