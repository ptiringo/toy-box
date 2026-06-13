# シークレット管理規約（mise + fnox）

ローカル開発で必要なシークレットは、平文で `~/.zshrc` 等に `export` せず、**暗号化保管／外部参照＋必要時だけ環境変数へ展開**する。仕組みは mise で管理する [fnox](https://fnox.jdx.dev/)（mise 作者 jdx 製）で統一する。

**現状、リポジトリ管理下で定義しているシークレットはない**（`fnox.toml` の `[secrets]` は空）。GitHub 操作は当初 GitHub MCP（PAT を env 補間で渡す案も検討）で行っていたが、**MCP を撤去して `gh` CLI 直接利用へ切り替えた**（sandbox 下の TLS 問題は `sandbox.enableWeakerNetworkIsolation` で解消。CLAUDE.md「MCP サーバー設定」を参照）ため、fnox での PAT 管理は不要になった。本ファイルは、将来 GCP 認証情報など別シークレットを扱う際の**運用規約**として維持する。

## バックエンド: 1Password（案C）

シークレットの実体は 1Password の Vault に保管し、`fnox.toml` には **`op://` 参照のみ**を書く（平文も暗号化値も置かない）。fnox は値の解決時に 1Password CLI (`op`) を `op inject` 経由で呼び出す。

- **サービスアカウントトークンは不要**: `op` がサインイン済み（デスクトップアプリ連携 or `op signin`）であれば、`OP_SERVICE_ACCOUNT_TOKEN` なしで解決できる。これにより「トークンのブートストラップ（鶏卵問題）」と「個人プランでのサービスアカウント可用性」の懸念を回避している。
- `fnox.toml` は参照のみのため **git にコミットしてよい**（チーム共有時も各自の `op` セッションで解決される）。

## 前提セットアップ（初回のみ）

1. **1Password デスクトップアプリの CLI 連携を有効化**
   - 1Password アプリ → 設定 → 開発者 →「1Password CLI と連携」をオン（生体認証で `op` が解錠される）。
   - 確認: `op whoami`（サインイン済みアカウントが表示されること）。
2. **シークレットの実体を 1Password に保管**
   - 任意の Vault にシークレットを保存し、その参照（`op://<vault>/<item>/<field>`）を `fnox.toml` の `value` に設定する。
   - 例: `op://Private/GCP service account/credential`
   - 各シークレットは最小権限で発行する。

## 日常運用

### シークレットの確認・追加

```bash
# 定義済みシークレットの一覧（値は解決せず参照のみ表示）
fnox list

# 値が解決できるか確認（op サインインが必要）
fnox get <ENV_NAME>

# 設定の健全性チェック
fnox doctor

# 新しいシークレット参照を追加（実トークンは 1Password 側に保存済みにしておく）
fnox set <ENV_NAME> "op://<vault>/<item>/<field>" --provider onepass
```

> Claude Code の Bash サンドボックス内からは `op` がデスクトップアプリのソケットに到達できずエラーになる。`fnox` / `op` をローカルで実行・検証する際はサンドボックスを無効化して実行する（Claude Code では当該コマンドをサンドボックス無効で実行する）。

### プロセスへの env 供給

シークレットを必要とするプロセスには、`fnox exec` で**起動時のみ env に展開**して渡す（永続化しない）。

```bash
# このセッション/プロセスの間だけ env に展開され、終了後は環境に残らない
fnox exec -- <command>
```

**代替: cd 時の自動ロード（`fnox activate`）**

```bash
# ~/.zshrc
eval "$(fnox activate zsh)"
```

プロジェクトディレクトリに `cd` した時点で env が自動ロードされる。ただし対話シェルの環境に値が載るため、「必要時だけ展開し永続化しない」方針には `fnox exec` のほうが忠実。

> **GitHub 操作は対象外**: GitHub の issue / PR 操作は MCP ではなく `gh` CLI で行う方針（`sandbox.enableWeakerNetworkIsolation` で sandbox 下の TLS 問題を解消）のため、PAT も env 補間も `fnox exec -- claude` も不要。詳細は CLAUDE.md「MCP サーバー設定」を参照。

## git / gitleaks との整合

- `fnox.toml` は **コミット対象**（`op://` 参照のみで秘密情報を含まない）。`.gitignore` で除外しない。
- gitleaks は `op://` 参照を秘密として誤検知しない（pre-commit の gitleaks スキャンと整合）。
- 実トークンを誤って `fnox.toml` や設定ファイルに直接書かないこと（必ず参照 / env 補間を使う）。

## 関連ファイル

- `mise.toml` — fnox をツールとして管理
- `fnox.toml` — プロバイダ定義とシークレット参照（`op://`）。現状 `[secrets]` は空
- `.claude/settings.local.json` — サンドボックス設定
