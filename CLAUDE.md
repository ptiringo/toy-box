# CLAUDE.md

## プロジェクト概要

Kotlin Spring Boot (Spring MVC + Virtual Thread) を使用した API プロジェクトです。複数のドメインモデル（競馬、エンターテイメント、テニス）を探索する sandbox プロジェクトとして開発されています。

JDK 21 の Virtual Thread (`spring.threads.virtual.enabled=true`) を有効化することで、ブロッキング JDBC 等の同期 IO を素直に書きながらスレッド占有を避ける構成を採用しています。WebFlux / Reactor / coroutine ベースのリアクティブ流派ではありません。

## 開発コマンド

### mise によるツールバージョン管理

ツールバージョンは mise で管理する（`mise.toml`、ツール一覧と導入手順は後述「ツール管理」）。対話型シェル（`mise activate` 済み）と Claude Code セッション（`.claude/hooks/session-start-mise.sh` が `mise hook-env` を適用）では mise 管理ツールが PATH に通るため、コマンドを直接実行できる。

```bash
./gradlew build              # 通常（mise activate 済み / Claude Code セッション）
mise exec -- ./gradlew build # mise 未活性化の非対話シェルのみ
```

### ビルドとテスト

```bash
# ビルド
./gradlew build

# テスト実行
./gradlew test

# アプリケーション起動
./gradlew bootRun
```

### コード品質チェック

```bash
# ktfmt フォーマットチェック
./gradlew ktfmtCheck

# ktfmt 自動フォーマット
./gradlew ktfmtFormat

# detekt 静的解析（命名規則・コードスメル・複雑度等の検出）
./gradlew detekt

# 全チェック実行（ktfmtCheck + detekt + test 等を含む）
./gradlew check

# SQL（Flyway マイグレーション）の lint
mise exec -- sqlfluff lint src/main/resources/db/migration   # 書式・スタイル
mise exec -- sqlfluff fix src/main/resources/db/migration    # 自動整形
mise exec -- squawk src/main/resources/db/migration/*.sql    # マイグレーション安全性
```

ktfmt はフォーマッタ、detekt は静的解析ツールという役割分担です。detekt の設定は `config/detekt/detekt.yml` にあり、`buildUponDefaultConfig = true` でデフォルト設定に上書きする形で運用しています。雛形を再生成したい場合は `./gradlew detektGenerateConfig` を実行してください。レポートは `build/reports/detekt/` に HTML / SARIF / Checkstyle XML / Markdown 形式で出力されます。プロジェクト固有のカスタムルール（例: ドメイン / アプリケーション層で `throw` しない）は `:detekt-rules` モジュールで定義し、`detektPlugins` 経由で組み込んでいます（詳細は `.claude/rules/architecture.md`）。

### 単一テストの実行

```bash
# 特定のテストクラスを実行
./gradlew test --tests "HelloControllerTest"

# 特定のテストメソッドを実行（メソッド名に応じてパターンを調整してください）
./gradlew test --tests "HelloControllerTest.*hello*"
```

## アーキテクチャ

### 構成パターン

このプロジェクトは **Spring MVC の標準的な `@RestController` パターン** を採用しています：

- **Controller** (`controller/`): `@RestController` + `@GetMapping` 等で HTTP エンドポイントを定義
- **Domain** (`domain/`): フレームワークに依存しないピュアなドメインモデル

戻り値はオブジェクトをそのまま返却し、Jackson が JSON にシリアライズします。

例：
```kotlin
// HelloController.kt
@RestController
class HelloController {
    data class HelloResponse(val message: String)

    @GetMapping("/api/hello")
    fun hello(): HelloResponse = HelloResponse("Hello World")
}
```

リクエスト処理スレッドは Virtual Thread 上で走るため、内部でブロッキング IO（JDBC 等）を呼び出しても OS スレッドは占有されません。`suspend` / `Mono` / `Flux` を導入する動機が薄いので、原則 **同期コードで書く** ことを推奨します。リアクティブ流派を採らない判断の経緯は [ADR-0002](docs/adr/0002-virtual-thread-over-reactive.md) を参照。

### アーキテクチャテスト（ArchUnit）

