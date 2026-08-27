# 0080. Docker 到達性ガードを Gradle の Test タスクへ広げる

- Status: Accepted
- Date: 2026-08-26
- Deciders: Matsui

## Context（背景・課題）

Issue #847。[ADR-0071](0071-pre-push-docker-fail-fast-guard.md) で導入した Docker 到達性の fail fast
ガードは **lefthook の pre-push からしか呼ばれない**。開発中に手で `./gradlew test` / `./gradlew check`
を叩く経路（`.claude/rules/testing.md` が案内している通常の実行方法）は素通りする。

Docker が落ちた状態でこの経路を通ると、**原因は 1 つなのに 172 件のテスト失敗**という形で落ちる。
`#690` の作業中に実測した内訳は次のとおり。

| 例外 | 件数 | 位置づけ |
|---|---:|---|
| `org.junit.jupiter.api.extension.ParameterResolutionException` | 121 | 連鎖 |
| `java.lang.NoClassDefFoundError` | 50 | 連鎖 |
| `java.lang.ExceptionInInitializerError` | **1** | **真因** |

共有コンテナを起動する `PostgresContainerSupport` の `companion`（静的初期化）が
`Could not find a valid Docker environment.` で失敗し、以降その基底クラスに触る全テストが
`NoClassDefFoundError` を投げ続ける。真因が失敗一覧の先頭に出るとは限らない（実行順に埋もれる）ため、
このとき並列化スパイク（#690）の検証中だったこともあり「並列化でテストが壊れた」と誤診しかけた。
切り分けには `ExceptionInInitializerError` を JUnit の XML から抽出する手間がかかった。

失敗の出方が原因を示さないぶん、**踏むたびに同じ切り分けコストがかかる**のが実害である。

### 検討した案

- **案 A: `Test` タスクの実行直前に到達性を確認して fail fast する**（採用）。ADR-0071 と同じガードを
  同じスクリプトで呼ぶだけで、原因 1 つに対して失敗 1 つを返せる。テストは 1 件も起動しない。
- **案 B: `PostgresContainerSupport` の静的初期化を try/catch し、原因を明示した例外に包み直す**（不採用）。
  追加コストはゼロだが、**失敗件数（172）は 1 件も減らない**。真因のメッセージが読みやすくなるだけで、
  「一覧のどこかに埋もれる」という実害の本体が残る。案 A を入れれば通常の経路では到達しないため、
  二重に持つ価値も薄い。
- **案 C: `Test` タスクに `onlyIf` を付け、Docker 不在ならテストごと skip する**（不採用）。ADR-0071 が
  「Docker 不要なテストだけ切り出す運用は採らない」と決めた方針に正面から反する。加えて **skip は緑で
  通る**ため、ゲートが何も検証していない状態を成功と誤読させる。危険側に転ぶ設計は採らない
  （`.claude/rules/gates.md`）。
- **案 D: 手当てしない**（不採用）。「Docker を起動しておけば済む」のは事実だが、それは切り分けコストを
  毎回払い続ける選択であり、現に一度誤診を誘発している。

### CI での扱い

CI には Docker が必ずあるため、プローブは**純粋な追加コスト**にしかならない。環境変数 `CI` で分岐して
ローカル専用にする。lefthook 側のガードは CI で動かないので分岐は要らない。

### pre-push での二重実行

pre-push は `docker-available`（lefthook）→ `full-test`（`./gradlew test`）の順に走るため、本 ADR の
ガードを入れると**プローブが 2 回走る**。これは許容する。

- lefthook 側を残すのは、**Gradle の起動と設定フェーズを待たずに数秒で落ちること自体が ADR-0071 の
  価値**だから（worktree では `--no-daemon` で走るため起動はさらに重い）。
- 片方を抑止する環境変数（`DOCKER_PROBE_SKIP` 等）は**置かない**。誤って抑止されたときに「ガードが
  効いていないのに気づけない」危険側へ転ぶ。カバレッジゲートで includes ではなく excludes 反転を
  選んだ（[ADR-0040](0040-coverage-gate-operation-model.md)）のと同じ「忘れても安全側」の基準による。
- 重複するのは 1 回ぶんのプローブ時間だけで、全テストを回す pre-push の中では無視できる。

