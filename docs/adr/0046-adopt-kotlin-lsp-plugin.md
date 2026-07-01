# 0046. Claude Code の Kotlin LSP プラグインを採用し kotlin-lsp を mise http バックエンドで配布する

- Status: Accepted
- Date: 2026-07-01
- Deciders: Matsui

## Context（背景・課題）

Claude Code は公式マーケットプレイス（`claude-plugins-official`）の言語別 LSP プラグインで、エージェント（Claude Code）向けの**コードインテリジェンス**を有効化できる（組み込み機能）。有効化するとエージェントは以下を得る:

- **編集直後の自動診断**: 型エラー・import 不足・構文エラーを即報告し、誤りを入れても同ターン内に検出・修正できる（compile / linter の実走を待たない）。
- **コードナビゲーション**: 定義ジャンプ・参照検索・型ホバー・呼び出し階層追跡。grep ベースより正確で、大規模コードでのファイル読み込みを削減する。

本プロジェクトは Kotlin 主体であり、この補助が内側ループ（編集→検証）の速度・正確性に効くと見込まれたため採否を評価した（#510）。

評価で確定した一次情報:

- プラグイン `kotlin-lsp@claude-plugins-official` の実体（README / `marketplace.json`）は、PATH 上の `kotlin-lsp` を `--stdio` で起動する。要求バイナリは **JetBrains 公式 `Kotlin/kotlin-lsp`**（`brew install JetBrains/utils/kotlin-lsp` を案内。IntelliJ 解析エンジン基盤で K2 完全対応）。**fwcd/kotlin-language-server ではない**（fwcd は最終 1.3.13＝2025-01 で保守停滞・K2 追随が弱く、現行コード（K2 / value class / Spring）に誤診断を出してエージェントを誤誘導するため不適）。
- JetBrains 公式の配布は `download-cdn.jetbrains.com`（mac は `.sit`、Linux は `.tar.gz`、Windows は `.zip`）で、**GitHub Releases にバイナリ資産が無い**。公式の brew tap（`JetBrains/utils`）は `depends_on :macos` で **macOS 限定**。
- 現時点で JetBrains kotlin-lsp は experimental（pre-alpha）扱い・一部クローズドソース。版は JetBrains ビルド番号（評価時点 `262.8190.0`）。

## Decision（決定）

Kotlin LSP プラグインを**採用する**。運用方針は以下。

### プラグインはリポジトリ管理で宣言する

`.claude/settings.json` の `enabledPlugins` に `"kotlin-lsp@claude-plugins-official": true` を宣言し、**クローンすれば同一構成**にする（MCP をリポジトリ管理で共有する [ADR-0003](0003-consolidate-mcp-config-in-repo.md) と同じ思想）。`claude-plugins-official` は組み込みの公式マーケットプレイスのため追加登録は不要。プラグインの取り込みは初回にユーザーの信頼承認を経る（無確認では走らない）。

### バイナリ供給: mise の http バックエンドでクロスプラットフォームに配布する

公式導入路（brew tap）は macOS 限定で、GitHub Releases 資産が無く ubi / aqua も使えない。本プロジェクトのツールは mise で版管理し `mise.lock` でクロスプラットフォーム再現性を担保する方針のため、[tfctl（ADR-0034）](0034-adopt-tfctl-cli.md) と同様に **http バックエンド**で `download-cdn.jetbrains.com` を直接指す（`mise.toml` の `[tools."http:kotlin-lsp"]`）。

