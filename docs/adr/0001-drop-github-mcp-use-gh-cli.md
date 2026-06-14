# 0001. GitHub MCP を撤去し gh CLI 直接利用へ切り替え

- Status: Accepted
- Date: 2026-06-14
- Deciders: ptiringo

## Context（背景・課題）

Claude Code の Bash サンドボックス（Seatbelt）内では、Go 製 CLI である `gh` の TLS 検証が
`com.apple.trustd.agent` に到達できず `OSStatus -26276` で失敗する。当初この問題を回避する目的で、
GitHub の issue / PR 操作を **GitHub MCP（`api.githubcopilot.com`）経由**で行う構成を導入していた。

しかし GitHub MCP には次の問題があった。

- OAuth 認可サーバーが **Dynamic Client Registration（DCR / RFC 7591）非対応**で、Claude Code の
  MCP OAuth フローが `Incompatible auth server: does not support dynamic client registration` で失敗する。
- 代替として PAT 方式に戻すと、トークンを env に補間する手間（`fnox exec` 経由での起動）が増える。

つまり「`gh` の TLS 回避」という当初の導入動機に対し、GitHub MCP 自体の認証が安定せず、運用コストが高かった。

### 検討した代替案

- **`sandbox.enableWeakerNetworkIsolation: true`**: サンドボックス内に `trustd` アクセスを開けば
  `gh` を sandbox 内に留めたまま TLS を通せる。ただしネットワーク分離が弱まる。MITM プロキシ + 独自 CA
  でプロキシ経由を維持したいケース向けの選択肢であり、標準プロキシのみの本リポジトリでは公式も
  `excludedCommands` を第一推奨としているため採らなかった。

## Decision（決定）

GitHub MCP を撤去し、GitHub の issue / PR 操作は **`gh` CLI を直接使う**。

サンドボックス下の TLS 問題は、`gh` を `.claude/settings.local.json` の `sandbox.excludedCommands` に
登録して **sandbox の外で実行**することで回避する。

- `"gh"` と `"gh *"` の両方を登録する（マッチングは完全一致 + グロブのため）。
- 複合コマンド（`A && gh ...`）はマッチしないので、`gh` は単体コマンドとして実行する。

## Consequences（結果・影響）

- GitHub 操作は通常の `gh` コマンド（`gh pr ...` / `gh issue ...` 等）で完結する。env 補間も MCP 承認
  フローも不要になった。
- `fnox` で GitHub PAT を管理する必要がなくなった（`fnox.toml` の `[secrets]` は空のまま。仕組み自体は
  将来の GCP 認証情報などに備えて維持）。
- リポジトリ管理の MCP（`.mcp.json`）には `context7` / `terraform` のみが残り、構成がシンプルになった。
- 運用ルールの現行版は CLAUDE.md「MCP サーバー設定」および `.claude/rules/secrets.md` を参照。
