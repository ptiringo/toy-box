# 0065. Claude Code on the web を軽量変更ワークフローとして最小サポートする

- Status: Accepted
- Date: 2026-07-11
- Deciders: Matsui

## Context（背景・課題）

[Claude Code on the web](https://code.claude.com/docs/ja/claude-code-on-the-web) は Anthropic 管理のクラウド VM でセッションを実行し、モバイルからも監視できる。本リポジトリでもクラウド／モバイルから軽い変更や PR 起票を回せるようにしたい。

ただしクラウドセッションは「リポジトリのクローンにあるものだけ」で起動し、ローカルの mise 管理ツール・環境変数は引き継がれない。素の環境（プリインストール: OpenJDK 21・Maven・Gradle・Docker/compose・PostgreSQL 16・Node、リソース 4 vCPU / 16 GB RAM / 30 GB disk）と本リポジトリの要求（Gradle toolchain `languageVersion = 25`）にギャップがある。素の JDK 21 では toolchain auto-detection を満たせない。

さらに Claude Code on the web では、**セットアップスクリプトの登録・環境変数・Custom 許可ドメインがクラウド環境 UI 側に保存され、リポジトリにコミットできない**。これは「Claude への指示ファイルはリポジトリ管理・ポータブルに保つ」という本リポジトリの方針（CLAUDE.md）と衝突する。

スコープとして次の 2 案を検討した。

- **フル同等化**: kotlin-lsp のクラウド有効化・gh + Projects/PR マージ運用・terraform MCP の動作確認までローカルと揃える。学習・利便性は高いが、UI 側非ポータブル設定が増え、取得先ドメインと失敗点も膨らむ。
- **最小構成**: まず `./gradlew check`（ビルド + テスト + Testcontainers + カバレッジゲート）がクラウドで緑になることだけをゴールにする。

まず足場として動くこと（check が緑）を優先し、**最小構成**を採る。

## Decision（決定）

Claude Code on the web を軽量変更ワークフローとして**最小構成でサポートする**。

- 素の JDK 21 と toolchain 25 のギャップは **mise で JDK 25 を供給**して埋める。ヘルパー `scripts/web-setup.sh` は mise 導入 → `mise install java` → mise activate 仕込みを担い、**`mise install java`（JDK 25 のみ）に限定**する（全ツール導入はしない）。`./gradlew check` に必要なのは JDK 25 と Docker（プリインストール）のみで、kotlin-lsp（JetBrains ホスト）や pipx 系ツールの取得失敗で `mise install` 全体を止めない・不要な Custom 許可ドメインを増やさないため。
- PATH 注入は既存の `.claude/hooks/session-start-mise.sh`（SessionStart フック。毎セッション `mise hook-env` を実行）に委ね、web 専用の新規フックは追加しない。
- UI 側にしか置けない非ポータブル設定（セットアップスクリプト登録・env・Custom 許可ドメイン）は、コミットできる**再現手順ドキュメント `docs/claude-code-on-the-web.md`** に残す。Custom 許可ドメインの出所は `.devcontainer/allowed-domains.txt`（コミット共有）にリンクで指し、値を二重管理しない。
- kotlin-lsp のクラウド有効化・gh + `GH_TOKEN` 運用・terraform MCP のクラウド確認は**スコープ外**とし、必要になったら別 Issue で扱う。

## Consequences（結果・影響）

- クラウド／モバイルから軽い変更・PR 起票を回せる足場が整う。`./gradlew check` が緑になる最小構成に絞ったことで、導入コストと失敗点を小さく保てる。
- UI 側設定（スクリプト登録・env・Custom ドメイン）は共有できないため、各自が `docs/claude-code-on-the-web.md` の手順を見て再現する必要がある。この非ポータブル性はドキュメントによる再現手順で補償する（リポジトリ管理・ポータブル方針との折り合い）。
- 全ツールを入れないため、クラウドでは lint 系ゲート（ktfmt 以外の shellcheck / sqlfluff / dprint / actionlint / zizmor など）の一部が動かない。ただしそれらは CI / pre-commit が担うため `./gradlew check` の緑には影響しない。
- 実クラウドセッションでの `./gradlew check` 緑・Custom 許可ドメインの過不足確定・16 GB RAM での Testcontainers 挙動は、初回セッションで実測して `docs/claude-code-on-the-web.md` に反映する運用とする。
