# 0071. pre-push の Docker 依存は fail fast ガードで扱い、テストの切り分けは行わない

- Status: Accepted
- Date: 2026-08-02
- Deciders: Matsui

## Context（背景・課題）

Issue #679。lefthook の pre-push フック（`full-test`）は `./gradlew test` で全テストを実行する。
テストの一部は Testcontainers（PostgreSQL）に依存するため、**Docker が利用できない状態だと push
そのものができなくなる**。問題は「できなくなること」よりも**失敗の仕方**にある。

- Docker デーモンが応答しないと Testcontainers が接続を試み続け、テストが長時間ハングする。
- 端末上は「push が無反応」に見え、ネットワークやリモート側の問題と誤認しやすい。
- 切り分けに時間を取られた末、`git push --no-verify` に逃げることになる。`--no-verify` は
  pre-push フックを丸ごと飛ばすので、ゲートの粒度としては粗すぎる。

#365（PR #676）の作業中に Docker Desktop がバックエンド不調（全 API ルートで 500 Internal Server
Error）になり、この事象を実際に踏んだ。`git push` が 2 分待っても無反応で、`git ls-remote`（成功）で
ネットワーク側を切り分けてようやく pre-push が原因と判明した。

### 検討した要素

- **どこまでローカルゲートで守るか**: 「Docker が無いと push できない」こと自体は、テストゲートを
  守るための帰結として妥当とも言える。実害はゲートの**範囲**ではなく**失敗の分かりにくさ**なので、
  範囲を削る対処は症状に対して過剰である。
- **案 A: fail fast**（採用）。pre-push の先頭で Docker の到達性を確認し、駄目なら理由と対処を
  明示して即座に失敗させる。ゲートの範囲は一切変えず、ハングだけを潰す。
- **案 B: Testcontainers 依存テストを pre-push の対象から外す**（不採用）。Docker 不要なテスト
  （ドメイン層のユニット・ArchUnit）だけをローカルゲートにし、Testcontainers 依存分は CI に委ねる案。
  次の理由で採らない。
  - **穴が空く場所が悪い**。Testcontainers に依存しているのは永続化層の契約テスト
    （`Jdbc*ContractTest` 群）と publish-after-commit / トランザクション境界の検証で、まさに
    「ローカルで壊したことに気づきたい」箇所である。ゲートから外すと、最も壊れやすい部分だけが
    push 後まで検出されなくなる。
  - **分類の二重管理が常時コストとして残る**。分離には JUnit の `@Tag` 付けとタスク分割が要り、
    新規テストでタグを付け忘れると**静かにゲートが緩む**（危険側に転ぶ）。カバレッジゲートで
    includes 方式ではなく excludes 反転を選んだ（[ADR-0040](0040-coverage-gate-operation-model.md)）
    のと同じ「忘れても安全側」の判断基準に反する。
  - **ゲート外への切り出しは既に済んでいる**。遅く探索的な E2E（[ADR-0056](0056-drop-karate-native-resttestclient-e2e.md)）と
    replay ハーネスは独立ソースセットとして `check` / pre-push の対象外にしてある。ローカルゲートに
    残しているのは速く決定的なものだけで、Testcontainers 契約テストもその条件を満たす（遅くなるのは
    Docker が不調なときだけ）。
- **案 C: 現状維持 + 文書化**（不採用）。「Docker が落ちていたら `--no-verify`」を運用として書くだけでは、
  毎回の切り分けコストが消えず、逃げ道が全フック迂回のままになる。
- **到達性の判定方法**: `docker info` の成否だけで判定する。実測した障害では CLI が API バージョン
  不整合のメッセージとともに 500 を返し、`docker info` が非ゼロで落ちたためこれで捕捉できる。
  `docker ps` 等を重ねても情報は増えない。ただし**ソケットは受け付けるが応答しない**状態では
  `docker info` 自体がハングしうるため、判定は必ず時間で打ち切る（既定 15 秒）。
- **実装上の落とし穴**（実測で確認し、スクリプトのコメントに残した）:
  - プローブの完了検知に `kill -0` を使うと、終了済みで未 wait の zombie に対しても成功するため
    「終わったプローブを生きている」と誤認する。終了コードをファイルへ落として有無で判定する。
  - プローブの出力を親から継承させたまま打ち切ると、孤児化した `docker` が呼び出し元（lefthook）の
    パイプを掴み続け、**fail fast のはずが打ち切り秒数ぶん待たされる**。プローブの stdout/stderr は
    `/dev/null` へ落とす。
  - GNU coreutils の `timeout` は macOS に無いため使わない（既存スクリプトと同じく bash 3.2 互換で書く）。

## Decision（決定）

**pre-push は Docker 到達性を先に確認して fail fast する。テストの切り分け（案 B）は行わず、
ローカルゲートの範囲は現状のまま維持する。**

- `scripts/check-docker-available.sh` を追加する。`docker info` を時間打ち切り付きで実行し、
  到達できなければ**理由**（コマンド不在 / 非ゼロ終了 / 無応答）と**対処**（Docker の起動・再起動、
  `docker info` の手動確認、ゲートを外して push する手順）を出して終了コード 1 で落とす。
  打ち切り秒数は環境変数 `DOCKER_PROBE_TIMEOUT_SECONDS` で上書きできる（既定 15）。
- `lefthook.yml` の pre-push に `docker-available` コマンドを追加し、`priority` で `full-test` より
  先に走らせる。pre-push を `piped: true` にして、ここが落ちたら後続を打ち切る。
  `glob` は `full-test` と揃える（テストが走らない push で Docker を要求しない）。
- 迂回の正規手順は `--no-verify` ではなく **`LEFTHOOK_EXCLUDE=docker-available,full-test git push`**
  とする（pre-push の他フックを残せる粒度）。この案内はガードの失敗メッセージ自体に埋め込む。

## Consequences（結果・影響）

- Docker が不調なときの push は、**数秒で・原因と対処つきで**失敗するようになる（実測: 即時エラー
  なら約 1 秒、無応答なら打ち切り秒数）。ネットワークを疑う切り分けが不要になる。
- **ローカルゲートの範囲は変わらない**。Testcontainers 依存テストは pre-push の対象のままで、
  テスト分類のタグ運用も発生しない。
- 迂回が `LEFTHOOK_EXCLUDE` に寄るため、テストゲートだけを外して gitleaks 等の他フックは残せる。
  それでも同じテストは CI（`api-tests.yml`）で走るので、壊れたまま push すれば CI が検出する。
- 判定は `docker info` 一本なので、**Docker は生きているが Testcontainers だけが失敗する**ケース
  （イメージの pull 不可、リソース枯渇等）は素通りする。そこは従来どおりテストの失敗として現れる。
  ガードの守備範囲は「Docker に到達できない」ことに限る。
- 成功パスでも完了検知の粒度（1 秒）ぶんの待ちが乗る。全テストを回す pre-push の中では無視できる。
- 結論（守るべきルール）は `.claude/rules/testing.md` の「ローカルゲートと Docker」に置いた。
- **関連 ADR**: [ADR-0056](0056-drop-karate-native-resttestclient-e2e.md)（何をゲート外へ切り出すかの
  線引き）、[ADR-0040](0040-coverage-gate-operation-model.md)（「忘れても安全側」を採る判断基準）。
