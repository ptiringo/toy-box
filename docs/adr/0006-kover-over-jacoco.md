# 0006. カバレッジ計測に Kover を採用し、成熟領域のみゲートする

- Status: Accepted
- Date: 2026-06-16
- Deciders: Matsui

## Context（背景・課題）

テスト戦略を明文化するにあたり、カバレッジを機械的に計測し回帰を検出するハーネスを用意したくなった。検討した論点は 2 つ。

### ツール選定: Kover か JaCoCo か

JVM のカバレッジでは JaCoCo が定番だが、本プロジェクトは型安全な ID のために `@JvmInline value class`（`JockeyId` / `BloodHorseId` 等）を多用している（CLAUDE.md「Value Object パターン」参照）。

- **JaCoCo は Kotlin の合成バイトコード（inline class のボクシング/アンボクシング、`Companion`、data class 生成メンバ等）をそのまま数える**ため、inline class を多用するコードでは「書いていない行」が未到達としてカウントされ、数値が実態とずれる。除外設定で抑えることはできるが、value class が増えるたびに調整が要る。
- [Kover](https://github.com/Kotlin/kotlinx-kover) は JetBrains 製で Kotlin コンパイラの事情を理解しており、こうした合成要素を既定で適切に扱う。Gradle プラグインで version catalog 管理でき、HTML / XML レポートと検証ルールを標準で持つ。

inline class が設計の中核にある以上、Kotlin ネイティブの Kover が素直と判断した。

### ゲートの単位: 全体一律か領域ごとか

このリポジトリは sandbox であり、成熟度に温度差がある。`horseracing` の血統・騎手まわりはレイヤーごとにテストが揃っている一方、`tennis` / `sakamichi` や `breeding` / `race` 等は TODO を含む探索段階のモデルで、テストがほぼ無い。

全体に一律の閾値を課すと、探索コードを書くたびにゲートが赤くなり、カバレッジ計測がノイズ源になる。かといってゲートを置かないと、成熟領域の回帰を見逃す。

## Decision（決定）

- カバレッジ計測ツールに **Kover** を採用する（JaCoCo は使わない）。
- レポートを 2 つの variant に分ける:
  - **`total`**: エントリーポイントのみ除外した全体。穴の可視化が目的で、絞り込まない。
  - **`mature`**: `copyVariant` で `total` を複製し、includes フィルタで**成熟パッケージだけ**に絞った検証ゲート。
- ゲートは**成熟パッケージのみ**に行カバレッジ下限を課す。探索段階のモデルは `total` には出すがゲート対象から外す。
- 下限は**ラチェット運用**とする。現状実測（行 88.3%）を少し下げた **85%** でロックし、上がったら下限も引き上げて後戻りを防ぐ。下げるのは設計判断を伴うときのみ。
- ゲート（`koverVerifyMature`）を `check` に組み込み、CI（`api-tests.yml`）でも検証する。PR の Job Summary には `koverLog` の数値を出す（外部サービスは使わない）。

設定の実体は `build.gradle.kts` の `kover {}` ブロック、運用ルールは [.claude/rules/testing.md](../../.claude/rules/testing.md) に置く。

## Consequences（結果・影響）

- inline class を増やしてもカバレッジ数値が実態とずれにくく、除外設定のメンテが不要になった。
- 探索コードを書いてもゲートが赤くならず、成熟領域の回帰だけを捕まえられる。可視化（`total`）と強制（`mature`）を分離した分、`build.gradle.kts` の設定はやや込み入る（Kover 0.9 の検証ルールがパッケージ単位フィルタを持たないため `copyVariant` で迂回している）。
- ゲート対象は `variant("mature")` の includes が唯一の出所。**領域が成熟したら includes に追加して昇格させる**運用が必要。追加を忘れると、テストを書いてもゲートに反映されない点に注意。
- 探索領域に残るカバレッジの穴（`infrastructure` の契約テスト、`ConfirmRaceResult` / `RegisterForBreeding` 等）は当面の宿題として testing.md に記載した。
