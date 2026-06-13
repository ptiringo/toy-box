# シークレット管理規約（mise + fnox）

ローカル開発で必要なシークレットは、平文で `~/.zshrc` 等に `export` せず、**暗号化保管／外部参照＋必要時だけ環境変数へ展開**する。仕組みは mise で管理する [fnox](https://fnox.jdx.dev/)（mise 作者 jdx 製）で統一する。

当面の対象は GitHub MCP（`https://api.githubcopilot.com/mcp/`）用の `GITHUB_PERSONAL_ACCESS_TOKEN`。将来的に GCP 認証情報など他シークレットも同じ仕組みへ寄せる。

## バックエンド: 1Password（案C）

シークレットの実体は 1Password の Vault に保管し、`fnox.toml` には **`op://` 参照のみ**を書く（平文も暗号化値も置かない）。fnox は値の解決時に 1Password CLI (`op`) を `op inject` 経由で呼び出す。

- **サービスアカウントトークンは不要**: `op` がサインイン済み（デスクトップアプリ連携 or `op signin`）であれば、`OP_SERVICE_ACCOUNT_TOKEN` なしで解決できる。これにより「トークンのブートストラップ（鶏卵問題）」と「個人プランでのサービスアカウント可用性」の懸念を回避している。
- `fnox.toml` は参照のみのため **git にコミットしてよい**（チーム共有時も各自の `op` セッションで解決される）。

## 前提セットアップ（初回のみ）

1. **1Password デスクトップアプリの CLI 連携を有効化**
   - 1Password アプリ → 設定 → 開発者 →「1Password CLI と連携」をオン（生体認証で `op` が解錠される）。
   - 確認: `op whoami`（サインイン済みアカウントが表示されること）。
2. **1Password に GitHub PAT を保管**
   - 任意の Vault に PAT を保存し、その参照を `fnox.toml` の `value`（`op://<vault>/<item>/<field>`）に設定する。
   - 例: `op://Personal/GitHub PAT/credential`
   - PAT に必要なスコープは利用する GitHub MCP のツールに依存（最小権限で発行する）。

## 日常運用

### シークレットの確認・追加

```bash
# 定義済みシークレットの一覧（値は解決せず参照のみ表示）
fnox list

# 値が解決できるか確認（op サインインが必要）
fnox get GITHUB_PERSONAL_ACCESS_TOKEN

# 設定の健全性チェック
fnox doctor

# 新しいシークレット参照を追加（実トークンは 1Password 側に保存済みにしておく）
fnox set <ENV_NAME> "op://<vault>/<item>/<field>" --provider onepass
```

> Claude Code の Bash サンドボックス内からは `op` がデスクトップアプリのソケットに到達できずエラーになる。`fnox` / `op` をローカルで実行・検証する際はサンドボックスを無効化して実行する（Claude Code では当該コマンドをサンドボックス無効で実行する）。

### Claude Code（MCP）への env 供給

GitHub MCP は HTTP 接続で `Authorization: Bearer ${GITHUB_PERSONAL_ACCESS_TOKEN}` を要求する（`.mcp.json` 参照）。`${...}` は **`claude` を起動したプロセスの環境変数**から補間されるため、トークンを `claude` の環境に注入した状態で起動する。

**推奨: 起動時のみ注入（永続化しない）**

```bash
# このセッションの間だけ env に展開され、終了後は環境に残らない
fnox exec -- claude
```

エイリアス化しておくと楽:

```bash
# ~/.zshrc など
alias claude='fnox exec -- claude'
```

**代替: cd 時の自動ロード（`fnox activate`）**

```bash
# ~/.zshrc
eval "$(fnox activate zsh)"
```

プロジェクトディレクトリに `cd` した時点で env が自動ロードされる。ただし対話シェルの環境にトークンが載るため、「必要時だけ展開し永続化しない」方針には `fnox exec` のほうが忠実。

## git / gitleaks との整合

- `fnox.toml` は **コミット対象**（`op://` 参照のみで秘密情報を含まない）。`.gitignore` で除外しない。
- gitleaks は `op://` 参照を秘密として誤検知しない（pre-commit の gitleaks スキャンと整合）。
- 実トークンを誤って `fnox.toml` や `.mcp.json` に直接書かないこと（必ず参照 / env 補間を使う）。

## 関連ファイル

- `mise.toml` — fnox をツールとして管理
- `fnox.toml` — プロバイダ定義とシークレット参照（`op://`）
- `.mcp.json` — GitHub MCP 定義（`${GITHUB_PERSONAL_ACCESS_TOKEN}` を env 補間）
- `.claude/settings.local.json` — サンドボックス設定