### 実装上の落とし穴（実測で確認）

- **`ProcessBuilder.inheritIO()` では失敗メッセージが端末に出ない**。タスクアクションは Gradle
  デーモンのプロセス内で走るため、継承先はデーモンの stdout であってビルドを起動した端末ではない。
  実測では「詳細は上のメッセージ」と案内しながら本文が丸ごと消えた。出力はパイプで受け切り、
  `GradleException` のメッセージに載せて Gradle の "What went wrong" に出す。
- **タスクアクションのラムダからビルドスクリプトのトップレベル `val` を直に読むと configuration cache
  が落ちる**。Kotlin DSL ではスクリプトオブジェクトごとキャプチャされ、`cannot serialize Gradle script
  object references` になる（本リポジトリは `org.gradle.configuration-cache.problems=fail` で運用して
  いるため設定フェーズごと失敗する）。値は設定フェーズでローカル変数へ束縛してから渡す。

## Decision（決定）

**`tasks.withType<Test>` の `doFirst` から `scripts/check-docker-available.sh` を呼び、Docker に到達
できなければテストを 1 件も起動せずに落とす。** ゲートの範囲（どのテストを走らせるか）は変えない。

- 対象は Gradle の全 `Test` タスク（決定時点では `test` / `e2eTest` / `replay`。出所は `build.gradle.kts`）。
  pre-push の `glob` に相当する絞り込みは置かない —— `Test` タスクが実行される時点で Docker は必ず要る。
- **環境変数 `CI` が設定されていればプローブを飛ばす**。
- 判定ロジックは pre-push と同じスクリプトに一本化し、**失敗時の対処案内だけ** 環境変数
  `DOCKER_PROBE_CALLER`（`pre-push` 既定 / `gradle`）で出し分ける。pre-push 向けの
  `LEFTHOOK_EXCLUDE=...` の案内は Gradle 経路では的外れになるため。
- **pre-push では lefthook と Gradle の両方でプローブが走る**。これを抑止する仕組みは置かない。
- [ADR-0071](0071-pre-push-docker-fail-fast-guard.md) は **Superseded にしない**。その決定（fail fast
  する / テストの切り分けは行わない）はそのまま有効で、本 ADR はその射程を広げるだけである。

## Consequences（結果・影響）

- Docker 不在で `test` / `check` を直接叩いたとき、**172 件のテスト失敗が 1 件のタスク失敗になる**。
  原因と対処がそのまま "What went wrong" に出るため、切り分けが不要になる。
- **ローカルの `Test` タスク実行にプローブ 1 回ぶんの時間が乗る**（`docker info` の応答時間と完了検知
  の粒度 1 秒で決まる。本 ADR 時点のローカル実測で成功時 2.07 秒）。タスクが UP-TO-DATE なら
  実行されないため毎回払うわけではなく、CI では飛ばすので影響しない。
- **ガードの守備範囲は ADR-0071 と同じ**「Docker に到達できない」ことに限る。Docker は生きているが
  Testcontainers だけ失敗するケース（イメージの pull 不可・リソース枯渇等）は素通りし、従来どおり
  テストの失敗として出る。
- **pre-push の逃げ道（`LEFTHOOK_EXCLUDE=docker-available,full-test git push`）に相当するものは
  `test` / `check` 側に無い**。Docker を復旧してから実行し直すことになる。案 C を採らない以上、
  これは意図した帰結である。
- ガードの非空振り確認は **Docker を落とさずに `DOCKER_HOST=tcp://127.0.0.1:1` を被せる**ことで
  再現できる（`docker info` が接続拒否で非ゼロになる）。手順は `.claude/rules/testing.md` に置いた。
- 結論（守るべきルール）は `.claude/rules/testing.md` の「ローカルゲートと Docker」に置いた。
- **関連 ADR**: [ADR-0071](0071-pre-push-docker-fail-fast-guard.md)（本 ADR が射程を広げる元の決定）、
  [ADR-0040](0040-coverage-gate-operation-model.md)（「忘れても安全側」を採る判断基準）、
  [ADR-0070](0070-db-test-cleanup-via-truncate-not-transactional.md)（連鎖の起点になる共有コンテナの
  後始末方式）。
