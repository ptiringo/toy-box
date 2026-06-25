# Flyway マイグレーション SQL チェック体制 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Flyway マイグレーション SQL に対し、書式・スタイル（sqlfluff）とマイグレーション安全性（squawk）を機械的にチェックする体制を、ローカル（lefthook）と CI に組み込む。

**Architecture:** mise で squawk（`github:` 単一バイナリ）と sqlfluff（`pipx:` Python パッケージ）を管理し、`mise exec --` 経由で起動する。lefthook の pre-commit に `glob: "**/*.sql"` で両ツールを追加し、CI は独立ワークフロー `sql-check.yml` で同じチェックを実行する。dialect は postgres 固定。

**Tech Stack:** mise（github / pipx バックエンド。`ubi` は deprecated のため使わない）、squawk v2.58.0+、sqlfluff（最新安定版）、lefthook、GitHub Actions。

## Global Constraints

- 対象 SQL: `src/main/resources/db/migration/**/*.sql`（現状 `V1__create_jockey.sql` のみ）
- sqlfluff の dialect は **postgres** 固定、`templater = raw`（Flyway は素の SQL）
- mise 管理ツールは必ず `mise exec --` 経由で呼ぶ（既存の gitleaks / actionlint / tflint と同様）
- ツールバージョンは `mise use ... @latest` で解決後に pin し、`lockfile = true` の運用に従い `mise.lock` を更新
- 指示ファイル（CLAUDE.md / .claude/rules）に環境依存（絶対パス・PATH・sandbox 設定）を書かない
- コミットメッセージは日本語・Conventional Commits。末尾に `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- ブランチ `feat/sql-migration-lint`（作成済み）。PR マージは merge commit（`--merge`）方針
- 探索段階のため本番 DB は未デプロイ。既存マイグレーションの整形による Flyway チェックサム変化は許容（再適用される契約テスト前提）

---

### Task 1: mise に squawk / sqlfluff / python を追加し起動を検証

最初に「sqlfluff の Python(pipx) 依存が実機で解決できるか」というリスクを潰す。

**Files:**
- Modify: `mise.toml`（`[tools]` にエントリ追加）
- Modify: `mise.lock`（`mise use` が自動更新）

**Interfaces:**
- Produces: `mise exec -- squawk <args>` と `mise exec -- sqlfluff <args>` が後続タスクで起動可能になる

- [ ] **Step 1: squawk を追加（GitHub バックエンド単一バイナリ）**

`ubi` バックエンドは deprecated のため、推奨の GitHub バックエンドを使う。
Run:
```bash
mise use "github:sbdchd/squawk@latest"
```
Expected: `mise.toml` の `[tools]` に `"github:sbdchd/squawk" = "2.58.0"`（以上）が追加され、`mise.lock` が更新される。
**失敗時の対処（複数バイナリで autodetect が曖昧な場合）**: squawk のリリースは裸バイナリ（`squawk-darwin-arm64` 等）＋ `.vsix` が混在する。autodetection が候補を一意に絞れずエラーになる場合は matching フィルタを付ける:
```bash
mise use "github:sbdchd/squawk[matching=squawk-]@latest"
```
（プラットフォーム別バイナリ名に共通する接頭辞で `.vsix` 等を除外する。実機の解決結果を見て調整する。）

- [ ] **Step 2: squawk の起動を確認**

Run:
```bash
mise exec -- squawk --version
```
Expected: バージョン（例 `squawk 2.58.0`）が表示され exit 0。

- [ ] **Step 3: sqlfluff を追加（pipx / Python）**

Run:
```bash
mise use "pipx:sqlfluff@latest"
mise exec -- sqlfluff --version
```
Expected: sqlfluff のバージョンが表示され exit 0。
**失敗時の対処（pipx が Python を見つけられない場合）**: `mise use python@3.12` を追加してから再実行する。それでも解決しない場合は `mise use "pipx:sqlfluff" --env` 等のバックエンド設定を見直す（このステップで導入手段を確定させること）。

- [ ] **Step 4: 追加内容を確認**

Run:
```bash
mise list 2>&1 | grep -iE 'squawk|sqlfluff|python'
git diff mise.toml mise.lock
```
Expected: 3ツール（python は pipx の依存として追加した場合）が表示され、`mise.toml` / `mise.lock` に差分がある。

- [ ] **Step 5: コミット**

```bash
git add mise.toml mise.lock
git commit -m "$(cat <<'EOF'
build: SQL lint 用に squawk と sqlfluff を mise へ追加

