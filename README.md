# toy-box

## セットアップ (Setup)

このプロジェクトでは、開発環境のセットアップを自動化するために `.github/copilot-setup-steps.yml` を提供しています。

### 必要な環境 (Prerequisites)

- Java 21 (Eclipse Temurin)
- editorconfig-checker
- Gradle (wrapper included)

### 自動セットアップ手順 (Automated Setup)

`.github/copilot-setup-steps.yml` ファイルに定義された手順に従って環境をセットアップできます：

1. **Java 21 のインストール**
   - Ubuntu: APT パッケージマネージャーを使用
   - macOS: Homebrew を使用
   - Windows: Chocolatey を使用

2. **EditorConfig Checker のインストール**
   - ファイル形式の一貫性チェック用ツール

3. **環境確認**
   - インストールされたツールのバージョン確認

4. **プロジェクトビルド確認**
   - Gradle を使用したビルドテスト

5. **EditorConfig チェック**
   - プロジェクトファイルの形式確認

### 手動セットアップ (Manual Setup)

自動セットアップが利用できない場合は、以下の手順で環境を構築してください：

```bash
# Java 21 のインストール (Ubuntu)
wget -O - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo apt-key add -
echo 'deb https://packages.adoptium.net/artifactory/deb $(lsb_release -sc) main' | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install -y temurin-21-jdk

# editorconfig-checker のインストール (Ubuntu)
curl -L -o editorconfig-checker.tar.gz https://github.com/editorconfig-checker/editorconfig-checker/releases/latest/download/ec-linux-amd64.tar.gz
tar -xzf editorconfig-checker.tar.gz
sudo mv bin/ec-linux-amd64 /usr/local/bin/editorconfig-checker
sudo chmod +x /usr/local/bin/editorconfig-checker
rm -rf bin editorconfig-checker.tar.gz

# プロジェクトビルド
cd api
./gradlew build

# EditorConfig チェック
editorconfig-checker
```

### 技術仕様 (Technical Specifications)

- **Java**: 21 (Eclipse Temurin)
- **Kotlin**: 2.2.0
- **Spring Boot**: 3.5.3
- **Gradle**: 8.14.2
- **WebFlux**: 非同期処理対応
