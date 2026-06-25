# Flyway マイグレーション SQL のチェック体制 設計書

- 日付: 2026-06-26
- ステータス: 承認済み（実装プラン作成へ）

## 背景・目的

永続化層の導入（ADR-0027 / ADR-0030）に伴い、`src/main/resources/db/migration/` 配下に
Flyway マイグレーション用の SQL ファイルが増えてきた。現状チェックは Kotlin（ktfmt / detekt）・
Terraform（tflint）・GitHub Actions（actionlint / zizmor）には揃っているが、**SQL には何の機械的チェックもない**。

マイグレーション SQL は H2（PostgreSQL 互換モード）と本番想定の PostgreSQL の双方に適用される
（ランタイムは H2、契約テストは Testcontainers の PostgreSQL）。本番運用に乗る前段として、
他のファイル種別と同じ水準のチェック体制を SQL にも整える。

満たしたいチェック目的（ユーザー確認済み、3つとも対象）:

1. **書式・スタイル統一** — インデント・キーワードの大文字小文字・カンマ位置等を機械的に強制
2. **マイグレーション安全性** — 本番で危険な DDL（テーブル全体ロック、既存テーブルへの NOT NULL 列追加、型変更等）を検出
3. **構文・妥当性チェック** — SQL が PostgreSQL で実際にパースできるか

## 方針

1ツールで3目的すべてはカバーできないため、役割で2ツールに分ける（A案 = squawk + sqlfluff）。
選定経緯と却下案（B案: 軽量フォーマッタ / C案: squawk のみ）は別途 ADR-0032 に記録する。

| ツール | 役割 | 導入手段 |
|---|---|---|
| **squawk** | マイグレーション安全性（危険な DDL 検出）＋ libpg_query による実パース（構文妥当性の一部を副次的にカバー） | mise `github:sbdchd/squawk`（単一バイナリ。`ubi` は deprecated のため GitHub バックエンドを使う） |
| **sqlfluff** | 書式・スタイル統一（lint + 自動整形 `fix`）、dialect=postgres | mise `pipx:sqlfluff`（Python） |

- 対象パス: `src/main/resources/db/migration/**/*.sql`
- dialect は両対応の土台である **postgres** に固定する
- 構文妥当性は既存の Testcontainers 契約テストで Flyway が実 PostgreSQL に適用することでも担保されており、
  squawk のパースはそれを前倒しで検出する補助という位置づけ

## コンポーネント

### 1. mise.toml（ツール追加）

`[tools]` に以下を追加する（バージョンは実装時に最新安定版へ pin。`lockfile = true` 運用に従い `mise.lock` も更新）。

- `"github:sbdchd/squawk"` — GitHub リリースのプリビルドバイナリ（`ubi` バックエンドは deprecated のため GitHub バックエンドを使う。複数バイナリ＋`.vsix` 混在で autodetect が曖昧なら `[matching=squawk-]` 等のフィルタを付ける）
- `"pipx:sqlfluff"` — Python パッケージ
- `python`（Temurin の java と同様に mise 管理）— pipx バックエンドが要する Python ランタイムを
  system Python に依存させず再現性を確保するため

**実装時の検証必須事項**: `mise install` で squawk / sqlfluff / python が実際に解決・導入でき、
`mise exec -- squawk --version` / `mise exec -- sqlfluff --version` が通ること。
pipx バックエンドの Python 解決方式（mise 管理 python を使うか uv 経由か）は実機で確認し、
動かない場合は導入手段を調整する（このリスクは実装の最初のステップで潰す）。

### 2. 設定ファイル（リポジトリ root）

- **`.sqlfluff`**:
  - `dialect = postgres`
  - `templater = raw`（Flyway は素の SQL。Jinja テンプレート解釈を無効化し `$` 等の誤解釈を防ぐ）
  - ルールは最小限から開始（行長・キーワード大文字小文字・末尾空白など）。厳しすぎて既存 SQL が
    大量に落ちる場合は段階導入とし、過剰なルールは除外する