アーキテクチャ規約は ArchUnit + jMolecules で機械的に強制されています（`src/test/kotlin/com/example/api/architecture/` 配下に関心ごとへ分割した `〜RulesTest` 群）。レイヤー依存方向（オニオン）、境界づけられたコンテキスト間の分離、DDD ビルディングブロックの整合性などが `./gradlew test` で検証されます。規約の詳細は `.claude/rules/architecture.md` を参照してください。

### ドメイン駆動設計

ドメインモデルには [jMolecules](https://github.com/xmolecules/jmolecules) のアノテーション（`org.jmolecules.ddd.annotation.*`）で DDD ビルディングブロックとしての役割を表明します。整合性は ArchUnit（`JMoleculesDddRules`）で検証されます。

#### Value Object パターン

型安全性のために `@JvmInline value class` を使用し、`@ValueObject` で役割を表明：

```kotlin
@ValueObject @JvmInline value class JockeyId(val value: UUID)

@ValueObject @JvmInline value class BloodHorseId(val value: UUID)
```

ゼロコスト抽象化により、異なるエンティティの ID を誤って混同することを防ぎます。他の集約への参照はこの ID 値クラスを介して行います（集約オブジェクトの直接参照は ArchUnit で違反として検出されます）。

#### Entity パターン

エンティティは UUID ベースの同一性を持ちます：

- **ID による等価性**: `equals()` と `hashCode()` は ID のみで実装
- **UUID 生成戦略**: タイムベース（UUIDv7 相当の `Generators.timeBasedEpochRandomGenerator()`、`java-uuid-generator` ライブラリ使用）に統一する。生成値が時刻順にソート可能で永続化時のインデックス局所性に優れるため、ランダム（`UUID.randomUUID()`）ではなくこちらを標準とする。生成ロジックは `domain.shared.generateId()` に集約しており、各 ID 値クラスは `JockeyId(generateId())` のようにこの関数を介して生成する（エンティティごとに生成方法を書き分けない）。選定理由（インデックス局所性）の詳細は [ADR-0005](docs/adr/0005-time-based-uuid-generation.md) を参照
- **役割の表明**: 集約ルートには `@AggregateRoot`、識別子プロパティには `@field:Identity` を付与（`@Identity` は FIELD ターゲットのため use-site target の明示が必要）
- **イミュータブル**: 集約はイミュータブルに保つ。プロパティは `val` のみとし `var` を持たせない。状態遷移は対象を書き換えず、同一性（ID）を引き継いだ新インスタンスを返すメソッドで表す（失敗しうるなら `Result<新インスタンス, エラー>`）。例: `BloodHorse.assignName()` は命名済みの新 `BloodHorse` を返す。`data class` は ID ベースの `final equals`/`hashCode` と衝突するため使わず、`private constructor` ＋ 手書き `copy` で写像する。詳細は [ADR-0009](docs/adr/0009-immutable-aggregates.md) を参照
- **生成は自己検証ファクトリに集約**: コンストラクタは `private` にし、生成は companion object の `public` ファクトリ経由に限る。**不変条件はファクトリが検証して `Result` を返す**。不変条件が集約内で完結する場合（例: `Jockey.create` の姓名ブランク）はもちろん、**集約をまたぐ前提条件でも、協力集約を引数で受け取ればその場で検証できる**ものはファクトリが自己検証する（例: `BloodHorse.create(sire, dam, entry, …)` が父=雄・母=雌・DNA・品種整合を検証）。検証を経ない構築経路は存在しないため、生成口を隠す（`internal` 化）必要はなく、封じ込めも行わない。前提条件を持たない生成（例: 父母不明の輸入馬 `BloodHorse.createImported`）は検証なしの `public` ファクトリでよい。テスト用 Object Mother（`〜Fixture`）は検証なしの `createImported` 等を使って任意の馬を組み立てる。この方針に至った経緯（当初は `internal` 封じ込めだった）は [ADR-0014](docs/adr/0014-self-validating-factory-over-confinement.md)（旧 [ADR-0010](docs/adr/0010-confine-aggregate-creation-to-domain-service.md) を Supersede）を参照

#### Command パターン

`Command<T>` はドメインコマンドのペイロードに**発生時刻メタデータを添える封筒**。ペイロード（何をしたいか）と、それがいつ発生したかという横断的メタデータを分離する。発生時刻はタイムゾーン非依存のドメインイベント時刻として `Instant` で保持する：

```kotlin
class Command<T>(
    val payload: T,
    val issuedAt: Instant,
)

// 使用例
fun registerInStudBook(command: Command<StudBook>)
```

#### Domain Event パターン

ドメインイベントは「**起きたこと**」を表すビルディングブロック（`Command` の「何をしたいか」と対）。`@org.jmolecules.event.annotation.DomainEvent` で役割を表明し、`domain.*.model` に置く。値としての等価性が自然なため `data class` を使ってよい（ID ベース `final equals` を持つ集約と異なり衝突しない）。他集約への参照は ID 値クラス経由。

イミュータブル集約（`var` 禁止）は内部にイベントを溜め込めないため、状態遷移メソッドが**遷移後の集約とイベントを `StateTransition<A, E>`（`domain.shared`）に同梱して返し**、発行は application 層が担う（集約は純粋なまま）。失敗しうる遷移は `Result<StateTransition<A, E>, エラー>` を返し、失敗時はイベントを生成しない：

```kotlin
@DomainEvent data class HorseNamed(val bloodHorseId: BloodHorseId, val name: HorseName)

// 状態遷移は遷移後の集約とイベントを同梱して返す
fun assignName(horseName: HorseName): Result<StateTransition<BloodHorse, HorseNamed>, HorseAlreadyNamed>
```

決定経緯と現状のスコープ（Spring `ApplicationEventPublisher` 連携・publish-after-commit・発生時刻 enrichment は別イシュー送り）は [ADR-0029](docs/adr/0029-domain-events-via-state-transition-return.md) を参照。

#### Query パターン（軽量 CQRS / L2）

読み取り系は書き込み集約を**経由しない**独立経路として実装する（軽量 CQRS = L2。ストア分離・結果整合・イベントソーシング = L3 は採らない）。リファレンス実装は `racing/jockey` の `FindJockeyUseCase`。

- **Read Model（View）とクエリポートは `application` に置く**（ドメインを汚さない）。View には jMolecules の `@QueryModel`（`org.jmolecules.architecture.cqrs`）を付け、不変条件のないフラットな `data class` とする
- **クエリポート（`〜Queries`）は plain interface**。書き込みポートの jMolecules `@Repository` は付けない。実装（infrastructure）は集約を組まずストアから直接 View へ詰める（例: `JdbcJockeyQueries` が `JdbcClient` で直 SELECT）
- クエリ入力 DTO は `〜Query` サフィックス。書き込み系の `Command<T>` 封筒は読み取りでは使わない

```kotlin
@QueryModel data class JockeyView(val id: UUID, val firstName: String, val lastName: String)

interface JockeyQueries { fun findById(id: JockeyId): JockeyView? }

@Service
class FindJockeyUseCase(private val jockeyQueries: JockeyQueries) {
    operator fun invoke(query: FindJockeyQuery): Result<JockeyView, JockeyNotFound> = ...
}
```

`@QueryModel` が `application` に居ることは ArchUnit で強制する（`.claude/rules/architecture.md`「読み取り経路（軽量 CQRS / L2）」）。決定経緯は [ADR-0031](docs/adr/0031-lightweight-cqrs-read-model.md) を参照。

### パッケージ構成

オニオンアーキテクチャの 4 リング（domainModel / domainService / applicationService / adapter）構成。`domain` 配下は各コンテキストを `model/` と `service/` に分割する。

```
com.example.api/
├── ApiApplication.kt    # エントリーポイント（@OpenAPIDefinition もここ）
├── controller/          # adapter (rest): @RestController（HTTP エンドポイント）
├── application/         # applicationService: ユースケース
│   ├── studbook/
│   └── racing/
├── domain/              # ドメイン（フレームワーク非依存）
│   ├── shared/          # 共有カーネル（Command / Entity 基底）。全コンテキストから参照可
│   ├── studbook/        # 軽種馬登録コンテキスト（JAIRS: 血統登録・繁殖登録）
│   │   ├── model/       #   domainModel: Entity / VO / Repository ポート
│   │   └── service/     #   domainService: トップレベル関数のドメインロジック
│   ├── racing/          # 競馬コンテキスト（JRA: 騎手・競走）
│   │   ├── model/
│   │   └── service/
│   ├── sakamichi/model/ # エンターテイメントコンテキスト
│   └── tennis/model/    # スポーツコンテキスト
└── infrastructure/      # adapter (persistence): ポートの具象実装（Spring 依存可）
    ├── studbook/
    └── racing/
```

**設計原則**（ArchUnit で強制。詳細は `.claude/rules/architecture.md`）:

- **domain.shared**: 共有カーネル。`Command` / `Entity` 基底 / `StateTransition`（状態遷移＋ドメインイベントの封筒）など、コンテキスト横断の最小限の基盤のみ
- **domain.\*.model**: Entity / Value Object / Repository ポート（interface）/ 集約内で完結するロジック。Repository ポートには jMolecules の `@Repository` を付与
- **domain.\*.service**: 複数の集約をまたぐドメインロジック。**Kotlin のトップレベル関数で書き**、`service/` への配置でドメインサービスと表現する（jMolecules `@Service` は付けない）。モデルにのみ依存でき、その逆は禁止
- **application**: ユースケース（コマンド DTO + ユースケースクラス + そのユースケースに紐づく失敗バリアント）。ドメインを組み合わせて業務シナリオを構築する。ユースケースを DI 経由で公開するための `@Service` / `@Component` のみ Spring 依存を許容し、ロジック本体は Plain Kotlin で書く
- **controller**: HTTP アダプター。`Result` から `ResponseEntity` への変換のみを担う
- **infrastructure**: Repository ポートの具体実装、外部システム連携などのアダプター。Spring 依存可

ユースケース関数の命名は `動詞 + リソース名` を基本とし、入力 DTO は `〜Command`（書き込み系）／ `〜Query`（読み取り系）サフィックスを付ける。

`domain` / `application` / `infrastructure` 配下のコンテキスト（`studbook` / `racing` / `sakamichi` / `tennis`）は境界づけられたコンテキストであり、コンテキスト間の依存は層やリングをまたぐ場合も含めて禁止（ArchUnit で強制）。`domain.shared` は対象外。

## コーディング規約

### 言語とスタイル

- **コメントとドキュメント**: 日本語で記述
- **変数名、関数名、クラス名**: 英語で記述（意味を明確に）
- **コミットメッセージ**: 日本語で記述、Conventional Commits 形式に準拠。最初に Conventional Commits ヘッダー（例: `feat: 新機能を追加`）を記述し、その後ファイルごとの詳細な変更内容を記述
- **PR のマージ方式**: 必ず **merge commit**（`gh pr merge --merge`）を使う。squash / rebase は使わない（個々のコミット履歴を main に残す方針）。CLI でマージする場合はセルフ PR の BLOCKED 表示回避のため `--admin` を付ける

### 命名規則

- **クラス名**: PascalCase（例: `UserService`, `OrderController`）
- **関数名**: camelCase（例: `createUser`, `validateInput`）
- **定数**: UPPER_SNAKE_CASE（例: `MAX_RETRY_COUNT`）
- **プロパティ**: camelCase（例: `userId`, `emailAddress`）

### テスト規約

テスト戦略（オニオン各リング × テストピラミッドの対応、どの層で何を・どうテストするか）とカバレッジハーネス（Kover の `total` / `mature` 2 variant 構成・成熟領域のみゲートするラチェット運用）は `.claude/rules/testing.md` を参照。本節は個別の記法（アノテーション・アサーション・命名）を定める。

#### アノテーション

- **JUnit 5 を使用**: `org.junit.jupiter.api.Test` アノテーションを使用
- **kotlin.test.Test は使用禁止**: マルチプラットフォーム対応が不要なため

#### アサーション

- **Kotlin の `assert` 関数を優先**: 単体テストでは Power Assert を活用
- **コントローラーの slice テスト**: `@WebMvcTest` + `MockMvcTester` を使用
- **アプリ全体の統合テスト**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient` + `RestTestClient` を使用（`RestTestClient` は Spring Framework 6.2 で追加された `RestClient` ベースの sync 版テストクライアント）
- **テストケース命名**: 日本語でテストの意図を明確に表現

例（slice テスト）：
```kotlin
@WebMvcTest(HelloController::class)
class HelloControllerTest(val mockMvc: MockMvc) {
    private val tester = MockMvcTester.create(mockMvc)

    @Test
    fun `helloエンドポイントがHello Worldを返すこと`() {
        tester.get().uri("/api/hello")
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.message")
            .isEqualTo("Hello World")
    }
}
```

### EditorConfig 準拠

- `end_of_line = lf`: Unix 形式の改行コード
- `insert_final_newline = true`: ファイル末尾に必ず改行
- `trim_trailing_whitespace = true`: 行末空白を削除（マークダウンを除く）
- `charset = utf-8`: UTF-8 エンコーディング

## Claude 指示ファイル・スキルの記述方針

`CLAUDE.md` / `.claude/rules/` / `.claude/skills/` など **Claude への指示ファイルはリポジトリ管理（クローンすれば誰でも同じ構成になる共有物）**である。したがって **環境依存の内容を書かず、ポータブルに保つ**。

- **書かない例**: 個人マシンの sandbox 設定・許可ホスト・絶対パス・`PATH`、特定セッションのメモリへの参照（`[[...]]`）、自分の環境でしか成り立たない前提手順。
- **環境依存の設定そのもの**は各自のローカル設定（`.claude/settings.local.json` 等）や各自のメモリに置き、共有ファイルからは出所をリンクで指すに留める。
- **手順は self-contained に**書く。「なぜ・何を」を本文で完結させ、特定環境固有の前提（「この環境では X が PATH に無い」等）は一般化した表現にする。

## 優先度管理

Issue の優先度は **GitHub Projects（`toy-box` = Project #4）の `Priority` single-select カスタムフィールド**で管理する。優先度ラベル（`P1〜P4`）は廃止済み（採否の経緯は [ADR-0011](docs/adr/0011-priority-via-projects-custom-field.md)）。

- **出所は Project の Priority フィールド 1 つ**。ラベルとの併用はしない（二重管理を避ける）。
- オプションは `P1: 今すぐ` / `P2: 近いうち` / `P3: いずれ` / `P4: 探索・保留` の 4 段階。
- **作成した Issue は必ず Project（#4）に追加する**。Project に入れた Issue だけが `Priority` フィールドを持てるため、issue を立てたら「Project へ追加 → `Priority` 設定」までを 1 セットで行う（優先度が即決でなくても Project には必ず入れ、未定なら後から設定する）。
- 優先度順に眺める・束ねる・絞るときは Project のビューを使う（`gh issue list` のラベル列には優先度は出ない）。
- **Issue の操作は `/issue-ops` スキルに集約**。次にやる Issue を選ぶ・候補を一覧する／新規作成して Project 追加＋優先度設定／優先度変更の手順（jq・操作 ID・実装済み確認）はそこを使う。とくに候補選びは、`gh project item-list` がクローズ済みも含み出力に open/closed が無いため priority だけで拾うとクローズ済み・実装済みを「未対応」として提案してしまう（再発実績あり）。`.status != "Done"` で絞る手順がスキルにある。
- フィールド定義と既存ラベルからの移行はスクリプト化してある（`scripts/migrate-priority-to-project.sh`、`gh project` CLI で再現可能）。Project スコープが要るので事前に `gh auth refresh -s project`。

## ツール管理

### mise

セットアップは `mise install`（`mise.toml` 指定の一式を導入）、確認は `mise list`。mise 未導入なら [mise インストール手順](https://mise.jdx.dev/getting-started.html) を参照。

現在管理されているツール（`mise.toml` 参照）：
- `actionlint`: GitHub Actions ワークフローの lint
- `editorconfig-checker`: EditorConfig 準拠チェック
- `fnox`: シークレット管理（暗号化保管／外部参照＋必要時だけ env へ展開）
- `gitleaks`: シークレット混入スキャン
- `java` (Temurin 21): Gradle / Kotlin のビルドおよびテスト実行用 JDK
- `lefthook`: Git フック管理
- `sqlfluff`: SQL 書式・スタイル lint および自動整形（dialect=postgres、mise の `pipx:` backend を uv 駆動で管理）
- `squawk`: Flyway マイグレーション SQL の安全性チェックと構文検証（libpg_query パース）
- `terraform`: インフラ構成管理
- `tfctl`: HCP Terraform / TFE 管理 CLI（run / variable / workspace 操作。レジストリ参照は MCP、操作は tfctl と棲み分け）。認証は `tfctl auth login`（tfctl 自身の資格情報ストア。`gh` CLI と同様に fnox は使わない）。採否と運用方針は [ADR-0034](docs/adr/0034-adopt-tfctl-cli.md) を参照
- `zizmor`: GitHub Actions ワークフローのセキュリティ監査

**Java バージョン管理について**: JDK のバージョン要件は `build.gradle.kts` の Gradle toolchain で宣言しています（`languageVersion = 21`）。実体の JDK は mise が提供し、Gradle の toolchain auto-detection が `JAVA_HOME` / `PATH` 経由で検出します。

### Lefthook

Git フックは **Lefthook** で管理（`lefthook.yml`）。セットアップは `lefthook install`。

- **pre-commit**（並列）: gitleaks、EditorConfig チェック、ktfmt チェック、detekt、actionlint、zizmor、Terraform fmt / validate、sqlfluff / squawk による SQL チェック
- **pre-push**: 全テスト
- **commit-msg**: Conventional Commits 形式チェック

```bash
lefthook run pre-commit                                # フック全体を手動実行
LEFTHOOK_EXCLUDE=ktfmt-check git commit -m "メッセージ"   # 特定コマンドをスキップ
```

## MCP サーバー設定

このリポジトリで必要とする MCP サーバーは、各自が `/plugin` 等でアドホックに導入するのではなく、**リポジトリ管理の設定ファイルに宣言**して共有する。クローンすれば誰でも同じ構成になり、再現性が保たれる（この方針の経緯は [ADR-0003](docs/adr/0003-consolidate-mcp-config-in-repo.md)）。

### Claude Code 用: `.mcp.json`（リポジトリ root）

Claude Code がプロジェクトスコープで読む設定ファイル。採用 MCP は以下：

| サーバー | 種別 | 認証 | 用途 |
|---------|------|------|------|
| `context7` | http | 不要 | ライブラリ・フレームワークの最新ドキュメント参照 |
| `terraform` | stdio (docker) | 不要 | Terraform レジストリ / プロバイダ情報の参照（`infra/` 用） |

#### GitHub 操作は MCP ではなく `gh` CLI で行う

issue / PR 操作には **GitHub MCP を使わず、`gh` CLI を直接使う**。サンドボックス下の TLS 問題（`OSStatus -26276`）は、`gh` を `.claude/settings.local.json` の `sandbox.excludedCommands` に登録して sandbox の外で実行することで回避する。

- `"gh"` と `"gh *"` の両方を登録する（マッチングは完全一致 + グロブのため）。複合コマンド（`A && gh ...`）はマッチしないので `gh` は単体コマンドで実行する。
- GitHub 操作は通常の `gh` コマンド（`gh pr ...` / `gh issue ...` 等）で完結し、env 補間も MCP 承認フローも不要。
- この方針に至った経緯（GitHub MCP を導入したが OAuth の DCR 非対応で撤去した理由、検討した代替案）は [ADR-0001](docs/adr/0001-drop-github-mcp-use-gh-cli.md) を参照。
- **プロジェクトスコープ `.mcp.json` は各開発者が初回に承認するフロー**になる。クローン後 Claude Code を起動すると未承認の MCP サーバーについて確認プロンプトが出るので、内容を確認のうえ承認する（承認状態は各自のローカル設定 `~/.claude.json` に記録され、リポジトリには載らない）。
- 個人のグローバル設定や `/plugin` 経由で同名サーバーを二重定義しないこと（このリポジトリでは `.mcp.json` を唯一の出所とする）。

### VS Code / Copilot 用: `.vscode/mcp.json`

VS Code（GitHub Copilot）が読む MCP 設定。**`.mcp.json` とは別ファイル・別フォーマット**（キーが `servers` か `mcpServers` か等が異なる）であり、Claude Code とは共有されない。

- 役割分担: **`.mcp.json` = Claude Code 用 / `.vscode/mcp.json` = VS Code・Copilot 用**。両者は別エディタ向けに併存させる。
- 両ファイルで共通利用する MCP（例: `context7`）は、片方を更新したらもう片方も合わせて**同期させる**（放置すると構成が乖離する）。

## シークレット管理（fnox + 1Password）

ローカル開発で必要なシークレットは、平文で shell profile に `export` せず、**1Password に保管＋必要時だけ env へ展開**する。仕組みは mise 管理の [fnox](https://fnox.jdx.dev/) で統一する。

- `fnox.toml` には `op://` 参照のみを書き（秘密情報を含まない）、**git にコミットしてよい**
- 値の解決は 1Password CLI (`op`) 経由。`op` がサインイン済み（デスクトップアプリ連携）なら**サービスアカウントトークンは不要**
- シークレットを必要とするプロセスには `fnox exec -- <command>` で起動時のみ env に展開する（永続化しない）
- **現状、定義しているシークレットはない**（`fnox.toml` の `[secrets]` は空）。本仕組みは将来のシークレット（GCP 認証情報など）に備えて維持する

案C（fnox + 1Password 参照のみ）を選んだ経緯は [ADR-0004](docs/adr/0004-secrets-fnox-1password.md)、運用手順・前提セットアップの詳細は **`.claude/rules/secrets.md`** を参照。

## OpenAPI/Swagger

このプロジェクトは `springdoc-openapi` を使用して API ドキュメントを自動生成します：

- **エンドポイント**: `/swagger-ui.html`（アプリケーション起動後）
- **OpenAPI JSON**: `/v3/api-docs`

コントローラーのメソッドには `@Operation`, `@ApiResponse`, `@Content` などのアノテーションを付与してドキュメント化します。`@OpenAPIDefinition`（タイトル・タグ等の全体定義）は `ApiApplication.kt` に付与しています。

## Spring Boot Actuator

ヘルスチェックは `/actuator/health` で公開（公開設定は `application.yml`、詳細表示は認可時のみ、動作確認は `HealthEndpointTest.kt`）。アプリ起動後に `curl http://localhost:8080/actuator/health` で `{"status":"UP"}` が返る。公開しているのはヘルスエンドポイントのみで、`info` / `metrics` 等は非公開。

## インフラストラクチャ（Terraform）

`infra/` ディレクトリに Terraform 構成を管理しています。HCP Terraform（旧 Terraform Cloud）をバックエンドとして使用。

### ディレクトリ構成

```
infra/
├── main.tf              # 共有リソースとモジュール呼び出し
├── providers.tf         # Google プロバイダー設定
├── terraform.tf         # Terraform / バックエンド設定
├── variables.tf         # ルート変数（wif_project_number など）
└── modules/
    ├── cicd/            # CI/CD パイプライン基盤
    │   ├── main.tf      # deployer SA、WIF バインディング、AR 書き込み権限
    │   ├── variables.tf
    │   └── outputs.tf
    └── cloudrun/        # Cloud Run デプロイ基盤
        ├── main.tf      # api-runner SA、run.developer 権限、actAs 権限
        ├── variables.tf
        └── outputs.tf
```

### モジュール

- **cicd**: GitHub Actions がイメージをビルド・プッシュするために必要なリソース群（deployer SA、WIF、AR 権限）
- **cloudrun**: Cloud Run へのデプロイと実行に必要なリソース群（api-runner SA、デプロイ権限）

### コマンド

```bash
# 初期化
terraform init

# 差分確認
terraform plan

# 適用
terraform apply

# フォーマット
terraform fmt -recursive
```

## 注意事項

- このプロジェクトは現在、永続化層（データベース、リポジトリ）を持ちません
- ドメインモデルは探索的な実装であり、TODO コメントが含まれています
- コード品質を重視しており、CI で ktfmt / detekt / EditorConfig のチェックが自動実行されます
