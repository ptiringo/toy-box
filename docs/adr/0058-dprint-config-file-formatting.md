# 0058. 設定ファイル（TOML/JSON/YAML）の整形に dprint を採用し lefthook / CI でゲートする

- Status: Accepted
- Date: 2026-07-08
- Deciders: Matsui

## Context（背景・課題）

Issue #328。設定ファイル（TOML / JSON / YAML）が増えてきたが、構造整形（インデント階層・キー整列・
配列の折り返し）を機械化する仕組みがない。`editorconfig-checker` は改行・文字コード・行末空白の
whitespace レベルしか強制せず、フォーマッタが担う構造整形は対象外。Kotlin は ktfmt、Terraform は
`terraform fmt` で整形しているのに、TOML / JSON / YAML だけ手作業に委ねられている。

ツールバージョンの単一の出所は引き続き `mise.toml` とし、**Node 依存を避けて単一バイナリ（Rust/Go 系）で
揃える**方針（CLAUDE.md「ツール管理」、#327／[ADR-0054](0054-vacuum-openapi-lint.md) で Spectral より
vacuum を選好したのと同じ論点）に沿わせたい。

### 検討した要素

- **対象形式とコメント保持の要件**: 本命は TOML（`gradle/libs.versions.toml` / `mise.toml` / `fnox.toml`。
  キー整列・並びの一貫性が効く）。JSON は実体が JSONC（`.vscode/*.json` / `.claude/settings.json` /
  `.devcontainer/devcontainer.json`。とくに devcontainer.json はコメント 32 行を持つ）。YAML も
  ワークフロー・`application.yml` 等がコメント過多。したがって **コメントを破壊しない整形器であることが必須要件**。
- **フォーマッタの選定**: 以下を比較し、いずれも却下理由が明確なため dprint を採る。
  - **prettier**: Node 依存で、かつコアが TOML 非対応（本命が外れる）。プラグインで TOML を足せるが
    それ自体 Node パッケージで依存を増幅する。#327 と同論点で却下。
  - **Spotless（Gradle プラグイン）**: ①TOML のネイティブ整形ステップが無い（Node の prettier を挟まねば
    本命 TOML を触れない）。②YAML/JSON は Jackson のデータバインディング往復で**コメントが消失**する
    （コメント保持要件に反する）。③置き換えたい既存ツールの大半は整形器でなくリンタ（detekt / sqlfluff /
    shellcheck / actionlint 等）で Spotless では代替できず「ツール数削減」にならない。④ktfmt を Spotless の
    ktfmt ステップへ移すのは全 Kotlin コードの整形挙動に関わる別テーマで、本 Issue のスコープ外。よって却下。
  - **oxfmt（Oxc フォーマッタ）**: TOML / JSON / JSONC / YAML を単一バイナリで全対応し format coverage は
    十分だが、公式に案内される配布経路が npm 系（`npm add -D oxfmt`）のみで、採用は現在 Node/JS ファイルが
    0 の本リポジトリに **`package.json` / `node_modules` / Node ランタイム / npm を CI に**——という
    Node ツールチェーンの土台ごと持ち込むことを意味する。pre-1.0 でもある。Node 回避方針に反するため却下。
  - **taplo（TOML 専用）/ yamlfmt（YAML 専用）**: 各形式では堅実だが単一形式ずつで、対象 3 形式を賄うには
    複数ツールになる。dprint 単一で足りるため不要。
  - **dprint（Rust 単一バイナリ）**: `json` / `toml` / `pretty_yaml`（`g-plane`）の Wasm プラグインで
    **TOML + JSON(JSONC) + YAML を単一バイナリでカバー**し、いずれもコメントを保持する。`aqua` backend で
    mise 管理でき（tbls / shellcheck / trivy / vacuum と同じ供給経路）、既存の流儀に最も素直に整合する。採用。
- **並び順の自動化**: dprint の toml プラグインは**キー順序を保持**する（並び替えない）。`libs.versions.toml` の
  意味的グルーピングを壊さないため安全（taplo の sort とは異なる）。今回は**構造整形のみ**とし、
  キー/配列ソートは差分肥大を避けて段階導入の余地に残す。
- **`.editorconfig` との整合**: dprint は `.editorconfig` を自動では読まないため、`dprint.json` の global 設定
  （`newLineKind = lf`・末尾改行・`indentWidth`）を `.editorconfig` に合わせる。`.editorconfig` は Kotlin 以外に
  indent 規則を持たないため、dprint 出力（LF・末尾改行・行末空白なし）は `editorconfig-checker` と衝突しない。
