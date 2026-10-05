# 0085. Gradle 公式の Agent Skills（gradle/gradle-skills）をプラグインとしてリポジトリ管理で宣言する

- Status: Accepted
- Date: 2026-10-05
- Deciders: Matsui

## Context（背景・課題）

Gradle 社が AI コーディングエージェント向けの公式スキル集 [gradle/gradle-skills](https://github.com/gradle/gradle-skills)（Apache-2.0、[紹介記事](https://blog.gradle.org/introducing-gradle-skills)）を公開した。Claude Code には `gradle-skills` マーケットプレイスの `gradle-skills` プラグインとして導入でき、決定時点（1.0.0）では次の 2 スキルを同梱する。プラグインの中身は `skills/` のみで、hooks・MCP サーバ・コマンドは含まない。

- `gradle-best-practices`: Gradle 公式ベストプラクティス（48 項目）の資料をスキル内に同梱し、ビルドスクリプト・settings・`gradle.properties`・version catalog・wrapper を監査して優先度つきのレポートを出す。既定は監査のみ（Audit モード）で、頼めば修正する（Apply モード）。
- `gradle-wrapper-upgrade`: wrapper をチェックサム固定・`wrapper` タスク 2 回実行・`./gradlew tasks` による検証・失敗時のロールバックつきで更新する。

導入前に、スキルを一時領域に置いて本リポジトリをお試し監査した（Audit モード、修正なし）。結果は次のとおり。

- **価値のある指摘があった**: wrapper の `distributionSha256Sum` 未設定、設定フェーズでの Provider の `.get()`（`mise which tbls` / `vacuum` が全タスク realize 時に走る）、リポジトリ宣言が settings でなくビルドスクリプトにある、`lintOpenApiDocs` の `dependsOn`。いずれも detekt（ソースの静的解析）や [ADR-0057](0057-gradle-build-health-tooling-not-adopted.md) で検討したツール群が見ない領域で、反映は [#952](https://github.com/ptiringo/toy-box/issues/952) が引き取った。
- **ノイズもあった**: `SourceSetOutput` の `+`（遅延評価の和集合で公式 idiom）を eager API として誤検知した。`no_source_in_root` / `use_convention_plugins` は、本体を単一モジュールに保つ方針（`.claude/rules/testing.md`）と衝突する構造変更を勧めた。

`gradle-wrapper-upgrade` は、wrapper の更新を Dependabot が `gradlew` / jar まで含めて担っているため出番は少ない。

検討した代替案:

- **`gradle-best-practices` だけを `.claude/skills/` に vendoring する**。スキルは 2 本だけで常時ロードの footprint は小さく、上流追従を手動コピーにする利点がない。上流の配布単位（プラグイン）のまま受け入れる。
- **各自が `/plugin install` でアドホックに入れる**。[ADR-0003](0003-consolidate-mcp-config-in-repo.md) / [ADR-0046](0046-adopt-kotlin-lsp-plugin.md) / [ADR-0084](0084-adopt-docker-skills-plugin.md) の「リポジトリ管理の設定で宣言して共有する」方針に反するため採らない。
- **上流が予告している 1.0.1 を待つ**。1.0.1 は「2 スキル間の矛盾した助言」と「無人の監査が構造的修正まで適用しうる文言」を直す予定だが、本リポジトリでは対話セッションで Audit モードとして使う前提のため、1.0.0 でも実害は小さい。待たずに入れ、リリース後に `ref` を上げる。

## Decision（決定）

`.claude/settings.json` に次を宣言し、クローンすれば同一構成で Gradle Skills が有効化候補になるようにする（[ADR-0084](0084-adopt-docker-skills-plugin.md) と同じ形）。

- `extraKnownMarketplaces.gradle-skills`: GitHub の `gradle/gradle-skills` をマーケットプレイスとして登録する。`ref` で**タグを固定**する（決定時点は `1.0.0`。現行値の出所は `.claude/settings.json`）。
- `enabledPlugins`: `"gradle-skills@gradle-skills": true`。

運用方針:

- `gradle-best-practices` は**指摘を人が選り分ける前提の単発監査**として使う。ADR-0057 の「常設の健全性ゲートは置かず、棚卸しはアドホックに行う」の、アドホック側の手段に位置づける（常設ゲートにはしない）。
- スキルの提案よりリポジトリの既存方針（単一モジュール構成・CLAUDE.md・ADR）を優先する。Apply モードで任せきりにしない。

## Consequences（結果・影響）

- Gradle ビルド構成の点検で、エージェントが公式ベストプラクティスに沿った観点を毎回同じ手順で当てられるようになる。
- **誤検知と方針衝突を引き受ける**: お試し監査では 10 件中、誤検知 1 件と構造変更の提案 2 件が出た。監査結果はそのまま反映せず、方針と照らして選り分ける。
- **ガイドラインの衝突**: wrapper の更新は Dependabot が担う方針を変えない。`gradle-wrapper-upgrade` は手で wrapper を上げる必要が生じたときの補助に留める。
- **信頼承認**: プラグインの取り込みは初回にユーザーの信頼承認を経る（無確認では走らない）。上流 README のとおり、マーケットプレイスの登録だけではプラグインは取得されず、各自が初回に `claude plugin install gradle-skills@gradle-skills` を実行する。取得元は `github.com`。
- **保守**: `ref` の更新は手動（Dependabot はこの参照を追跡しない）。1.0.1 のリリースを確認したら差分をレビューして上げる。
- 運用ルールの結論は CLAUDE.md「MCP サーバー設定」に記載（経緯は本 ADR）。
