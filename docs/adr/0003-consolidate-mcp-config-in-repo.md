# 0003. MCP サーバー設定をリポジトリ管理ファイルに集約する

- Status: Accepted
- Date: 2026-06-14
- Deciders: ptiringo

## Context（背景・課題）

開発に必要な MCP サーバーを各メンバーが `/plugin` 等で**アドホックに導入**すると、人によって構成が
バラつき、再現性がない。「クローンすれば誰でも同じ構成になる」状態を担保したい。

一方で、Claude Code と VS Code（GitHub Copilot）は MCP 設定ファイルの**場所もフォーマットも異なる**
（キーが `mcpServers` か `servers` か等）ため、1 ファイルで両対応はできない。

## Decision（決定）

必要な MCP は**リポジトリ管理の設定ファイルに宣言**して共有する。

- **Claude Code 用**: リポジトリ root の `.mcp.json`。採用は `context7`（http / 認証不要）と
  `terraform`（stdio docker / 認証不要）。
- **VS Code・Copilot 用**: `.vscode/mcp.json`（別ファイル・別フォーマット）。両方で使う MCP（例:
  `context7`）は片方を更新したら**もう片方も同期**する。
- 個人のグローバル設定や `/plugin` 経由で**同名サーバーを二重定義しない**（`.mcp.json` を唯一の出所とする）。
- プロジェクトスコープ `.mcp.json` は**各開発者が初回に承認**するフロー。承認状態は各自の `~/.claude.json`
  に記録され、リポジトリには載らない。

## Consequences（結果・影響）

- クローン後に Claude Code を起動すれば、未承認 MCP の確認プロンプトを経て全員が同じ構成になる。
- GitHub MCP は保持しない（GitHub 操作は `gh` CLI 直接利用。[ADR-0001](0001-drop-github-mcp-use-gh-cli.md)）。
- 設定ファイルが 2 系統あるため、共通 MCP の同期忘れに注意が必要（運用ルールとして明記）。
- 現行の運用ルールは CLAUDE.md「MCP サーバー設定」を参照。