- **形式**: mac の `.sit` は実体が zip のため `format = "zip"` で明示、Linux は `.tar.gz`（自動判定）。プラットフォーム別 URL を `{{version}}` テンプレートにし、**バージョン更新は version フィールド 1 箇所＋4 checksum**で済ませる。
- **コマンド名**: アーカイブは version 付きトップディレクトリ（`kotlin-server-<version>/`）配下にランチャ `kotlin-lsp.sh` を持つ。`strip_components = 1` で剥がし `rename_exe = "kotlin-lsp"` でプラグイン期待名 `kotlin-lsp` として PATH に出す。
- **整合性**: http バックエンドは aqua / ubi と異なり `mise.lock` に per-platform SHA を埋めないため、公式 `<asset>.sha256` の値を各プラットフォームの `checksum` に**インラインで固定**する（tfctl と同じ運用）。
- **検証**: macOS arm64 で `mise install http:kotlin-lsp` が成功し、`kotlin-lsp --version` が `LS-262.8190.0` を返すことを確認済み。Linux 分は同型設定（`tar.gz`）だが未実機検証のため、devcontainer で使う際に確認する。

### 役割: ゲートは置き換えない補助操舵

LSP＝**編集時のリアルタイム診断・ナビ**、detekt / ArchUnit / gradle check（stop hook の check 含む）＝**バッチの品質検証（ゲート）**。両者は目的が異なり補完関係で、LSP はゲートを置き換えない。gradle check 前に誤りへ気づけて内側ループが速くなる、が狙い。

### #462 との関係

「必要な外部スキル / プラグインを設定ファイルで宣言して一括登録する仕組み」（#462）は別スコープ。本決定は当面 `enabledPlugins` に**この 1 プラグインを手動宣言**するに留め、一般化した宣言・一括登録の仕組みは #462 で扱う。

## Consequences（結果・影響）

- `mise install` で誰でも `kotlin-lsp` バイナリが入り、クローンすればプラグインが有効化候補になる（初回承認後）。編集直後にエージェントが型・import・構文の診断を得られ、定義/参照ナビでファイル読み込みを減らせる。
- **保守コスト**: registry / Dependabot による自動更新は効かない。バージョン更新は `mise.toml` の version + 4 checksum を公式 `.sha256` から手動更新する（手順はコメントに明記）。JetBrains kotlin-lsp が aqua-registry 収載 / mise registry 短名化 / GA に達したら、より標準的な backend への移行を再評価する。
- **pre-alpha リスク**: JetBrains kotlin-lsp は experimental・一部クローズドソースで、ビルド番号ごとに挙動差の余地がある。バージョン固定で受け止め、追従是非を都度判断する。誤診断が実害になる場合はプラグインを無効化（`enabledPlugins` を false / 削除）できる。
- **負荷**: IntelliJ 解析エンジン基盤のためメモリ消費が大きく、Gradle daemon / Testcontainers との同時稼働時は負荷に注意（16GB クラスのマシンでは特に）。
- **プラットフォーム**: 設定は 4 プラットフォーム対称だが実機検証は macOS arm64 のみ。Linux（devcontainer / CI）で LSP を使う場合は別途確認する（CI ゲートには LSP は不要）。
- **devcontainer firewall**: egress 許可リスト（`.devcontainer/allowed-domains.txt`、[ADR-0037](0037-devcontainer-egress-firewall.md)）に配布ホスト `download-cdn.jetbrains.com` を追加した（無いと devcontainer 内 `mise install` が落ちる）。
- **CI への波及**: mise-action を使う lint 系ジョブ（shellcheck / sql-check / terraform-check / copilot-setup）は mise.toml の全ツールを install するため kotlin-lsp も取得する（mise-action のキャッシュで初回以降は再ダウンロードされない）。gradle 系ジョブ（api-tests / e2e-tests 等）は mise を経由しないため対象外。CI は LSP を必要としないが、tfctl 同様「全ツールを mise.toml に集約し CI も install する」既存パターンに合わせて許容する。
- **mise.lock**: http バックエンドは per-platform SHA を `mise.lock` に永続化しない（tfctl と同じく version + backend の stub のみ）。クロスプラットフォームの整合性は `mise.toml` インラインの 4 checksum が担保する。
- 運用ルールの結論は CLAUDE.md「ツール管理」に記載（経緯は本 ADR）。
