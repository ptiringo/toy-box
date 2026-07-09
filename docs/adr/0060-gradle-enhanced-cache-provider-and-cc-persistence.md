# 0060. setup-gradle は既定の enhanced キャッシュプロバイダを使う（configuration cache の CI 持ち越しは断念）

- Status: Accepted
- Date: 2026-07-09
- Deciders: ptiringo

## Context（背景・課題）

[ADR-0015](0015-gradle-build-performance-tuning.md) で `org.gradle.configuration-cache=true`（設定フェーズのキャッシュ、以下 CC）と `org.gradle.caching=true`（build cache）を採用した。しかし CI はエフェメラル（使い捨て runner）であり、runner 間でキャッシュを持ち越さない限り効果が出ない。

CI の `gradle/actions/setup-gradle` には `cache-provider: basic` が指定されていた。これは v6 系で既定が enhanced になった際に依存更新コミット（`f2561d8`、Copilot agent 生成）で理由の記述なく追加されたもので、ADR-0015 も事実として言及するのみで basic を選んだ根拠は残っていない。

`gradle-on-ephemeral-ci` の記事を契機に、実際の CI ログと `gradle/actions` の原文・ソースを突き合わせて次を確認した。

**basic プロバイダの問題**

- `basic` は `@actions/cache` の薄いラッパで、Gradle User Home の **`~/.gradle/caches` と `~/.gradle/wrapper` だけ**を保存・復元する。
- **restore key を使わず、キャッシュキー（Gradle ビルドファイルのハッシュ）が完全一致すると保存をスキップする**。実際に main の実行ログで `Save was skipped` を確認した。すなわちビルドファイルが変わるまで Gradle User Home のキャッシュ（ローカル build cache 含む）は更新されない。
- 公式ドキュメントの制限に `cache-cleanup` 非対応と明記されている。

**CC の持ち越しは setup-gradle 単体では実現できない**

当初は `cache-encryption-key` を与えれば CC が runner 間で持ち越せると考えたが、これは誤りだった。実測と一次資料で次を確認した。

- `setup-gradle` がキャッシュするのは Gradle User Home のみで、その内訳は `generated-gradle-jars` / `kotlin-dsl` / `scripts` / `modules-2` / `transforms-3` / `jars-9` / `build-cache-1`。CC の実体はプロジェクト配下 `.gradle/configuration-cache` にあり、この一覧に含まれない。
- `cache-encryption-key` は **必要条件だが十分条件ではない**（`setup-gradle/action.yml`: "Configuration-cache data will not be saved/restored without an encryption key being provided."）。
- CC を含む project-state キャッシュ（`ProjectCacheStatus`）は **two-tier gate（opt-in ＋ Develocity trial、次いで encryption key ＋ Gradle version）に守られた beta 機能**であり、`develocity-access-key` と `develocity-server-url` を要する（`sources/vendor/gradle-actions-caching/index.d.ts`、`sources/src/caching-report.ts` の `trial-not-licensed` 文言）。
- 実測: enhanced ＋ `cache-encryption-key` を設定した main 実行でも、保存されたエントリに `configuration-cache` パスは 1 件も無く、次の main 実行は 5 回の Gradle 起動すべてが `Calculating task graph as no cached configuration is available` だった（`Reusing configuration cache` は 0 件）。

検討した代替案:

- **`basic` を維持する**: MIT ライセンスの `actions/cache` のみに依存でき、供給網が単純。しかし上記のとおりキャッシュがほとんど更新されない。basic を積極的に選んだ経緯も存在しない。
- **Develocity を導入して CC 持ち越しを取りに行く**: 効果は大きいが、beta 機能かつ外部サービス（access key / server URL）への依存を伴う。本プロジェクトの規模に対して割に合わないため、現時点では採らない。
- **`GRADLE_RO_DEP_CACHE`（共有 read-only 依存キャッシュ）**: setup-gradle を使わない構成向けの手法で不要。
- **キャッシュ書き込みの集約（`cache-read-only`）**: setup-gradle の既定が「default ブランチでのみ保存し、他は復元のみ」であるため既に達成済み。PR 実行のログでも `cache-read-only: true` が自動設定されることを確認した。追加設定は no-op。

## Decision（決定）

- `gradle/actions/setup-gradle` の `cache-provider` 指定を**削除し、既定の enhanced を使う**。enhanced は proprietary 技術だが、GitHub Actions cache を保存先とし、**public リポジトリでは無料**である（本リポジトリは public）。
- **CC の runner 間持ち越しは行わない**。Develocity を導入しない限り不可能であり、Develocity は導入しない。`cache-encryption-key` は単体では無効なので指定しない。
- `cache-cleanup` は指定しない（enhanced の既定が `on-success` のため）。
- `cache-read-only` は指定しない（既定で達成済みのため）。
- `deploy.yml` はサプライチェーン保護のため `cache-disabled: true` を維持する。

## Consequences（結果・影響）

- CI の Gradle User Home キャッシュが毎回更新・復元されるようになる。実測（`api-tests.yml`、main）で `modules-2`（依存）・`build-cache-1`・`kotlin-dsl`（ビルドスクリプトのコンパイル結果）・`transforms` などがすべてキャッシュヒットした。basic 時代の「キー完全一致で保存スキップ」という停滞は解消した。
  - 参考値: basic の main 実行 195s に対し、enhanced の warm 実行は 136s。ただし後者は同一 SHA の再実行で build cache が全ヒットする条件のため、厳密な等価比較ではない（方向性の証拠として扱う）。
- **CC は CI では効かない**。`org.gradle.configuration-cache=true` の恩恵はローカル（デーモン常駐）に限られる。設定フェーズの時間は CI では毎回発生する。ADR-0015 が想定していた「CI でも CC が効く」は成り立たない。
- **proprietary な gradle-actions-caching に依存する**。public リポジトリでは無料だが、private 化する場合は Free Preview の条件を確認する必要がある。ライセンス純度（MIT）を優先するなら basic へ戻す判断もありうるが、その場合はキャッシュ更新の停滞を受け入れることになる。
- 将来 Develocity を導入する場合は、`develocity-access-key` / `develocity-server-url` と `cache-encryption-key` を揃えれば project-state キャッシュ（build-logic ＋ CC）が有効化しうる。beta 機能である点に注意。
- ADR-0015 の「CI は `cache-provider: basic` が Gradle User Home を持ち越す」という記述は本 ADR で置き換わる（ADR-0015 の他の決定は有効）。

## 補足: 本 ADR 内での訂正（2026-07-09）

初版では「`cache-encryption-key` を指定すれば CC が runner 間で持ち越される」と記述し、その前提で 6 ワークフローに `cache-encryption-key` と `cache-cleanup` を追加した（PR #594・#598）。その後の実測で CC が一切キャッシュされていないことが判明し、原因が Develocity ゲートであることを一次資料で確認したため、当該 2 オプションを削除し本 ADR を訂正した。決定（enhanced プロバイダの採用）自体は変わっていない。
