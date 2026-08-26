# 0055. 差分カバレッジ（patch coverage）を diff-cover で 90% ハードゲートする

- Status: Accepted
- Date: 2026-07-07
- Deciders: Matsui

> 注: 本 ADR が決めた差分ゲートの閾値（`--fail-under 90`、**現行値の出所は `.github/workflows/api-tests.yml`**）は決定当時から不変。一方、本文が引用する集約ゲート（`koverVerifyMature`）の **LINE 90% / BRANCH 80% は [ADR-0040](0040-coverage-gate-operation-model.md) 決定時点の値**で、その後 [#735](https://github.com/ptiringo/toy-box/issues/735) で引き上げられた（**現行値の出所は `build.gradle.kts`**、要約は `.claude/rules/testing.md`）。決定 2 が書いた「`koverVerifyMature` の LINE 下限に揃えた」は **決定当時の事実の記述であって追従の約束ではない**と [#843](https://github.com/ptiringo/toy-box/issues/843) で確定した（2026-08-26。理由は決定 2 の追記を参照）。

## Context（背景・課題）

[ADR-0040](0040-coverage-gate-operation-model.md) は「成熟領域全体の絶対水準」を守る集約ゲート（`koverVerifyMature`、LINE 90% / BRANCH 80%）を確立した一方、差分カバレッジ（patch coverage: PR で変更した行のカバレッジ）は別概念として #437 に委譲していた。

絶対水準の集約ゲートには次の弱点がある。

### 母集団の希釈で回帰を見逃す

`mature` variant は成熟領域全体を母集団とするため、既存のテスト済みコードが厚いほど、新規追加した数十行が未テストでも全体比率への影響は小さい。極端な場合、新規コードのカバレッジが 0% でも全体の LINE 90% / BRANCH 80% を割らずに通過しうる。母集団が大きいほど「新規変更コードのテスト漏れ」を検出する感度が下がる。

### 変更行そのものを直接検証する仕組みが無い

PR レビューで「この差分にテストが付いているか」を目視確認する運用は漏れが起きやすい。機械的に変更行のカバレッジを算出し閾値で強制するゲートが必要。

## Decision（決定）

### 1. diff-cover を採用する

[diff-cover](https://github.com/Bachmann1234/diff-cover)（pipx 経由、バージョン 10.3.0、mise 管理）で、`git diff` の変更行と Kover の XML カバレッジレポートを突き合わせ、変更行だけのカバレッジ率を算出する。

### 2. `--fail-under 90` のハードゲートとする

変更行カバレッジが 90% 未満なら diff-cover が非ゼロ終了し、CI ジョブを失敗させる。閾値は決定当時の `koverVerifyMature` の LINE 下限（当時 90%）と同値に置いた。

#### 2-1. この同値は追従の約束ではない（[#843](https://github.com/ptiringo/toy-box/issues/843) で 2026-08-26 に確定）

差分ゲートの閾値は集約ゲートの手動ラチェット（[ADR-0040](0040-coverage-gate-operation-model.md)）に**連動させず、独立に決める**。集約ゲートの下限を引き上げても `--fail-under` は動かさない。理由は 3 つ。

- **守る対象が違う**: 集約ゲートは「成熟領域全体の絶対水準を下げない」ための機構で、下限は実測直下のキリ番に固定する。差分ゲートは「新規・変更行にテストを添えさせる」ための下限であり、守るのは水準ではなく習慣である。
- **ラチェットの論理が移植できない**: 集約ゲートは母集団が数千行あり 1 行の増減が 0.1pt 未満しか動かないため「実測直下に固定」が意味を持つ。差分ゲートの母集団は PR ごとに数行〜百数十行で、1 行未カバーの重みが 1pt から数十 pt まで跳ねる。実測に張り付かせても、次の PR の実測は母集団ごと別物になる。
- **離散性で誤検知が増える**: 閾値を集約ゲートの LINE 下限へ揃えると（判断時点では 95%）、差分 20 行未満の PR は 1 行未カバーで即失敗する。差分カバレッジは #687 で観測したとおり ktfmt の整形結果（ラムダを単独行へ折るか）でも上下するため、この既知のノイズに対する余裕が実質ゼロになる。

判断時点（2026-08-26）の実測: diff-cover 導入後に `src/main/kotlin` を変更したマージ 7 件の PR ジョブは、差分 2〜109 行に対していずれも 97〜100%。閾値 90% を割った実例は #687 の 89%（整形起因。1 行に収め直して 98% へ回復）1 件だけで、これは追従させていても同じく落ちていた。つまり**追従は「落ちる PR を増やす」方向にしか効かず、見逃していた回帰は観測されていない**。

### 3. 入力は `mature` variant の XML とし、集約ゲートを補完する関係にする

diff-cover に食わせる XML は `koverVerifyMature` と同じ `mature` variant（`build/reports/kover/reportMature.xml`、`excludes` 適用済み）から生成する（`koverXmlReportMature` タスクを新設、`onCheck = false` で `check` には載せない）。

これにより、探索領域（denylist、`variant("mature")` の `excludes`）の変更行は分母に入らない。denylist の出所は `build.gradle.kts` の一箇所に保ったまま、差分ゲートも同じ除外を自動で継承する。

**集約ゲート（絶対水準・成熟領域全体）と差分ゲート（変更行・新規コード）は二重に課す補完関係**であり、どちらかを置き換えるものではない。

### 4. CI 配線は `api-tests.yml` の PR ジョブに相乗りする

差分ゲートは base ブランチとの差分が前提のため、`pull_request` イベント限定（`github.event_name == 'pull_request'`）で実行し、`push`（main への直 push）では走らせない。既存の `test` ジョブに以下のステップを追加する形で実装する。

- `./gradlew koverXmlReportMature` で `mature` variant の XML を生成
- `mise exec -- diff-cover build/reports/kover/reportMature.xml --compare-branch "origin/$BASE_REF" --src-roots src/main/kotlin --fail-under 90 --markdown-report diff-cover.md`
- 生成した Markdown レポートを Job Summary に追記（外部サービス不使用、既存の `koverLog` 出力と同じ流儀）

新規の外部ジョブ・別ワークフローは設けない。

## Consequences（結果・影響）

- **新規変更コードのテスト漏れを即検出**: 母集団の希釈を受けず、PR ごとの変更行だけを直接評価する。
- **二重ゲート**: 既存の `koverVerifyMature`（絶対水準）に加えて差分ゲートが必ず走る。両方を満たさないと PR は通らない。
- **CI に Python 依存が増える**: diff-cover は Python 製で `pipx` 経由の配布となる。mise の `MISE_ENV: ci` で CI ランナーにも同一バージョンを供給するため、ローカルと CI の差異は生じない。
- **`--src-roots src/main/kotlin` の指定が必須**: 省略すると diff-cover がソースパスを解決できずレポートが空になる（実装時に確認済み）。
- **main push では走らない**: 差分ゲートは PR の変更行という概念に依存するため、main への直接 push（通常は発生しない運用だが）には効かない。集約ゲート（`koverVerifyMature`）は push でも走るため、最終的な安全網は保たれる。
- **ADR-0040 は不変**: 絶対水準の集約ゲートの設計・閾値は変更しない。本 ADR は #437 で委譲されていた差分カバレッジのみを新たに決定する。
- **2 つの閾値は別々に動かす**（決定 2-1）: 集約ゲートの手動ラチェットで LINE / BRANCH の下限を引き上げるとき、`--fail-under` は触らない。差分ゲートの閾値を見直すのは、差分カバレッジ自身の実測（PR ジョブの diff-cover 出力）が根拠になったときに限る。運用手順は `.claude/rules/testing.md`。

## 関連

- [ADR-0040](0040-coverage-gate-operation-model.md): 集約ゲート（絶対水準・LINE / BRANCH の 2 ボーンド。現行値の出所は `build.gradle.kts`）の運用モデル。本 ADR が差分カバレッジを委譲された先
- [ADR-0006](0006-kover-over-jacoco.md): Kover 採用の前提（本 ADR が入力とする XML レポートの生成元）
- #437: 差分カバレッジ（patch coverage）の検討 Issue（本 ADR の決定対象）
- #412: ADR-0040 に対応する実装 Issue（`mature` variant の excludes 反転を実装）
