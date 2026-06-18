# toy-box

Kotlin + Spring Boot で複数のドメインモデル（競馬・エンターテイメント・テニス）を探索する sandbox プロジェクトです。オニオンアーキテクチャ / DDD / 各種品質ゲートを実際に組みながら、設計判断を ADR として残していくことを主眼にしています。

## 特徴

- **Spring MVC + Virtual Thread**: JDK 21 の Virtual Thread（`spring.threads.virtual.enabled=true`）を有効化し、ブロッキング IO を素直な同期コードで書きつつスレッド占有を避ける構成。WebFlux / Reactor / coroutine のリアクティブ流派は採らない（→ [ADR-0002](docs/adr/0002-virtual-thread-over-reactive.md)）。
- **オニオンアーキテクチャ（4 リング）**: domainModel / domainService / applicationService / adapter。依存の向きを ArchUnit で機械的に強制。
- **DDD（jMolecules）**: Entity / Value Object / Repository ポートなどの役割をアノテーションで表明し、整合性を ArchUnit で検証。
- **品質ゲート**: ktfmt（フォーマット）/ detekt（静的解析）/ Kover（カバレッジ・ラチェット）/ ArchUnit（アーキテクチャ規約）を CI と pre-commit で自動チェック。

## 技術スタック

| 領域 | 採用技術 |
|---|---|
| 言語 | Kotlin 2.3 |
| フレームワーク | Spring Boot 4.1（Spring MVC + Virtual Thread） |
| ビルド | Gradle（Kotlin DSL）/ JDK 21 |
| API ドキュメント | springdoc-openapi（Swagger UI） |
| DDD メタデータ | jMolecules |
| エラー表現 | kotlin-result（`Result<V, E>`） |
| ID 生成 | java-uuid-generator（UUIDv7 相当のタイムベース） |
| フォーマット / 静的解析 | ktfmt / detekt |
| カバレッジ | Kover |
| アーキテクチャテスト | ArchUnit + jMolecules ArchUnit |
| テスト | JUnit 5 / Power Assert / MockMvcTester / RestTestClient / mockk |
| ツールバージョン管理 | mise |
| Git フック | Lefthook |
| インフラ | Terraform（GCP / HCP Terraform） |

## Getting Started

### 前提

ツールバージョンは [mise](https://mise.jdx.dev/) で管理しています。mise を導入のうえ、以下で一式（JDK 21 ほか）をセットアップします。

```bash
mise install        # mise.toml 記載のツールを導入
lefthook install    # Git フックを有効化
```

mise を活性化済みのシェル（または Claude Code セッション）では Gradle を直接実行できます。詳細は CLAUDE.md「ツール管理」を参照。

### Dev Container（再現可能な開発環境）

ローカルへの個別セットアップの代わりに、[Dev Container](https://containers.dev/)（`.devcontainer/`）でも開発環境を立ち上げられます。**IntelliJ IDEA（JetBrains Gateway / Dev Containers 連携）を主シナリオ**とし、VS Code / GitHub Codespaces からも利用できます。

- JDK 21 + mise 管理ツール一式が揃った状態でコンテナが起動し、`./gradlew check` まで通ります。
- ツールバージョンの出所は引き続き `mise.toml`（devcontainer 側で二重管理しません）。コンテナ作成時に `mise install` / `lefthook install` が自動実行されます。
- Claude Code CLI を同梱しており、コンテナ内でもそのまま利用できます（mise 管理ツールは PATH に通ります）。

詳細・既知の制約（terraform MCP の Docker 依存、シークレット連携など）は [.devcontainer/README.md](.devcontainer/README.md) を参照。

### ビルド・テスト・起動

```bash
./gradlew build      # ビルド
./gradlew test       # テスト実行
./gradlew bootRun    # アプリ起動（http://localhost:8080）
./gradlew check      # ktfmt + detekt + test + カバレッジゲートを一括実行
```

起動後の主なエンドポイント:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- ヘルスチェック: `http://localhost:8080/actuator/health`

## アーキテクチャ概観

オニオンアーキテクチャの 4 リングで構成し、依存の矢印は常に外→内に向きます。

```
adapter (controller / infrastructure)
  └─ applicationService (application)
       └─ domainService (domain.*.service)
            └─ domainModel (domain.shared + domain.*.model)
```

- **domainModel**: Entity / Value Object / Repository ポート。フレームワーク非依存（Spring / jakarta / Jackson 禁止）。
- **domainService**: 複数集約をまたぐドメインロジック。Kotlin のトップレベル関数で記述。
- **applicationService**: ユースケース。`@Service` / `@Component` のみ Spring 依存を許容。
- **adapter**: `controller`（HTTP）と `infrastructure`（ポート実装）。

`domain` / `application` / `infrastructure` 直下のコンテキスト（`horseracing` / `sakamichi` / `tennis`）は境界づけられたコンテキストで、コンテキスト間の依存は禁止。これらレイヤー依存・コンテキスト分離・DDD ビルディングブロックの整合性は ArchUnit で強制され、`./gradlew test` で検証されます。詳細は [.claude/rules/architecture.md](.claude/rules/architecture.md) を参照。

## ドキュメント索引

- **[CLAUDE.md](CLAUDE.md)**: プロジェクト全体の開発ガイド（コマンド・アーキテクチャ・規約・ツール構成）。最も網羅的な一次資料。
- **`.claude/rules/`**: 領域別の詳細規約
  - [architecture.md](.claude/rules/architecture.md) — オニオン 4 リング / ArchUnit 規約
  - [testing.md](.claude/rules/testing.md) — テスト戦略 / Kover カバレッジ運用
  - [secrets.md](.claude/rules/secrets.md) — シークレット管理（mise + fnox + 1Password）
- **[docs/adr/](docs/adr/)**: アーキテクチャ意思決定記録（ADR）。「なぜそうしたか」の経緯。
  - [0002 — Virtual Thread over Reactive](docs/adr/0002-virtual-thread-over-reactive.md)
  - [0005 — Time-based UUID generation](docs/adr/0005-time-based-uuid-generation.md)
  - [0006 — Kover over JaCoCo](docs/adr/0006-kover-over-jacoco.md) ほか

## コードスタイルと品質チェック

フォーマットは **ktfmt**（Kotlin 公式コーディング規約 `kotlinlang-style`、4 space indent / 100 char limit）、静的解析は **detekt**（`config/detekt/detekt.yml` でルール調整）で担当します。

```bash
./gradlew ktfmtCheck    # フォーマット違反チェック
./gradlew ktfmtFormat   # 自動フォーマット
./gradlew detekt        # 静的解析（レポート: build/reports/detekt/）
```

これらは **Lefthook** の pre-commit フック（ktfmt / detekt / EditorConfig / gitleaks ほか）と CI（Pull Request 時）で自動実行され、違反があれば失敗します。特定フックのスキップは `LEFTHOOK_EXCLUDE=ktfmt-check git commit -m "メッセージ"` のように指定します。
