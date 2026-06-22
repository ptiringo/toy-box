# 0015. Gradle ビルド性能チューニングの採否を実測で決める

- Status: Accepted
- Date: 2026-06-22
- Deciders: ptiringo

## Context（背景・課題）

ビルド・テストの待ち時間を縮めたい（#349）。Gradle 公式パフォーマンスガイドの各レバーのうち、本プロジェクト（単一本体モジュール + 極小の `:detekt-rules`、Kotlin + Spring Boot、JDK 21、Gradle 9.5.1）で実際に効くものを実測で見極めて採用する。

計測は 8 論理コア環境で、`--build-cache` / `--configuration-cache` / `--daemon` のフラグでレバーを個別に切り替えて before/after を取得した（後述「計測環境の制約」のとおり、ローカルのグローバル設定がキャッシュ機構を無効化しているため、フラグで実環境を模した）。

検討したレバーと実測:

| レバー | 計測 | 判断 |
|---|---|---|
| build cache | clean test 87s→2s / clean check 32.7s→4s（フルヒット） | 採用 |
| configuration cache（既存有効） | no-op test 14s→1.5s（daemon 併用） | 維持 + 健全性ゲート追加 |
| JVM ヒープ（`-Xmx2g`） | 現規模では実測差ノイズ範囲 | 採用（将来備え） |
| `org.gradle.parallel` | assemble 6.0s vs 6.1s（差なし） | 見送り |
| `maxParallelForks`（テスト並列フォーク） | forks=1/2/4 で 42s→61s→97s（逆効果） | 見送り |

`maxParallelForks` が逆効果になるのは、Spring の `ApplicationContext` キャッシュが JVM 単位であるため。テスト JVM をフォークすると重い `@SpringBootTest` のコンテキスト初期化が各フォークで重複し、JVM 起動コストも加わって、単一モジュール・小規模スイートでは並列化の利得を上回る。公式ガイドが一般論として勧める「テストフォークの並列化」が、本プロジェクトの規模・構成では成立しないことを実証した。

`org.gradle.parallel` が無効果なのは、プロジェクトが root + 極小の `:detekt-rules` の2つしかなく、並列プロジェクト実行で重ねられる仕事が無いため。

## Decision（決定）

`gradle.properties` に以下を設定する:

- `org.gradle.caching=true` — build cache を有効化（最大の効果）。
- `org.gradle.configuration-cache.problems=fail` — configuration cache 非互換の混入を警告で素通りさせず失敗させる健全性ゲート（CC 自体は従来どおり有効）。
- `org.gradle.jvmargs=-Xmx2g -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8` — デーモンヒープを明示（既定 512MB の GC 圧予防。現規模では効果小だが将来備え）。

採用しないものは、理由（実測値）を `gradle.properties` / `build.gradle.kts` のコメントに残す:

- `org.gradle.parallel` は設定しない（効果なし）。マルチモジュール化時に再評価。
- `maxParallelForks` は既定（1）のまま据え置く（逆効果）。テスト規模がフォーク間でコンテキスト起動を償却できる水準になったら再評価。

CI（`api-tests.yml`）は `gradle/actions/setup-gradle`（`cache-provider: basic`）が Gradle User Home（ローカル build cache の `~/.gradle/caches/build-cache-1` を含む）を runner 間で持ち越すため、`org.gradle.caching=true` の有効化だけで build cache が CI でも効く。ワークフロー自体の変更は根拠コメントの追記に留める。

## Consequences（結果・影響）

- ローカル（非サンドボックス）・CI のクリーン／インクリメンタルビルドが、キャッシュヒット時に大幅に短縮される。`koverVerifyMature` のカバレッジゲートは test が FROM-CACHE でも正常に通過することを確認済み。
- `problems=fail` により、今後 configuration cache 非互換な記述（設定フェーズでの非決定的な値参照など）を加えるとビルドが失敗する。CC 互換性を保つ規律を強制できる一方、CC 非互換な記述を入れたいときは明示的な対処が要る。
- **計測環境の制約**: ローカル開発環境の `~/.gradle/gradle.properties` がサンドボックス安定化のため VFS watch / configuration cache / daemon / build cache をすべて無効化している（グローバルがプロジェクト設定を上書きする）。このため本決定の施策は **CI および非サンドボックスのローカル開発でのみ効く**（サンドボックス内ビルドでは無効化されたまま）。このグローバル無効化が過剰でないか（VFS watch のみで stale を防げないか等）の評価は本 ADR のスコープ外とし、別途検討する。
- スコープ外: dependency locking / repository 整理（現状の単一 `mavenCentral()` で問題なし）、Develocity（リモート build cache / Build Scan）は別途検討。
