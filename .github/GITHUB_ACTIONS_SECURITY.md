# GitHub Actions セキュリティガイドライン

## 概要

このリポジトリでは、GitHub Actionsでサードパーティアクションを使用する際のセキュリティを確保するため、すべてのサードパーティアクションをコミットハッシュで固定しています。

## セキュリティ方針

### サードパーティアクションのバージョン固定

**必須ルール**: すべてのサードパーティアクションは `uses: <owner>/<repo>@<commit_hash>` の形式で指定してください。

#### 良い例
```yaml
- name: Checkout code
  uses: actions/checkout@692973e3d937129bcbf40652eb9f2f61becf3332 # v4.1.7
```

#### 避けるべき例
```yaml
# タグ指定（セキュリティリスクあり）
- name: Checkout code
  uses: actions/checkout@v4

# ブランチ指定（セキュリティリスクあり）
- name: Checkout code
  uses: actions/checkout@main
```

### 理由

- **セキュリティ**: タグやブランチは後から書き換えられる可能性があり、悪意のあるコードが注入されるリスクがあります
- **再現性**: コミットハッシュを使用することで、常に同じコードが実行されることが保証されます
- **監査性**: 使用するアクションの内容が明確になり、セキュリティ監査が容易になります

## 運用方法

### 新しいアクションの追加時

1. 使用したいアクションのリポジトリで対象のタグまたはリリースを確認
2. そのタグが指すコミットハッシュを取得
3. コミットハッシュでアクションを指定し、コメントで元のタグ情報を併記

### コミットハッシュの取得方法

```bash
# GitHub CLIを使用する場合
gh api repos/OWNER/REPO/git/refs/tags/TAG_NAME

# または、GitHubウェブサイトでタグページを確認
# https://github.com/OWNER/REPO/releases/tag/TAG_NAME
```

### アクションのアップデート時

1. 新しいバージョンのリリースを確認
2. セキュリティアップデートやバグ修正の内容を検証
3. 新しいコミットハッシュを取得して更新
4. コメント内のバージョン情報も併せて更新

## チェックリスト

新しいワークフローを作成する際は、以下を確認してください：

- [ ] すべてのサードパーティアクションがコミットハッシュで指定されている
- [ ] 各アクションのコメントに対応するタグ/バージョン情報が記載されている
- [ ] 使用するアクションのセキュリティ情報を確認済み

## 現在使用中のアクション

| アクション | コミットハッシュ | 対応バージョン |
|-----------|-----------------|---------------|
| actions/checkout | 692973e3d937129bcbf40652eb9f2f61becf3332 | v4.1.7 |
| actions/setup-java | 99b8673ff64fbf99d8d325f52d9a5bdedb8483e9 | v4.2.1 |
| gradle/actions/setup-gradle | dbbdc275be76ac10734476cc723d82dfe7ec6eda | v4.0.0 |
| editorconfig-checker/action-editorconfig-checker | d4c87b8aa72665554f5355abe02c6c7e1ebaebdc | v2.0.1 |
| jdx/mise-action | 1f5d0ff6b99aef0fc7f2bb567c206be6e22966eb | v2.0.4 |

## 参考資料

- [GitHub Actions のセキュリティ強化](https://docs.github.com/ja/actions/security-guides/security-hardening-for-github-actions)
- [サードパーティアクションの使用](https://docs.github.com/ja/actions/security-guides/security-hardening-for-github-actions#using-third-party-actions)