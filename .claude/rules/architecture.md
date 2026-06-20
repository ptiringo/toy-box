# アーキテクチャ規約

本プロジェクトはオニオンアーキテクチャ（ドメインモデル / ドメインサービス / アプリケーションサービス / アダプターの 4 リング）を採用する。規約は ArchUnit（`src/test/kotlin/com/example/api/architecture/ArchitectureTest.kt`）で機械的に強制されており、違反すると `./gradlew test` が失敗する。**新しいコードを書くときは以下の規約に従うこと。規約を変えたい場合はテストと本ファイルを同時に更新する。**

## レイヤー依存ルール（オニオン 4 リング）

内側ほど安定し、依存の矢印は常に外→内に向く。`onionArchitecture()` で強制している。

```
                  ┌─────────────── adapter ───────────────┐
                  │  controller / infrastructure          │
                  │   ┌────── applicationService ──────┐  │
                  │   │   ┌─── domainService ───┐       │  │
                  │   │   │  ┌ domainModel ┐    │       │  │
                  │   │   │  │ shared+model │   │       │  │
                  │   │   │  └──────────────┘   │       │  │
                  │   │   └─────────────────────┘       │  │
                  │   └─────────────────────────────────┘  │
                  └────────────────────────────────────────┘
```

| リング | パッケージ | 依存してよい先 | Spring 依存 |
|-------|-----------|--------------|------------|
| domainModel | `domain.shared` + `domain.*.model` | 純粋ライブラリのみ（kotlin-result / java-uuid-generator / jMolecules） | 禁止（jakarta / Jackson も禁止） |
| domainService | `domain.*.service` | domainModel | 禁止 |
| applicationService | `application` | domainModel / domainService | `org.springframework.stereotype`（`@Service` / `@Component`）のみ |
| adapter (rest) | `controller` | 内側すべて | 可 |
| adapter (persistence) | `infrastructure` | 内側すべて | 可 |

- **ドメインサービスはドメインモデルにのみ依存でき、その逆（モデル→サービス）は禁止**
- アダプター同士（`controller` ⇔ `infrastructure`）の参照は禁止
- `@RestController` は `controller`、`@Service` は `application`、Spring の `@Repository`（ポート実装）は `infrastructure` に置く

### ドメインモデルとドメインサービスの分け方

各コンテキストの配下を `model/` と `service/` に分割する（パッケージ構造を参照）。

- **model**: Entity / Value Object / Repository ポート（interface）/ 集約内で完結するロジック
- **service**: 複数の集約やモデルをまたぐドメインロジック。**Kotlin のトップレベル関数で書く**（`object` でラップしない）。jMolecules の `@Service` は付けない（パッケージ配置で表現し、`service/` に居ることがドメインサービスの証）
- 1 ファイルにモデルとサービスを混在させない（例: `confirmRaceResult` は `service/race/` に、入力 VO の `RaceResult` は `model/race/` に置く）

## パッケージ構造

```
domain/
├── shared/                      # 共有カーネル（Command / Entity 基底）。全コンテキストから参照可
├── horseracing/
│   ├── model/                   # ドメインモデルリング
│   │   ├── jockey/              #   Jockey, JockeyId, JockeyRepository
│   │   ├── race/                #   Race, RaceResult, ...
│   │   ├── breeding/
│   │   └── horse/...
│   └── service/                 # ドメインサービスリング
│       ├── race/                #   confirmRaceResult
│       └── horse/               #   registerInStudBook
├── sakamichi/model/
└── tennis/model/
```

## 境界づけられたコンテキストの分離

`application` / `domain` / `infrastructure` 各層の直下のパッケージ名（`horseracing` / `sakamichi` / `tennis`）を境界づけられたコンテキストとみなし、**コンテキスト間の依存は層やリングをまたぐ場合も含めて一切禁止**する（例: `application.horseracing` → `domain.tennis.model` は違反）。`model` / `service` のサブ階層はコンテキスト名の判定に影響しない。

- `domain.shared` は共有カーネルであり、コンテキスト分離の対象外（どのコンテキストからも参照可）
- 新しいコンテキストを追加する場合、`<context>/model/`（必要なら `service/`）を切るだけで自動的に分離ルールの対象になる

