# Architecture Decision Records

このディレクトリは、設計・運用上の意思決定を記録する ADR（Architecture Decision Record）を管理する。

- 1 決定 1 ファイル。ファイル名は `NNNN-ケバブケースのタイトル.md`（4 桁ゼロ詰めの連番）。
- 本文は日本語。フォーマット・運用は `.claude/skills/adr/SKILL.md` を参照（`/adr` スキルで新規作成できる）。
- CLAUDE.md / `.claude/rules/` には**結論（守るべきルール）**を置き、「なぜそう決めたか」の経緯はこの ADR に残す。

## 一覧

| # | タイトル | Status |
|---|---------|--------|
| [0001](0001-drop-github-mcp-use-gh-cli.md) | GitHub MCP を撤去し gh CLI 直接利用へ切り替え | Accepted |
| [0002](0002-virtual-thread-over-reactive.md) | Virtual Thread を採用し、リアクティブ流派を採らない | Accepted |
| [0003](0003-consolidate-mcp-config-in-repo.md) | MCP サーバー設定をリポジトリ管理ファイルに集約する | Accepted |
| [0004](0004-secrets-fnox-1password.md) | シークレット管理を fnox + 1Password（参照のみ）で行う | Accepted |
| [0005](0005-time-based-uuid-generation.md) | エンティティ識別子をタイムベース UUID（UUIDv7 相当）に統一する | Accepted |
