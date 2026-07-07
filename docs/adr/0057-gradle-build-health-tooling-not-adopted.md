# 0057. Gradle build 健全性チェックの常設ツールは現時点で採用しない

- Status: Accepted
- Date: 2026-07-08
- Deciders: Matsui

## Context（背景・課題）

`build.gradle.kts` のプラグイン・依存が増えてきた（kotlin.jvm / spring / power-assert / spring-boot / dependency-management / springdoc / ktfmt / detekt / kover）。一方で **ビルドスクリプト自体の健全性**——未使用依存、宣言と利用の不整合、非推奨記法など——を機械的にチェックする仕組みは無い（[#329](https://github.com/ptiringo/toy-box/issues/329)）。detekt は Kotlin **ソースコード**の静的解析であってビルド構成の健全性は見ない。

「gradle-lint-plugin（nebula.lint）を入れるか」を起点に、Gradle build の lint / 棚卸しに何を使うかを候補比較し、Kotlin DSL 環境で実機評価した。

- **nebula.lint（`com.netflix.nebula:gradle-lint-plugin` 21.2.1）**: 適用した時点で `BuildCancelledException: Gradle Lint Plugin currently doesn't support kotlin build scripts. Please, switch to groovy build script if you want to use linting.` で失敗する。Kotlin DSL は「弱い」のではなく**ハード非対応**。本 project は `build.gradle.kts`（Kotlin DSL）を採用しているため使えない。
- **dependency-analysis（`com.autonomousapps.dependency-analysis` 3.9.0）**: Gradle 9.6 + Kotlin DSL で動作する。ただし Spring Boot アプリでは偽陽性が支配的だった。`buildHealth` は `spring-boot-starter-web` / `-actuator` / `-data-jdbc` / `-flyway` / `spring-ai-starter-mcp-server-webmvc` などの starter を「未使用」と判定する（autoconfiguration 経由の利用がソース ABI に現れないため）。加えて `spring-web` / `spring-webmvc` / `spring-context` など推移依存 約70件を「直接宣言せよ」と助言する。これは Spring Boot の「starter を宣言し、個別ライブラリは推移に委ねる」流儀と真っ向から対立する。素の状態でゲート化するのは不可能で、Spring 向けの大量の bundle / ignore 抑制設定が要り、その設定は starter を捨てる方向へ構成を歪める。
- **ben-manes versions（`com.github.ben-manes.versions`）/ version-catalog-update**: 依存・プラグインの新バージョン検知と `libs.versions.toml` 更新の半自動化。しかし本 project は **Dependabot が既に稼働**しており（Gradle エコシステムを minor/patch でグループ化、[#381](https://github.com/ptiringo/toy-box/issues/381)、`.github/dependabot.yml`）、Gradle プラグイン版も含めて更新 PR を出している。機能が重複し、足す価値が薄い。

## Decision（決定）

- **Gradle build の健全性チェックを行う常設ツール（nebula.lint / dependency-analysis / ben-manes versions / version-catalog-update）は、現時点ではいずれも採用しない**。
- 役割分担は既存の 2 本で足りると判断する: **ソースコードの静的解析は detekt**、**依存の更新追従は Dependabot**。ビルド構成の健全性（未使用依存の棚卸し等）を機械強制する常設ゲートは今は置かない。
- 単発の棚卸しが必要になったときは、`dependency-analysis` を**一時的に**適用して `buildHealth` を回し（偽陽性を人手で選り分けて）参照する、というアドホック運用に留める。常設の依存・CI ゲートには載せない。

## Consequences（結果・影響）

- **機械強制ゲートを増やさないことで、Spring Boot の starter 流儀を歪めずに済む**。dependency-analysis をゲート化していたら、starter を推移依存の直接宣言へ展開する（あるいは巨大な ignore 設定を保守し続ける）必要があり、Spring Boot のバージョン更新のたびに設定が壊れ得た。この保守負債を負わない。
- **未使用依存の常時検出は失われる**。ビルドスクリプトに未使用依存が紛れ込んでも自動では気付けない。ただし本 project は単一の本体モジュール + `:detekt-rules` の小規模構成で、依存は `libs.versions.toml` に集約され目視できる範囲にある。棚卸しが必要になれば上記アドホック運用で拾える。
- **状況が変われば再評価する**。dependency-analysis が Spring Boot の autoconfiguration 利用を第一級で扱えるようになる、モジュール分割が進んで未使用依存の混入リスクが上がる、などの変化があれば、本 ADR を supersede して再検討する。
- 結論は CLAUDE.md「コード品質チェック」に 1 行で残し、経緯（実機評価の事実）は本 ADR に置く。

## 関連

- [ADR-0011](0011-priority-via-projects-custom-field.md): 「採らない」ことも決定として記録する運用（本 ADR も不採用の記録）
- `.github/dependabot.yml` / [#381](https://github.com/ptiringo/toy-box/issues/381): 依存更新は Dependabot が担う（versions 系ツールと役割が重複する根拠）