## DDD ビルディングブロック（jMolecules）

ドメインモデルには [jMolecules](https://github.com/xmolecules/jmolecules) のアノテーション（`org.jmolecules.ddd.annotation.*`）で役割を表明する。アノテーションはメタデータのみでランタイム挙動を持たないため domain 層に置いてよい。

| 対象 | アノテーション | 例 |
|------|--------------|-----|
| 集約ルート | `@AggregateRoot` | `Jockey`, `Race` |
| 集約内エンティティ | `@Entity` | （現状なし） |
| 値オブジェクト（ID 値クラス含む） | `@ValueObject` | `JockeyId` |
| 識別子プロパティ | `@field:Identity` | `Jockey.id` |
| Repository ポート（interface） | `@Repository`（jMolecules 版） | `JockeyRepository` |

ドメインサービス（`service/` のトップレベル関数）には jMolecules アノテーションを付けない。`@Service`（jMolecules）は型向けでトップレベル関数に付けられず、ドメインサービスであることは `service/` パッケージへの配置で表現する。

注意点:

- `@Identity` は FIELD / METHOD ターゲットのため、Kotlin プロパティには **`@field:Identity`** と use-site target を明示する
- jMolecules アノテーション付きクラスはドメインモデルリング（`domain.*.model`）にのみ置ける（ArchUnit で強制）
- `JMoleculesDddRules.all()` により以下が強制される:
  - `@Entity` / `@AggregateRoot` は `@Identity` 付き識別子を持つ
  - **他の集約への参照は ID 値クラス（または `Association`）経由のみ**。集約オブジェクトを直接フィールドに持ってはならない（例: `Stallion` は `BloodHorse` ではなく `BloodHorseId` を持つ）
  - `@ValueObject` は Entity / AggregateRoot を参照しない

## その他の強制ルール

- 標準出力・標準エラーへの直接書き込み禁止（ロガーを使う）
- フィールドインジェクション禁止（コンストラクタインジェクションを使う）
- `UUID.randomUUID()` の直接呼び出し禁止。ID は `domain.shared.generateId()`（UUIDv7 相当のタイムベース生成）経由で生成する（永続化時のインデックス局所性のため。[ADR-0005](../../docs/adr/0005-time-based-uuid-generation.md)）。main コードのみ対象（テストの fixture は対象外）
- **集約（`@AggregateRoot` / `@Entity`）はイミュータブル**（`val` のみ・`var` 禁止）。状態遷移は対象を書き換えず、同一性（ID）を引き継いだ新インスタンスを返すメソッドで表す（[ADR-0009](../../docs/adr/0009-immutable-aggregates.md)）。`val` は final フィールド・`var` は非 final フィールドへコンパイルされるため、集約クラスが直接宣言するフィールドが全て final であることを ArchUnit で検証して `var` を排除する
- **ドメインサービス（`domain.*.service`）はトップレベル関数で書く**（`object` / `class` でラップしない）。トップレベル関数はファイルごとのファサードクラス（`〜Kt`）へコンパイルされるため、service パッケージ内のクラスが `Kt` で終わることを ArchUnit で検証して `object` / `class` 宣言を排除する。ただしサービスの戻り値（`Result<_, 〜Error>`）の失敗側を表す失敗バリアント型（`〜Error`）はサービスと同居させてよく、対象から除外する
- **`@RestController` のハンドラは成功レスポンスで `ResponseEntity` を返さない**。成功は `@ResponseStatus` ＋戻り値で resource を返す（[error-handling.md](error-handling.md) / [api-design.md](api-design.md)）。エラー描画 funnel の `GlobalExceptionHandler` は `@RestController` ではない（`ResponseEntityExceptionHandler` 継承）ため誤検出されない

## ルールの変更・追加

- アーキテクチャ違反でテストが落ちた場合、**原則コードをアーキテクチャに合わせる**。ルール側を変えるのは設計判断の変更時のみ
- 依存ライブラリ: `jmolecules-bom`（バージョン管理）+ `jmolecules-ddd`（main）、`archunit-junit5` + `jmolecules-archunit`（test）。いずれも version catalog（`gradle/libs.versions.toml`）で管理
