# Pre-commit Hooks with Lefthook

このプロジェクトでは Lefthook を使用した pre-commit hooks を導入しています。

## セットアップ

プロジェクトをクローンした後、以下のコマンドで git hooks をインストールしてください：

```bash
lefthook install
```

## 実行される検証項目

### pre-commit

コミット前に以下の検証が自動実行されます：

1. **EditorConfig チェック** - `.editorconfig` の設定に準拠しているかチェック
2. **API テスト** - 変更されたKotlinファイルに関連するテストの実行

### pre-push

プッシュ前に全体のテストスイートが実行されます。

### commit-msg

コミットメッセージが conventional commit 形式に準拠しているかチェックします。

## 手動実行

フックを手動で実行したい場合：

```bash
# 全ての pre-commit hooks を実行
lefthook run pre-commit

# 特定のコマンドのみ実行
lefthook run pre-commit api-test
```

## フックのスキップ

緊急時にフックをスキップしたい場合：

```bash
# pre-commit hooks をスキップしてコミット
git commit --no-verify -m "緊急修正"

# 特定のコマンドをスキップ
LEFTHOOK_EXCLUDE=api-test git commit -m "テストをスキップしてコミット"
```

## トラブルシューティング

### Lefthook がインストールされていない場合

```bash
# Ubuntu/Debian
curl -1sLf 'https://dl.cloudsmith.io/public/evilmartians/lefthook/setup.deb.sh' | sudo -E bash
sudo apt install lefthook

# または直接ダウンロード
wget "https://github.com/evilmartians/lefthook/releases/download/v1.10.0/lefthook_1.10.0_Linux_x86_64" -O lefthook
chmod +x lefthook && sudo mv lefthook /usr/local/bin/
```

### hooks が実行されない場合

```bash
# hooks を再インストール
lefthook install

# hooks の状態を確認
lefthook version
git config --list | grep hook
```
