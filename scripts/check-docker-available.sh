#!/usr/bin/env bash
# テストを起動する前に Docker の到達性を確認し、到達できなければ理由と対処を明示して即座に
# 失敗させる（ADR-0071 / #679。射程を Gradle の Test タスクへ広げたのは #847）。
#
# 呼び出し元は 2 つあり、どちらも判定ロジックは同じ。失敗時の対処案内だけを
# DOCKER_PROBE_CALLER で出し分ける（迂回の手順が経路ごとに違うため）。
#
#   - pre-push（lefthook の docker-available コマンド。既定）
#     `./gradlew test` は Testcontainers（PostgreSQL）に依存するため Docker デーモンを要求する。
#     デーモンが不調だと Testcontainers が接続を試み続けてテストが長時間ハングし、端末上は
#     「push が無反応」に見える。ネットワークやリモートを疑って切り分けに時間を取られ、最後は
#     `--no-verify`（全フック迂回）へ逃げることになる。
#   - gradle（build.gradle.kts の tasks.withType<Test> の doFirst）
#     Docker 不在のまま test / check を直接叩くと、共有コンテナの静的初期化
#     （PostgresContainerSupport）が失敗し、そこから NoClassDefFoundError が連鎖して 172 件の
#     テスト失敗になる。真因は ExceptionInInitializerError の 1 件だけで、失敗一覧の先頭に
#     出るとも限らないため埋もれる（#847 で実測）。
#
# どちらもゲートの範囲は変えない。失敗を「速く・分かる形」に変えるのがこのスクリプトの役割。
#
# 使い方:
#   scripts/check-docker-available.sh
#   - 環境変数 DOCKER_PROBE_TIMEOUT_SECONDS で打ち切り秒数を上書きできる（既定 15）。
#   - 環境変数 DOCKER_PROBE_CALLER で対処案内を切り替える（pre-push（既定）/ gradle）。
# 終了コード: Docker に到達できれば 0、できなければ 1。
#
# 判定は `docker info` の成否だけで行い、`docker ps` 等は重ねない。実測した障害
# （Docker Desktop のバックエンド不調）では CLI が API バージョン不整合のメッセージとともに
# 500 を返し `docker info` が非ゼロで落ちたため、これで捕捉できる。一方でソケットは
# 受け付けるが応答しない状態では `docker info` 自体がハングするので、必ず時間で打ち切る。
#
# 互換: macOS 標準の bash 3.2 で動く。GNU coreutils の `timeout` は macOS に無いため使わず、
# 自前のポーリングで打ち切る（完了検知の粒度は 1 秒）。
set -euo pipefail

TIMEOUT_SECONDS="${DOCKER_PROBE_TIMEOUT_SECONDS:-15}"
CALLER="${DOCKER_PROBE_CALLER:-pre-push}"

# 到達不能の理由と、どちらの経路でも共通する対処（復旧・確認）を出す。
fail_common() {
  cat >&2 <<EOF
NG: Docker に到達できません（$1）。

$2

対処:
  1. Docker を起動する（Docker Desktop / colima / dockerd 等）。起動しているつもりなら再起動する
     （バックエンドが不調で API が 500 を返し続けることがある）。
  2. \`docker info\` を手で叩いて出力を確認する。
EOF
}

# 経路ごとに違うのは 3 番目（今すぐ先へ進みたいときの逃げ道）だけ。
fail() {
  case "$CALLER" in
    gradle)
      fail_common "$1" "このプロジェクトのテストは Testcontainers（PostgreSQL）で本番ターゲットの DB を用意するため Docker
デーモンが要ります。このまま起動すると共有コンテナの静的初期化が失敗し、原因を示さないテスト失敗が
大量に出て真因が埋もれるため（#847）、テストを 1 件も走らせずに打ち切りました。"
      cat >&2 <<EOF
  3. Docker 不要なテストだけを回す運用は用意していません（ADR-0071。穴が空くのが永続化契約テスト
     という最も守りたい場所になるため）。Docker を復旧してから実行し直してください。
EOF
      ;;
    *)
      fail_common "$1" "pre-push の全テスト（./gradlew test）は Testcontainers（PostgreSQL）を使うため Docker デーモンが
要ります。テストを起動すると接続の再試行で長時間ハングし「push が無反応」に見えるため、起動前に
打ち切りました。"
      cat >&2 <<EOF
  3. どうしても今 push したいなら、テストゲートだけ外して push する:
       LEFTHOOK_EXCLUDE=docker-available,full-test git push
     （pre-push の他フックは残る。\`--no-verify\` は全フックを飛ばすので最後の手段）。
     同じテストは CI（api-tests.yml）でも走るため、壊れたまま push すれば CI が検出する。
EOF
      ;;
  esac
  exit 1
}

if ! command -v docker >/dev/null 2>&1; then
  fail "docker コマンドが PATH にありません"
fi

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT

# プローブを背後で走らせ、終了コードをファイルへ落とす。完了検知にファイルの有無を使うのは、
# `kill -0` によるプロセス生存判定が zombie（終了済みだが未 wait）にも成功してしまい、
# 終わったプローブを生きていると誤認するため。
#
# `docker info` の出力を /dev/null へ捨てるのは静粛化のためだけではない。打ち切り時に
# プローブを kill しても孤児化した `docker` が残りうるので、親から継承した stdout/stderr を
# 掴ませない。掴ませると呼び出し元（lefthook）がパイプの終端を待ち続け、fail fast のはずが
# 打ち切り秒数ぶん待たされる（実測で確認済み）。
(
  # errexit 下ではサブシェルが `docker info` の非ゼロ終了で即死し status を書けないため、
  # `|| status=$?` で受けてから書き出す。
  status=0
  docker info >/dev/null 2>&1 || status=$?
  echo "$status" >"$work_dir/status"
) &
probe_pid=$!

waited=0
while [ ! -f "$work_dir/status" ]; do
  if [ "$waited" -ge "$TIMEOUT_SECONDS" ]; then
    kill -TERM "$probe_pid" 2>/dev/null || true
    fail "docker info が ${TIMEOUT_SECONDS} 秒以内に応答しませんでした"
  fi
  sleep 1
  waited=$((waited + 1))
done

probe_status=$(cat "$work_dir/status")
if [ "$probe_status" -ne 0 ]; then
  fail "docker info が失敗しました（終了コード ${probe_status}）"
fi
