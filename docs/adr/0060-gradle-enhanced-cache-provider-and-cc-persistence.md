# 0060. setup-gradle は既定の enhanced キャッシュプロバイダを使い configuration cache を CI で持ち越す

- Status: Accepted
- Date: 2026-07-09
- Deciders: ptiringo

## Context（背景・課題）

[ADR-0015](0015-gradle-build-performance-tuning.md) で `org.gradle.configuration-cache=true`（設定フェーズのキャッシュ、以下 CC）と `org.gradle.caching=true`（build cache）を採用した。しかし CI はエフェメラル（使い捨て runner）であり、runner 間でキャッシュを持ち越さない限り効果が出ない。

CI の `gradle/actions/setup-gradle` には `cache-provider: basic` が指定されていた。これは v6 系で既定が enhanced になった際に依存更新コミット（`f2561d8`、Copilot agent 生成）で理由の記述なく追加されたもので、ADR-0015 も事実として言及するのみで basic を選んだ根拠は残っていない。

`gradle-on-ephemeral-ci` の記事を契機に実測・原文確認したところ、以下が判明した。

- `basic` は `@actions/cache` の薄いラッパで、Gradle User Home の **`~/.gradle/caches` と `~/.gradle/wrapper` の 2 つだけ**を保存・復元する。CC のデータはプロジェクト配下 `.gradle/configuration-cache` にあるため**保存対象外**であり、CC は runner 間で持ち越されない。
- `basic` は公式ドキュメントの制限に **`cache-cleanup` 非対応**と明記されている。
- `basic` は restore key を使わず、キャッシュキー（Gradle ビルドファイルのハッシュ）が完全一致すると保存をスキップする。実際に main の実行ログで `Save was skipped` を確認した。すなわち **ビルドファイルが変わるまで Gradle User Home のキャッシュ（ローカル build cache 含む）は更新されない**。
- `cache-encryption-key` は「GitHub Actions cache に保存する CC データを暗号化する」ための入力であり、CC を保存する enhanced と組み合わせて初めて機能する。`basic` の下では `GRADLE_ENCRYPTION_KEY` を env に出すだけで、跨ランの再利用は起きない。

検討した代替案:

- **`basic` を維持する**: MIT ライセンスの `actions/cache` のみに依存でき、供給網が単純。しかし ADR-0015 で採用した CC の効果が CI で得られず、build cache も上記のとおり更新が滞る。basic を積極的に選んだ経緯も存在しない。
- **`GRADLE_RO_DEP_CACHE`（共有 read-only 依存キャッシュ）**: setup-gradle を使わない構成向けの手法で、本プロジェクトには不要。
- **キャッシュ書き込みの集約（`cache-read-only`）**: setup-gradle の既定が「default ブランチでのみ保存し、他は復元のみ」であるため既に達成済み。追加設定は no-op。

## Decision（決定）

- `gradle/actions/setup-gradle` の `cache-provider` 指定を**削除し、既定の enhanced を使う**。enhanced は proprietary 技術だが、GitHub Actions cache を保存先とし、**public リポジトリでは無料**である（本リポジトリは public）。
- CC データを runner 間で持ち越すため、キャッシュ有効な全ワークフローに `cache-encryption-key: ${{ secrets.GRADLE_ENCRYPTION_KEY }}` を指定する。鍵は repo secret を唯一の出所とし（全ジョブで同一鍵が必要）、`openssl rand -base64 16` で生成する。
- キャッシュエントリを軽量に保つため `cache-cleanup: on-success` を指定する。
- 対象は `api-tests` / `codeql` / `db-doc-check` / `e2e-tests` / `openapi-lint` / `container-smoke-test`。`deploy.yml` はサプライチェーン保護のため `cache-disabled: true` を維持し対象外。`copilot-setup-steps.yml` はエージェント環境のキャッシュ warm が役目のため provider の既定化のみ行い、`cache-encryption-key` は渡さない（Copilot 環境での secret 可用性に依存させない）。
- `cache-read-only` は設定しない（既定で達成済みのため）。

## Consequences（結果・影響）

- CI で設定フェーズ（タスクグラフ構築）がスキップされ、エフェメラル runner でも CC の効果が得られる。enhanced は restore key と重複排除を持つため、build cache のヒット率も basic より改善する見込み。
- **proprietary な gradle-actions-caching に依存する**。public リポジトリでは無料だが、private 化する場合は Free Preview の条件を確認する必要がある。ライセンス純度（MIT）を優先するなら basic へ戻す判断もありうる。
- **repo secret `GRADLE_ENCRYPTION_KEY` が前提**になる。未登録・空文字の場合は CC が持ち越されないだけで、ビルドは壊れない（graceful degradation）。鍵をローテーションすると既存の CC エントリは復号できず捨てられる（再構築されるだけで無害）。
- ADR-0015 の「CI は `cache-provider: basic` が Gradle User Home を持ち越す」という記述は本 ADR で置き換わる（ADR-0015 の他の決定は有効）。
- 効果の実測（設定フェーズ時間の before/after）は、本決定のマージ後に main でキャッシュが seed され、次の実行で `Reusing configuration cache.` が出ることを確認して行う。
