# 0019. コンパイラ警告をエラー化して警告ゼロ運用を強制する

- Status: Accepted
- Date: 2026-06-22
- Deciders: ptiringo

## Context（背景・課題）

本プロジェクトは detekt（静的解析・カスタムルール）/ ArchUnit（アーキテクチャ規約）/ Kover（カバレッジ）/ ktfmt（整形）と、品質を機械強制する仕組みを重ねてきた。一方で **Kotlin コンパイラ自身が出す警告（deprecation、型安全性、冗長な記述など）はゲートされておらず**、ビルドログに出るだけで放置できる状態だった。警告は「将来エラーになる予兆」や「設計の綻び」を含むため、混入を検知・抑止したい。

検討時点で main / test / `:detekt-rules` の全モジュールを clean からフルコンパイルしたところ、**コンパイラ警告は 0 件**だった。つまり「今ゼロのものをゼロのまま維持する」ことが論点であり、導入コスト（既存警告の潰し込み）は発生しない。

扱い方として次を比較した:

- **却下: Kover 型の件数ラチェット**（警告数のベースラインを記録し、増加したら CI を落とす）。カバレッジは「100% が現実的でない」ため 85% のような恣意的な閾値が要り、だからこそ Kover はラチェット運用（[ADR-0006](0006-kover-over-jacoco.md)）にした。しかし**警告は「ゼロ維持」が自然な閾値**であり、件数を記録・比較する機構を自作するのは過剰（オーバーエンジニアリング）。
- **却下: 可視化のみ**（PR の Job Summary に警告件数を出すだけ）。検知はできても抑止できず、放置を許す。
- **採用: `allWarningsAsErrors`**（警告をコンパイルエラーに昇格させる）。現状ゼロなので有効化してもビルドは壊れず、以後の混入を即座にビルドで止められる。detekt / ArchUnit / kover と同列の機械強制ゲートに収まる。個別に許容したい警告は `@Suppress` または Kotlin 2.2+ の `-Xwarning-level=<ID>:warning` で逃がせるエスケープハッチがある。

## Decision（決定）

両モジュール（root `api` / `:detekt-rules`）の Kotlin `compilerOptions` に **`allWarningsAsErrors = true`** を設定し、コンパイラ警告が 1 件でもあればビルドを失敗させる。

- 設定は各モジュールの `build.gradle.kts` に明示的に書く（`allprojects` で root に他モジュールの設定を寄せず、局所性を保つ）。
- CI（`api-tests.yml`）は ktfmt の後に**専用のコンパイルステップ**（`compileKotlin compileTestKotlin :detekt-rules:compileKotlin :detekt-rules:compileTestKotlin`）を置き、警告で落ちたときに原因ステップが明確になるようにする（detekt / test 内のコンパイルでも警告ゲートは効くが、専用ステップで可視性を上げる）。
- 個別に正当な理由で許容する警告は、対象に `@Suppress("…")` を付けるか、`-Xwarning-level=<診断 ID>:warning` で全体のレベルを下げて逃がす。

## Consequences（結果・影響）

- **得たもの**: コンパイラ警告の混入が、レビュー前にビルド（ローカル・CI 双方）で止まる。deprecation などの将来の破壊的変更の予兆を早期に検知できる。品質ゲートが detekt / ArchUnit / kover / ktfmt と揃い、「警告ゼロ」がリポジトリの不変条件になる。
- **引き受けたもの**: Kotlin / 依存ライブラリのバージョン更新で**新しい deprecation 警告が出るとビルドが赤くなる**。これは検知したい事象だが、更新作業時に対応（移行 or `@Suppress` での一時退避）が必要になる。
- **注意（K2 の挙動）**: Kotlin K2 コンパイラでは**未使用ローカル変数（`val x = 1` を使わない等）は警告化されない**。ルールが実際に効くことの検証（ミューテーション）は、未使用変数ではなく `@Deprecated` 関数の呼び出しなど確実に警告が出るコードで行うこと。本決定の導入時も deprecated 呼び出しを一時挿入し、`e: warnings found and -Werror specified` で BUILD FAILED することを確認している。
- 結論（守るべきルール）は `.claude/rules/architecture.md` の「その他の強制ルール」に 1 項として残す。
