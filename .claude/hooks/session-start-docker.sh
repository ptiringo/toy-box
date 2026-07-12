#!/bin/bash
# SessionStart hook: Docker デーモンが未起動なら（安全に起動できる場合のみ）起動する。
#
# Testcontainers を使う統合テスト（PostgresContainerSupport → Jdbc*ContractTest や
# SecurityConfigTest 等）と、それらを回す `./gradlew check` は Docker デーモンを要求する。
# CCR リモートコンテナのようにデーモンが自動起動しない環境では、これが無いと
# Testcontainers が /var/run/docker.sock を見つけられずコンテキスト初期化で全滅する。
#
# ポータビリティのためのガード（共有フックなので環境依存にしない）:
#   - `docker` CLI が無ければ何もしない。
#   - 既に `docker info` が通る（＝デーモン稼働中。ローカルの Docker Desktop 等）なら no-op。
#   - 未起動でも、無確認で起動できる場合（root もしくは passwordless sudo）に限り起動する。
#     パスワードプロンプトを出す `sudo` には決してフォールバックしない。
# これによりローカル開発機では実質何もせず、デーモンが要る環境でだけ立ち上げる。

command -v docker >/dev/null 2>&1 || exit 0

# 既に使えるなら何もしない。
if docker info >/dev/null 2>&1; then
  exit 0
fi

command -v dockerd >/dev/null 2>&1 || exit 0

# 無確認で dockerd を起動する手段を決める（プロンプトが出る sudo は使わない）。
if [ "$(id -u)" = "0" ]; then
  runner=()
elif sudo -n true >/dev/null 2>&1; then
  runner=(sudo)
else
  # 権限が無い環境では黙って諦める（ローカル等では docker info 側で既に抜けている想定）。
  exit 0
fi

log_file="${TMPDIR:-/tmp}/claude-session-dockerd.log"
nohup "${runner[@]}" dockerd >"$log_file" 2>&1 &

# ソケットが立ち上がるまで短時間だけ待つ（後続の gradle check で race しないように）。
for _ in $(seq 1 30); do
  if docker info >/dev/null 2>&1; then
    echo "Docker デーモンを起動しました（Testcontainers / gradle check 用）。"
    exit 0
  fi
  sleep 1
done

echo "Docker デーモンの起動を試みましたが応答待ちで確認できませんでした（ログ: $log_file）。" >&2
exit 0