- **プラグイン供給と再現性**: dprint プラグインは `plugins.dprint.dev` の**バージョン付き Wasm URL**（不変
  アーティファクト）を `dprint.json` から参照してピンする（`dprint config add` が最新版 URL を書き込む）。
  版付き URL 自体が実体を一意に固定するため再現性が保たれる。これは `mise.toml` の `http:` backend
  （tfctl / kotlin-lsp を版付き URL＋sha256 で固定）と同じ「版で固定する」哲学に連なる。
- **ゲートの場所**: 設定ファイルのみが対象でビルド不要＝軽量なので、lefthook pre-commit（staged 限定）と
  CI の双方に載せられる。関心ごとに独立ワークフローを並べる既存の流儀（editorconfig-check.yml /
  shellcheck.yml / sql-check.yml / terraform-check.yml）に従い、独立ワークフロー `dprint-check.yml` とする。
- **Claude Code セッション内フィードバック**: editorconfig / terraform fmt / workflow lint は既に
  「Claude hook（PostToolUse）→ lefthook → CI」の 3 層で先回り検査している（`.claude/hooks/post-edit-*.sh`）。
  dprint も揃え、`.claude/hooks/post-edit-dprint.sh`（`post-edit-terraform-fmt.sh` と同型）を PostToolUse に
  追加する。編集ファイルが対象形式なら `dprint check` し、未整形なら `exit 2` ＋ stderr で Claude に整形を促す
  （その場で自動整形させ、lefthook / CI 到達前に解消する）。この 3 層目は DX 補助で、ゲートの本体は
  lefthook / CI が担う。

## Decision（決定）

### ツール採用

**dprint を採用し、設定ファイル（TOML / JSON / YAML）を整形する**（mise 管理）。`dprint.json` に
`json` / `toml` / `pretty_yaml` の 3 プラグインをバージョン付き URL でピンする。Markdown 等の追加プラグインは
今回入れない（YAGNI）。

### スコープ

- **対象**: `*.toml`（libs.versions.toml / mise.toml / fnox.toml）、`*.json`（.mcp.json / .vscode/*.json /
  .claude/settings.json / .devcontainer/devcontainer.json）、`*.yml` / `*.yaml`（.github/workflows/* /
  src/main/resources/application.yml / lefthook.yml / config/detekt/detekt.yml / .github/dependabot.yml）。
- **除外**（`dprint.json` の excludes）: `build/**`・`.claude/worktrees/**`・`.gradle/**`、
  `.devcontainer/devcontainer-lock.json`（生成物）、`.claude/settings.local.json`（gitignore・個人設定）、
  `mise.lock`（生成物）。

### 設定方針

- `dprint.json` の global 設定を `.editorconfig` に整合させる（LF・末尾改行・indent）。
- キー/配列の**ソートは無効**（構造整形のみ）。

### 実行タイミング

- **Claude Code hook**（PostToolUse）に `.claude/hooks/post-edit-dprint.sh` を追加。編集ファイルが対象形式なら
  `dprint check` し、未整形なら `exit 2` で Claude に整形を促す（`post-edit-terraform-fmt.sh` と同型の DX 補助）。
- **lefthook pre-commit** に `dprint check`（staged 対象、`tags: format`）を追加。
- **CI** に独立ワークフロー `dprint-check.yml`（PR ゲート）を追加。`editorconfig-check.yml` とは役割分離。

3 層（Claude hook → lefthook → CI）は editorconfig / terraform fmt と同じ多層防御の構成に揃える。

ktfmt は現状（`com.ncorti.ktfmt.gradle` プラグイン）を維持する。Spotless / oxfmt / prettier は不採用。

## Consequences（結果・影響）

- TOML / JSON / YAML の構造整形が lefthook / CI でゲートされる。導入時に `dprint fmt` で既存差分を
  取り込み済みのため、以降はドリフト検出として機能する。
- JSONC / `pretty_yaml` によりコメントが保持される（devcontainer.json の 32 コメントも維持）。
- Node ツールチェーンを導入せず、Node-free / mise 単一バイナリの方針を保つ。将来 JS/TS を扱う必要が
  生じても、その判断は本 ADR とは独立に再検討する。
- ソートは未導入のため、依存の並び替え等は手動。必要になれば後続 Issue で段階導入する。
- dprint プラグインは初回に `plugins.dprint.dev` から取得する（ネットワーク要）。checksum ピンで再現性を
  担保し、CI ではプラグインキャッシュを検討する。
- **関連 ADR**: [ADR-0054](0054-vacuum-openapi-lint.md)（vacuum。単一バイナリ / aqua 管理 / 独立ワークフローで
  CI ゲートという同じ論点）、[ADR-0057](0057-gradle-build-health-tooling-not-adopted.md)（#329。近接時期の
  ビルド健全性 tooling の採否判断）。
