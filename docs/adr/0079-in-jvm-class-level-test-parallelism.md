# 0079. テストを JVM 内のクラス間並列で走らせる（DB / WebMvc は逐次のまま）

- Status: Accepted
- Date: 2026-08-25
- Deciders: Matsui

## Context（背景・課題）

テスト並列化は [ADR-0015](0015-gradle-build-performance-tuning.md) の時点で「採らない」と決め、`.claude/rules/testing.md` は再評価の条件を「#338 でテスト隔離を整えてから」と書いていた。しかし #338 はクローズ済みで受け皿が無く、トリガーが宙に浮いていた（#690 がその受け皿として立った）。

不採用の根拠は 3 つあった。いずれも #690 で実測し直した。

1. **フォーク（`maxParallelForks`）は逆効果** — ADR-0015 当時 forks=1/2/4 で 42s/61s/97s と単調悪化。Spring のコンテキストキャッシュが JVM 単位のため、フォークすると 16 個のコンテキスト構築が各 JVM で重複する。**この結論は今回も維持する**（今回フォークは実測していない）。
2. **JVM 内並列は `@MockBean` / `@MockkBean` を使うテストを Spring 公式が非推奨としている** — これは `@WebMvcTest` スライスに限った話で、スイート全体の制約ではなかった。
3. **全テーブル TRUNCATE（[ADR-0070](0070-db-test-cleanup-via-truncate-not-transactional.md)）が並行実行と両立しない** — これも DB を触るテストに限った話だった。

つまり 2 と 3 は「並列にしてはいけないテストが 2 種類ある」という制約であって、「並列にできない」ではない。JUnit 5 の `@Execution(SAME_THREAD)` でその 2 種類だけを逐次側へ閉じれば、残りは並列にできる。

#690 のフェーズ1 はこの部分適用について「素直に並列化できるのは Unit 群 25% だけで、利得上限は -19%」と見積もっていた。加えて [ADR-0077](0077-consolidate-web-test-contexts-trading-jwt-decoder-assurance.md)（#817）でコンテキストを 18 → 16 に畳んで -20.5% を取った結果、比較の基準線が下がり利得はさらに薄くなるはずだった。**推論ではこの時点で「採らない」に傾いていたが、実際に回して測ったところ逆の結果が出た。**

### 実測

同一コミットで環境変数によって並列を切り替え、baseline（並列なし）と parallel を比較した。

**CI（ubuntu-latest / 4 コア・各 8 回）**

| | baseline | parallel |
|---|---:|---:|
| `:test` 壁時計 中央値 | 74.0s | **65.5s（-11.5%）** |
| 平均 | 72.2s | 62.9s（-13.0%） |
| 分布 | 60–83s | 49–70s |

Mann-Whitney の並べ替え検定（全 12870 通り）で片側 **p = 0.030**。

**ローカル（macOS / 8 論理コア・16GB、baseline と parallel を交互に 8 ペア）**

| | baseline | parallel |
|---|---:|---:|
| 中央値（8 ペア） | 79.0s | **68.5s（-13.3%）** |
| 中央値（外れ値と初回を除く 6 ペア） | 77.5s | **67.0s（-13.5%）** |

8 ペア中 7 ペアで parallel が速く、符号検定で片側 p = 0.035（外れ値と初回を除く 6 ペアでは 6/6・p = 0.016）。

**付随して確認したこと**

- **コンテキストキャッシュは並列でも劣化しない** — baseline / parallel とも `size = 16 / hitCount = 4860 / missCount = 16 / failureCount = 0` で完全に一致した。並列によるコンテキストの重複構築は起きていない。
- **テストは壊れない** — CI 28 ジョブ + ローカル 8 実行のすべてが success。
- **個々のクラスは約 2 倍に膨らむ** — per-class time の合計は 63.3s → 124.3s になる。それでも壁時計が縮むのは実行が重なるため（per-class 合計 / 壁時計 = baseline 0.86 → parallel 1.90）。

### 測定方法についての注意（この数字を再現・更新するときのために）

**各 3 回では判定できなかった。** 最初の計測は各 3 回で parallel が **+6.1% 遅い**という結果になり、2 回目の各 3 回では **-14.1% 速い**と符号が反転した。各 8 回へ増やして初めて安定した。ADR-0077 の「CI 3 回で測る」という手順は、-20.5% のような大きい効果には足りるが、**10% 前後の効果には足りない**。

### `@AnalyzeClasses` の規約テストは並列化されない

ArchUnit の `@AnalyzeClasses` は ArchUnit 独自の TestEngine で動くため、`junit.jupiter.execution.parallel.*`（JUnit Jupiter engine の設定）の対象外になる。実測でも `OnionLayerRulesTest` は 0.52s → 0.80s、`ControllerContractRulesTest` は 0.03s → 0.04s とほぼ動かない。一方、自前で `ClassFileImporter` を回すメタテスト（`ControllerPackageLayoutRuleTest` / `DtoDomainEnumRuleTest` 等）は Jupiter のテストなので並列化され、クラスパス走査が重なって 2.53s → 12.40s のように膨らむ。