- squawk（github:sbdchd/squawk）: マイグレーション安全性チェック
- sqlfluff（pipx:sqlfluff）: SQL 書式・スタイルチェック

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: sqlfluff 設定を追加し既存マイグレーションを通す

**Files:**
- Create: `.sqlfluff`（リポジトリ root）
- Modify: `src/main/resources/db/migration/V1__create_jockey.sql`（sqlfluff fix で整形される可能性あり）

**Interfaces:**
- Consumes: Task 1 の `mise exec -- sqlfluff`
- Produces: `mise exec -- sqlfluff lint src/main/resources/db/migration` が exit 0 で通る状態

- [ ] **Step 1: `.sqlfluff` を作成**

`.sqlfluff`:
```ini
# sqlfluff 設定。Flyway マイグレーション SQL（PostgreSQL 互換）を対象とする。
# dialect は H2(PG互換) と PostgreSQL の双方の土台である postgres に固定。
# Flyway は素の SQL を実行するため templater は raw（Jinja 解釈を無効化）。
[sqlfluff]
dialect = postgres
templater = raw
max_line_length = 120

[sqlfluff:indentation]
indent_unit = space
tab_space_size = 4

[sqlfluff:rules:capitalisation.keywords]
capitalisation_policy = upper

[sqlfluff:rules:capitalisation.types]
extended_capitalisation_policy = upper
```

- [ ] **Step 2: 既存マイグレーションを lint（違反の洗い出し）**

Run:
```bash
mise exec -- sqlfluff lint src/main/resources/db/migration
```
Expected: ここでは違反が出てよい（列の整列に使う複数スペース等が `layout.spacing` に引っかかる想定）。出力の違反コードを確認する。

- [ ] **Step 3: sqlfluff の整形に従ってファイルを修正**

方針: ktfmt と同様「フォーマッタに整形を委ねる」。
Run:
```bash
mise exec -- sqlfluff fix src/main/resources/db/migration
```
Expected: `V1__create_jockey.sql` が sqlfluff のスタイルへ整形される（列整列の複数スペースが単一スペースへ等）。`git diff` で差分を目視し、コメント（先頭の日本語コメント）が壊れていないことを確認する。

- [ ] **Step 4: lint が通ることを確認**

Run:
```bash
mise exec -- sqlfluff lint src/main/resources/db/migration
echo "exit=$?"
```
Expected: 違反なし、`exit=0`。

- [ ] **Step 5: 空振り防止（ミューテーション）— 小文字キーワードで検出されること**

Run:
```bash
cp src/main/resources/db/migration/V1__create_jockey.sql "$TMPDIR/mut.sql"
# 先頭の CREATE TABLE を小文字に崩す
sed -i '' 's/CREATE TABLE/create table/' "$TMPDIR/mut.sql"
mise exec -- sqlfluff lint "$TMPDIR/mut.sql"; echo "exit=$?"
```
Expected: `capitalisation.keywords`（CP01）違反が報告され `exit` が非0（ルールが空振りしていない）。確認後 `$TMPDIR/mut.sql` は破棄。

- [ ] **Step 6: コミット**