- **squawk**: まず既定ルールで運用。除外が必要になれば `squawk.toml` を後付けする
  （初期の `CREATE TABLE`（新規テーブル作成）は危険 DDL ルールに概ね該当せず通る想定）

### 3. lefthook.yml（pre-commit に追加、既存コマンドのパターン踏襲）

```yaml
# sqlfluff による SQL の書式・スタイルチェック
sqlfluff:
  glob: "**/*.sql"
  run: mise exec -- sqlfluff lint {staged_files}
  tags: format

# squawk によるマイグレーション安全性チェック
squawk:
  glob: "**/*.sql"
  run: mise exec -- squawk {staged_files}
  tags: lint
```

- 既存の gitleaks / actionlint / tflint と同様、mise 管理ツールの解決を確実にするため `mise exec --` 経由で呼ぶ
- 自動整形は `mise exec -- sqlfluff fix <files>`（ktfmtFormat 相当）として手動運用。pre-commit では `lint`（チェックのみ）

### 4. CI: `.github/workflows/sql-check.yml`（新規）

`terraform-check.yml` を雛形にする。

- トリガ: `pull_request` / `push`（branches: main）で `paths: ['src/main/resources/db/migration/**']`、加えて `workflow_dispatch`
- `permissions: contents: read`、`persist-credentials: false`、各 action は既存ワークフローと同じ pin（commit SHA）に揃える
- ステップ: checkout → `jdx/mise-action` で squawk + sqlfluff を導入 → バージョン確認 →
  `sqlfluff lint <migration dir>` → `squawk <migration files>`

### 5. ドキュメント

- **ADR-0032**（新規、/adr スキルで起こす）: SQL lint ツール選定。
  - 決定: squawk + sqlfluff の2本立て、dialect=postgres
  - 理由: 3チェック目的を1ツールで満たせず役割分担が必要。sqlfluff は SQL lint のデファクトでルールが豊富。
    Python 依存は pipx でツール内に閉じ込め、プロジェクトコードには波及しない
  - 却下案: B案（squawk + 軽量フォーマッタ）はルールベース lint が弱い / C案（squawk のみ）は書式統一を満たせない
- **CLAUDE.md**: 「コード品質チェック」「ツール管理（mise）」「Lefthook」節に SQL チェックを追記
- 必要に応じて `.editorconfig` に SQL 用のインデント設定を追加（sqlfluff 側設定と矛盾しない範囲で）

## テスト・検証（受け入れ基準）

1. `mise install` 後、squawk / sqlfluff が `mise exec --` で起動する（バージョン表示）
2. 既存 `V1__create_jockey.sql` に対し squawk・sqlfluff が **両方とも通る**
   （通らなければ設定調整 or ファイル整形のいずれかで解消する）
3. **空振り防止（ミューテーション）**:
   - キーワードを小文字に崩した SQL で sqlfluff が違反を検出する
   - 既存テーブルへ `NOT NULL` 列を追加する等の危険 DDL で squawk が違反を検出する
4. lefthook pre-commit で SQL を含むコミット時に両チェックが走る（`lefthook run pre-commit`）
5. CI ワークフローが migration パス変更時にトリガされ、ローカルと同じチェックを実行する
6. `actionlint` / `zizmor` が新規ワークフロー `sql-check.yml` を通す

## スコープ外（YAGNI）

- squawk の高度なルールカスタマイズ（`squawk.toml`）— 必要になってから
- マイグレーション以外の SQL（現状存在しない）への適用
- sqlfluff の autofix を pre-commit で自動適用する運用（まずは lint のみ、整形は手動）
- 結果整合・複数 dialect 同時 lint 等の発展

## 関連

- ADR-0005（UUIDv7 採番）/ ADR-0027（Spring Data JDBC 永続化）/ ADR-0030（JDBC 一本化）
- ADR-0017（tflint 導入）— 同種の lint ツール導入の前例
- 対象ファイル: `src/main/resources/db/migration/V1__create_jockey.sql`
