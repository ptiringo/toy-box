# Copilot Instructions Structure

このディレクトリには、GitHub Copilot Coding Agent の用途別カスタム指示ファイルが格納されています。

## ファイル構成

### 📁 `.github/instructions/`
- **`api.instructions.md`**: API開発（Kotlin Spring Boot WebFlux）専用の指示
- **`testing.instructions.md`**: テスト開発専用の指示
- **`docs.instructions.md`**: ドキュメント作成専用の指示

### 📄 `.github/copilot-instructions.md`
グローバルな指示（言語・スタイル、命名規則、EditorConfig準拠）を含む

## 適用範囲

GitHub Copilot は以下の優先順位で指示ファイルを参照します：

1. **ディレクトリ固有**: `.github/instructions/{用途}.instructions.md`
2. **グローバル**: `.github/copilot-instructions.md`

この構成により、各専門領域に最適化された指示を提供しながら、全体的な一貫性を保持できます。

## 参考資料

- [GitHub公式: Copilot Coding Agent Custom Instructions](https://github.blog/changelog/2025-07-23-github-copilot-coding-agent-now-supports-instructions-md-custom-instructions/)
