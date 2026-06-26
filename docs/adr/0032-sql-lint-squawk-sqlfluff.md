# 0032. SQL lint に squawk + sqlfluff を採用する

- Status: Accepted
- Date: 2026-06-26
- Deciders: Matsui

## Context（背景・課題）

Flyway マイグレーション SQL（`src/main/resources/db/migration/`）が増えてきた。他のファイル種別と比べると、品質ゲートの整備状況にギャップがある:

| 種別 | 書式 | 安全性 | 構文 |
|------|------|--------|------|
| Kotlin | ktfmt | detekt | コンパイラ |
| Terraform | `terraform fmt` | Trivy | `terraform validate` |
| GitHub Actions | — | zizmor | actionlint |
| **SQL** | **未整備** | **未整備** | **未整備** |

SQL に機械チェックが無いと、マイグレーションで次のリスクが顕在化しやすい:

1. **書式の乱れ**: キーワード大文字化・インデント・行長が不統一になり可読性が落ちる
2. **安全性の問題**: `NOT NULL` カラム追加時のデフォルト値欠落、ロックを取りすぎる `ALTER TABLE`、`CONCURRENTLY` なし `CREATE INDEX` など、本番 PostgreSQL で問題になる移行を見逃す
3. **構文エラー**: シンタックスは `flyway migrate` まで検出されず、PR レビューで人手に頼る

解消したいゴールは 3 つ: **書式統一**・**マイグレーション安全性チェック**・**構文検証**。

### 検討したツールセット案

- **案A（squawk + sqlfluff）**: squawk で安全性 + libpg_query 実パース、sqlfluff で書式・スタイルを担当。2ツールで役割分担する
- **案B（squawk + 軽量フォーマッタ）**: sqlfluff の代わりに `pg_format` 等の軽量フォーマッタを使う。書式整形はできるがルール lint が弱い
- **案C（squawk のみ）**: 安全性チェックと構文検証は得られるが、書式統一を満たせない

### 各ツールの調査結果（2026年時点）

**squawk**:
- リポジトリ: `github:sbdchd/squawk`、アクティブに開発継続中
- PostgreSQL の安全なマイグレーション規約（`NOT NULL WITHOUT DEFAULT`・`ADDING SERIAL FIELD`・`BAN DROP COLUMN` 等）をルールベースで検査
- バックエンドとして `libpg_query` を使い、実際に SQL をパースするため構文エラーも検出できる
- `github` バックエンドが単一バイナリで配布（mise の github backend で管理可）。`ubi` バックエンドは deprecated のため使わない
- `.squawk.toml` で除外ルールを宣言: `prefer-robust-stmts`（Flyway が `flyway_schema_history` で冪等性を保証するため文単体の堅牢化が不要）・`prefer-text-field`（新規テーブルでは `varchar(n)` にロックリスクがなく、Spring Data JDBC の型マッピング都合で `varchar` を許容する設計判断）

**sqlfluff**:
- 成熟した SQL フォーマッタ兼 linter（v4.x）
- `dialect = postgres` で PostgreSQL 方言対応、`templater = raw` でテンプレート非使用の単純 SQL を扱う
- キーワード大文字化・型名大文字化・最大行長 120 文字などのスタイルルールをカバー
- `version BIGINT` の列名は sqlfluff の `RF04`（`references.keywords`）が予約語的識別子として警告するため、その行のみ `-- noqa: RF04` でインライン抑制する（`version` は PostgreSQL の予約語ではなく Spring Data JDBC の楽観ロック列として正当な識別子。RF04 は sqlfluff のルールであり squawk とは無関係）
- Python ツールだが mise の `pipx:` backend で管理し、その実行ドライバに uv を使うことで Python 依存をツール環境内に閉じ込める。uv が入っていれば `pipx:` backend は uv 経由でインストールするため、pipx 本体を別ツールとして入れる必要はない

## Decision（決定）

**SQL lint に squawk（安全性 + 構文）と sqlfluff（書式・スタイル）の 2 ツールを採用する（案A）**。

役割分担:

| ツール | 担当 | 管理方法 |
|--------|------|---------|
| squawk | マイグレーション安全性チェック・構文検証 | mise（github backend）|
| sqlfluff | 書式・スタイル lint・自動整形 | mise（`pipx:` backend / uv 駆動）|

設定ファイル:

- **`.sqlfluff`**: `dialect = postgres`、`templater = raw`、キーワード / 型名大文字化、`max_line_length = 120`
- **`.squawk.toml`**: `prefer-robust-stmts` と `prefer-text-field` を `excluded_rules` に宣言し、採用しない理由をコメントで明記

Lefthook の pre-commit（`**/*.sql` glob）と CI（`sql-check.yml`）の両経路でチェックする。

## Consequences（結果・影響）

- **良くなる点**: SQL が他のファイル種別と同水準の機械チェックを受けるようになり、本番 PostgreSQL で問題になるマイグレーションを PR 段階で検出できる。書式が統一され可読性が上がる。
- **案B を採らなかった理由**: 軽量フォーマッタ（`pg_format` 等）は整形のみでルール lint がない。書式統一はできても安全性以外の sqlfluff ルール（長さ・命名規則等）を持てず、後から機能追加する動機が弱まる。
- **案C を採らなかった理由**: squawk のみでは書式統一を満たせない。sqlfluff の Python 依存が懸念されたが、mise の `pipx:` backend（uv 駆動）で依存を閉じ込めることで他ツールと同等の管理コストに収まる。
- **引き受けるトレードオフ**:
  - sqlfluff は Python ベースのため、他の Go / Rust 系ツールと比べると初回インストールに若干時間がかかる。uv 駆動で最小化している。
  - `.squawk.toml` の `excluded_rules` と `-- noqa: RF04` は意図的な抑制であり、新規 SQL ファイルを追加する際はそれらが適切かを確認する。
- **関連**: Terraform lint 導入（tflint + Trivy）の前例は [ADR-0017](0017-terraform-quality-gates-tflint-trivy-defer-opa.md)。永続化層（Flyway マイグレーション SQL の出所）は [ADR-0027](0027-persistence-spring-data-jdbc.md) / [ADR-0030](0030-jdbc-only-persistence-retire-inmemory.md)。