フェーズ1 が「Unit 群 25% が並列対象」と見積もった中身は、実際にはこれより狭い。それでも壁時計が縮んだのは、Unit 群が DB 群・WebMvc 群の実行時間に重なって隠れるためで、**利得の出どころは「並列化した群が速くなること」ではなく「逐次側の裏で他が終わること」**にある。

### 採らなかった代替案

- **`maxParallelForks`（プロセス並列）** — 上記 1 のとおり。コンテキスト構築の重複が JVM 数に比例するため、コンテキストが 16 個ある現状で有利になる理由が無い。
- **スレッドごとにスキーマを分ける / フォークごとにコンテナを分ける** — DB 群も並列化する案。TRUNCATE 方式（ADR-0070）と Testcontainers の構成を作り直す必要があるのに対し、DB 群を逐次に閉じたままでも -11.5% が取れたため、複雑さに見合わない。
- **クラス内のメソッドも並列にする（`mode.default=concurrent`）** — 既存テストがメソッド間の実行順や共有フィクスチャに依存していないことを一件ずつ確かめる必要がある。クラス間並列だけで効果が出ているため踏み込まない。

## Decision（決定）

**JUnit 5 の JVM 内クラス間並列を有効にする。** `build.gradle.kts` の `tasks.withType<Test>` に次を置く。

- `junit.jupiter.execution.parallel.enabled = true`
- `junit.jupiter.execution.parallel.mode.default = same_thread`（クラス内のメソッドは逐次のまま）
- `junit.jupiter.execution.parallel.mode.classes.default = concurrent`（クラス間だけ並列）

並列度は JUnit 既定の `dynamic` 戦略（利用可能プロセッサ数）に任せ、固定しない。

**並列にしてはいけない 2 種類は `@Execution(SAME_THREAD)` で閉じる。**

| 対象 | 理由 | 強制のしかた |
|---|---|---|
| DB を触るテスト | 全テーブル TRUNCATE（ADR-0070）が並行実行と両立しない | `PostgresContainerSupport` のクラス注釈。`@Execution` は `@Inherited` なので継承先すべてに効く |
| `@WebMvcTest` スライス | `@MockkBean`（`@MockBean` 機構）が Spring 公式「Parallel Test Execution」の非推奨条件 | クラスごとに付与し、`TestParallelismRulesTest.webMvcTestsRunInSameThread` が機械強制する |

**`maxParallelForks` は既定（1）のまま据え置く**（ADR-0015 の結論を維持）。

## Consequences（結果・影響）

### 得たもの

- `:test` が CI で -11.5%、ローカルで -13.3%。ADR-0077 の -20.5% に続く短縮で、両者は独立に効く（コンテキスト削減は構築コストを、並列化は実行の重なりを削る）。
- ADR-0070 の TRUNCATE 方式にも Testcontainers の共有コンテナ構成にも手を入れずに済んだ。

### 引き受けたもの

- **`@WebMvcTest` を足すときは `@Execution(SAME_THREAD)` が要る。** 付け忘れても例外にはならず、Spring の非推奨条件のまま静かに走って後から不安定さとして出る。これを防ぐため `TestParallelismRulesTest` で機械強制し、注釈の有無だけでなく**値が `SAME_THREAD` であること**まで検査する（`CONCURRENT` の明示も付け忘れと同じ結果になるため）。違反サンプル 2 種を `architecture/fixture/` に恒久的に置き、ルールが実際に噛むことを回帰テストで担保する（`.claude/rules/gates.md`）。
- **並列設定自体が消えても全テストは緑のまま通り、速度が静かに戻るだけになる。** これを検出するため `ParallelExecutionProbeTest` を置く。2 クラスが互いの到達を待ち合わせ、逐次実行なら必ずタイムアウトして落ちる。壁時計を測る方式と違い測定ノイズに左右されない。
- **テストの隠れた共有状態が露出しうる。** 今回の実測では 36 回の実行すべてが success だったが、将来テストを足したときに並列固有の不安定さが出る可能性は残る。その場合は当該クラスに `@Execution(SAME_THREAD)` を付けて逃がしたうえで、共有状態そのものを設計で断つ。
- **外れ値は解消しない。** ローカル 8 ペア中 1 回、parallel 側で 506s（通常 66s の 7.7 倍）を観測した。ただし同じペアの baseline も 120s（通常 77s）と遅く、#818 が観測した同型の外れ値は baseline（並列なし）で起きている。並列固有のリスクとは言えないが、**並列化しても消えない**（原因は #818 が引き取る）。

### 効果を測り直すときの手順

`.claude/rules/testing.md` に手順を残す。要点は、**各 3 回では足りず各 8 回が要る**こと、ローカルでも交互に回せば測れること（ADR-0077 の「ローカルは計測に使えない」は 20% 規模の効果を 3 回で測る前提の話で、8 ペアの交互比較なら 7/8 で符号が揃った）。