```bash
git add .sqlfluff src/main/resources/db/migration/V1__create_jockey.sql
git commit -m "$(cat <<'EOF'
build: sqlfluff 設定を追加し既存マイグレーションを整形

- .sqlfluff（dialect=postgres / templater=raw / キーワード大文字）
- V1__create_jockey.sql を sqlfluff スタイルへ整形

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: squawk で既存マイグレーションを検証し安全性ルールを確認

**Files:**
- 検証のみ（squawk は既定ルールで運用、設定ファイルは追加しない）

**Interfaces:**
- Consumes: Task 1 の `mise exec -- squawk`
- Produces: `mise exec -- squawk src/main/resources/db/migration/*.sql` が exit 0 で通ることの確認

- [ ] **Step 1: 既存マイグレーションを squawk で検証**

Run:
```bash
mise exec -- squawk src/main/resources/db/migration/*.sql; echo "exit=$?"
```
Expected: 新規 `CREATE TABLE` は危険 DDL ルールに該当せず、違反なし `exit=0`。
**もし違反が出た場合**: 内容を確認し、新規テーブル作成として妥当なら該当ルールを `squawk.toml`（root）の `excluded_rules` で除外する。除外したら理由をコミットメッセージに記す。

- [ ] **Step 2: 空振り防止（ミューテーション）— 危険 DDL で検出されること**

Run:
```bash
cat > "$TMPDIR/danger.sql" <<'EOF'
ALTER TABLE jockey ADD COLUMN nickname VARCHAR(255) NOT NULL;
EOF
mise exec -- squawk "$TMPDIR/danger.sql"; echo "exit=$?"
```
Expected: 既存テーブルへの NOT NULL 列追加に関する警告（例 `adding-not-nullable-field` / `constraint-missing-not-valid` 等）が報告され `exit` が非0。確認後 `$TMPDIR/danger.sql` は破棄。

- [ ] **Step 3: （該当時のみ）squawk.toml をコミット**

Step 1 で除外設定を追加した場合のみ:
```bash
git add squawk.toml
git commit -m "$(cat <<'EOF'
build: squawk のルール除外設定を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```
除外設定が不要だった場合、このタスクはコミットなし（検証のみ）で完了。

---

### Task 4: lefthook pre-commit に SQL チェックを組み込む

**Files:**
- Modify: `lefthook.yml`（pre-commit の commands に2件追加）

**Interfaces:**
- Consumes: Task 1〜3 で動作確認した squawk / sqlfluff
- Produces: SQL を含むコミット時に pre-commit で両チェックが走る

- [ ] **Step 1: lefthook.yml に sqlfluff / squawk を追加**

`lefthook.yml` の `pre-commit:` の `commands:` 配下、`tflint:` の後に追加:
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

- [ ] **Step 2: lefthook を手動実行して両コマンドが走ることを確認**

Run:
```bash
mise exec -- lefthook run pre-commit
```
Expected: 出力に `sqlfluff` と `squawk` が現れ、いずれも成功（✔️）。SQL に差分が無くても lefthook はコマンド一覧を表示する。

- [ ] **Step 3: コミット**

```bash
git add lefthook.yml
git commit -m "$(cat <<'EOF'
ci: lefthook pre-commit に SQL チェック（sqlfluff / squawk）を追加

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: CI ワークフロー `sql-check.yml` を追加

**Files:**
- Create: `.github/workflows/sql-check.yml`

**Interfaces:**
- Consumes: mise 管理の squawk / sqlfluff（`jdx/mise-action` で導入）
- Produces: migration パス変更時にローカルと同じチェックが CI で走る

- [ ] **Step 1: ワークフローを作成**

`.github/workflows/sql-check.yml`:
```yaml
# SQL（Flyway マイグレーション）チェックワークフロー
#
# 目的: マイグレーション SQL の変更時に squawk（安全性）と sqlfluff（書式）を実行し、
# ローカルの pre-commit と同じ品質ゲートを CI でも担保する。

name: SQL Check

on:
  pull_request:
    branches: [ main ]
    types: [opened, synchronize, reopened]
    paths:
      - 'src/main/resources/db/migration/**'
      - '.sqlfluff'
  push:
    branches: [ main ]
    paths:
      - 'src/main/resources/db/migration/**'
      - '.sqlfluff'
  workflow_dispatch:

jobs:
  sql-check:
    runs-on: ubuntu-latest
    name: SQL Lint and Migration Safety Check
    timeout-minutes: 5
    permissions:
      contents: read

    steps:
    - name: Checkout code
      uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0
      with:
        persist-credentials: false

    - name: Install mise
      uses: jdx/mise-action@e6a8b3978addb5a52f2b4cd9d91eafa7f0ab959d # v4.2.0

    - name: Verify tool versions
      run: |
        squawk --version
        sqlfluff --version

    - name: sqlfluff lint
      run: sqlfluff lint src/main/resources/db/migration

    - name: squawk migration safety
      run: squawk src/main/resources/db/migration/*.sql
```
注: action の pin（commit SHA）は `terraform-check.yml` の `actions/checkout` / `jdx/mise-action` と同一バージョンに揃える。実装時に該当ファイルの SHA をコピーして一致させること。

- [ ] **Step 2: actionlint でワークフローを検証**

Run:
```bash
mise exec -- actionlint .github/workflows/sql-check.yml
echo "exit=$?"
```
Expected: 違反なし `exit=0`。

- [ ] **Step 3: zizmor でセキュリティ監査**

Run:
```bash
mise exec -- zizmor --offline --quiet .github/workflows/sql-check.yml
echo "exit=$?"
```
Expected: 違反なし `exit=0`（`permissions: contents: read` / `persist-credentials: false` を設定済み）。

- [ ] **Step 4: コミット**

```bash
git add .github/workflows/sql-check.yml
git commit -m "$(cat <<'EOF'
ci: SQL チェック用 GitHub Actions ワークフローを追加

- migration パス変更時に sqlfluff lint と squawk を実行
- terraform-check.yml を雛形に最小権限で構成

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: ADR-0032 とドキュメント整備

**Files:**
- Create: `docs/adr/0032-sql-lint-squawk-sqlfluff.md`（/adr スキルで起こす）
- Modify: `docs/adr/README.md`（ADR 一覧へ追記。既存フォーマットに従う）
- Modify: `CLAUDE.md`（「コード品質チェック」「ツール管理（mise）」「Lefthook」節）

**Interfaces:**
- Consumes: Task 1〜5 の確定した構成（ツール・設定・lefthook・CI）

- [ ] **Step 1: /adr スキルで ADR-0032 を起こす**

`/adr` スキルを起動し、以下を記録する:
- タイトル: SQL lint に squawk + sqlfluff を採用する
- Context: マイグレーション SQL が増え、他ファイル種別と同水準のチェックが無い。3チェック目的（書式・安全性・構文）を満たしたい
- Decision: squawk（安全性 + 実パース、github バックエンド単一バイナリ。ubi は deprecated）+ sqlfluff（書式・スタイル、pipx、dialect=postgres）の2本立て
- Consequences / 却下案: B案（squawk + 軽量フォーマッタ＝ルール lint が弱い）、C案（squawk のみ＝書式統一を満たせない）。sqlfluff の Python 依存は pipx でツール内に閉じる
- 関連: ADR-0017（tflint 導入の前例）、ADR-0027 / ADR-0030（永続化）

- [ ] **Step 2: CLAUDE.md を追記**

「### コード品質チェック」節に SQL チェックのコマンド例を追加:
```bash
# SQL（Flyway マイグレーション）の lint
mise exec -- sqlfluff lint src/main/resources/db/migration   # 書式・スタイル
mise exec -- sqlfluff fix src/main/resources/db/migration    # 自動整形
mise exec -- squawk src/main/resources/db/migration/*.sql    # マイグレーション安全性
```
「## ツール管理」→「現在管理されているツール」リストに `squawk` と `sqlfluff` を追加（1行ずつ、用途付き）。
「### Lefthook」の pre-commit 説明に「sqlfluff / squawk による SQL チェック」を追記。

- [ ] **Step 3: ドキュメントの整合を確認**

Run:
```bash
mise exec -- ec CLAUDE.md docs/adr/0032-sql-lint-squawk-sqlfluff.md docs/adr/README.md
echo "exit=$?"
```
Expected: EditorConfig 違反なし `exit=0`。

- [ ] **Step 4: コミット**

```bash
git add docs/adr/0032-sql-lint-squawk-sqlfluff.md docs/adr/README.md CLAUDE.md
git commit -m "$(cat <<'EOF'
docs: SQL lint 導入を ADR-0032 と CLAUDE.md に記録

- ADR-0032: squawk + sqlfluff 採用の経緯と却下案
- CLAUDE.md: コード品質チェック / ツール管理 / Lefthook 節へ追記

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: 最終検証と PR

**Files:**
- 変更なし（検証 + PR 作成）

- [ ] **Step 1: SQL チェックを通しで実行**

Run:
```bash
mise exec -- sqlfluff lint src/main/resources/db/migration && \
mise exec -- squawk src/main/resources/db/migration/*.sql && \
echo "ALL GREEN"
```
Expected: `ALL GREEN`。

- [ ] **Step 2: lefthook 全体を実行**

Run:
```bash
mise exec -- lefthook run pre-commit
```
Expected: sqlfluff / squawk を含む全コマンドが成功。

- [ ] **Step 3: PR を作成**

```bash
git push -u origin feat/sql-migration-lint
gh pr create --title "feat: Flyway マイグレーション SQL のチェック体制（squawk + sqlfluff）" --body-file -
```
PR 本文には設計書（`docs/superpowers/specs/2026-06-26-sql-migration-lint-design.md`）と ADR-0032 へのリンク、導入ツールと組み込み先（lefthook / CI）を記載し、末尾に `🤖 Generated with [Claude Code](https://claude.com/claude-code)` を付す。

- [ ] **Step 4: CI（sql-check / 既存チェック）がグリーンであることを確認**

Run:
```bash
gh pr checks --watch
```
Expected: `SQL Check` を含む全チェックが pass。
