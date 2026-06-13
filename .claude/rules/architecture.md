# アーキテクチャ規約

本プロジェクトはポート&アダプター（軽量オニオンアーキテクチャ）を採用する。規約は ArchUnit（`src/test/kotlin/com/example/api/architecture/ArchitectureTest.kt`）で機械的に強制されており、違反すると `./gradlew test` が失敗する。**新しいコードを書くときは以下の規約に従うこと。規約を変えたい場合はテストと本ファイルを同時に更新する。**

## レイヤー依存ルール（オニオン）

```
controller ──┬──→ application ──→ domain ←── infrastructure
             └──────────────────→ domain
```

| レイヤー | 依存してよい先 | Spring 依存 |
|---------|--------------|------------|
| `domain` | 純粋ライブラリのみ（kotlin-result / java-uuid-generator / jMolecules） | 禁止（jakarta / Jackson も禁止） |
| `application` | `domain` | `org.springframework.stereotype`（`@Service` / `@Component`）のみ |
| `controller` | `application`, `domain` | 可 |
| `infrastructure` | `domain` | 可 |

- アダプター同士（`controller` ⇔ `infrastructure`）の参照は禁止
- `@RestController` は `controller`、`@Service` は `application`、Spring の `@Repository`（ポート実装）は `infrastructure` に置く

## 境界づけられたコンテキストの分離

`application` / `domain` / `infrastructure` 直下のパッケージ名（`horseracing` / `sakamichi` / `tennis`）を境界づけられたコンテキストとみなし、**コンテキスト間の依存は層をまたぐ場合も含めて一切禁止**する（例: `application.horseracing` → `domain.tennis` は違反）。

- `domain` 直下（`Command` / `Entity`）は共有カーネルであり、どのコンテキストからも参照可
- 新しいコンテキストを追加する場合、パッケージを切るだけで自動的に分離ルールの対象になる

## DDD ビルディングブロック（jMolecules）

ドメインモデルには [jMolecules](https://github.com/xmolecules/jmolecules) のアノテーション（`org.jmolecules.ddd.annotation.*`）で役割を表明する。アノテーションはメタデータのみでランタイム挙動を持たないため domain 層に置いてよい。

| 対象 | アノテーション | 例 |
|------|--------------|-----|
| 集約ルート | `@AggregateRoot` | `Jockey`, `Race` |
| 集約内エンティティ | `@Entity` | （現状なし） |
| 値オブジェクト（ID 値クラス含む） | `@ValueObject` | `JockeyId` |
| 識別子プロパティ | `@field:Identity` | `Jockey.id` |
| Repository ポート（interface） | `@Repository`（jMolecules 版） | `JockeyRepository` |

注意点:

- `@Identity` は FIELD / METHOD ターゲットのため、Kotlin プロパティには **`@field:Identity`** と use-site target を明示する
- jMolecules アノテーション付きクラスは domain 層にのみ置ける（ArchUnit で強制）
- `JMoleculesDddRules.all()` により以下が強制される:
  - `@Entity` / `@AggregateRoot` は `@Identity` 付き識別子を持つ
  - **他の集約への参照は ID 値クラス（または `Association`）経由のみ**。集約オブジェクトを直接フィールドに持ってはならない（例: `Stallion` は `BloodHorse` ではなく `BloodHorseId` を持つ）
  - `@ValueObject` は Entity / AggregateRoot を参照しない

## その他の強制ルール

- 標準出力・標準エラーへの直接書き込み禁止（ロガーを使う）
- フィールドインジェクション禁止（コンストラクタインジェクションを使う）

## ルールの変更・追加

- アーキテクチャ違反でテストが落ちた場合、**原則コードをアーキテクチャに合わせる**。ルール側を変えるのは設計判断の変更時のみ
- 依存ライブラリ: `jmolecules-bom`（バージョン管理）+ `jmolecules-ddd`（main）、`archunit-junit5` + `jmolecules-archunit`（test）。いずれも version catalog（`gradle/libs.versions.toml`）で管理
