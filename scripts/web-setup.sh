#!/usr/bin/env bash
# Claude Code on the web のクラウドセッション用セットアップ。
#
# クラウド環境 UI の「セットアップスクリプト」欄から呼ぶ本体。呼び出し方は
# docs/claude-code-on-the-web.md 参照（クローン位置は setup 実行時の CWD からは不定なので
# 絶対パスで呼ぶ）。クラウド VM は素の JDK 21 だが本リポジトリの Gradle toolchain は 25 を
# 要求するため、mise で Temurin 25 を供給してギャップを埋める。PATH 注入は既存の
# .claude/hooks/session-start-mise.sh（SessionStart フック）が毎セッション担うので、
# ここでは「mise 導入 → mise install java → mise activate 仕込み」までを一度だけ行う。
#
# スコープは `./gradlew check` を緑にすることに限定し、`mise install`（全ツール）ではなく
# `mise install java`（JDK 25 のみ）だけを入れる。kotlin-lsp（JetBrains ホスト・スコープ外）や
# pipx 系ツールの取得失敗で全体を止めない・不要な Custom 許可ドメインを増やさないため。
#
# 冪等: 再実行しても mise 再導入や .bashrc への重複追記は行わない。
set -euo pipefail

# 呼び出し時の CWD に依存せずリポジトリルートで動くよう、スクリプト自身の位置から移動する。
# Claude Code on the web の setup スクリプトは CWD がリポジトリルートである保証がなく（公式未記載）、
# repo 相対パスで呼ぶと 127（No such file or directory）になる。後続の `mise trust` /
# `mise install`（mise.toml を読む）はリポジトリルートで実行する必要がある。
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# mise 本体が無ければ導入し、このスクリプト内でも使えるよう PATH に載せる。
if ! command -v mise >/dev/null 2>&1; then
  echo "installing mise ..."
  curl -fsSL https://mise.run | sh
fi
export PATH="${HOME}/.local/bin:${PATH}"

if ! command -v mise >/dev/null 2>&1; then
  echo "error: mise の導入に失敗しました（PATH に mise が見つかりません）。" >&2
  exit 1
fi

# 対話 bash 起動時に mise を有効化する（重複追記を避ける。post-create.sh と同方式）。
if ! grep -qs 'mise activate bash' "${HOME}/.bashrc"; then
  # `$(mise activate bash)` は .bashrc にリテラルとして書き込むのが意図（ここで展開しない）。
  # shellcheck disable=SC2016
  echo 'eval "$(mise activate bash)"' >> "${HOME}/.bashrc"
fi

# このリポジトリの mise.toml を信頼してから JDK 25 のみ導入する。
mise trust
echo "installing JDK 25 via mise (java only) ..."
mise install java

# クラウドの egress は TLS 傍受プロキシ（自前 CA で再署名する MITM）経由。curl / apt は
# システム CA ストア（プロキシ CA 込み）で通るが、mise 導入の Temurin は独自 cacerts を使うため、
# 素のままだと Gradle の HTTPS ダウンロード（services.gradle.org 等）が
# 「PKIX path building failed」で落ちる。プロキシ CA を JDK cacerts に取り込んで JVM に信頼させる。
# プロキシ CA の所在は公式未文書化のため、候補（env var が指す CA ファイル群 + システムバンドル）を
# 広めに集めて取り込む。バンドルは複数証明書の連結で keytool は先頭 1 件しか読まないため分割する。
# JAVA_HOME はインストールパス直取り（mise where）で得る。ガードにより CA が無い環境ではスキップする。
java_home_dir="$(mise where java 2>/dev/null || true)"
cacerts="${java_home_dir}/lib/security/cacerts"
echo "diag: java_home_dir=${java_home_dir:-empty}"
echo "diag: cacerts=$( [ -f "${cacerts}" ] && echo present || echo missing )"
echo "diag: NODE_EXTRA_CA_CERTS=${NODE_EXTRA_CA_CERTS:-unset}"
echo "diag: SSL_CERT_FILE=${SSL_CERT_FILE:-unset}"
echo "diag: CURL_CA_BUNDLE=${CURL_CA_BUNDLE:-unset}"
echo "diag: REQUESTS_CA_BUNDLE=${REQUESTS_CA_BUNDLE:-unset}"

# 取り込み候補 CA ファイル（存在するものだけ）。
ca_sources=""
for f in "${NODE_EXTRA_CA_CERTS:-}" "${SSL_CERT_FILE:-}" "${CURL_CA_BUNDLE:-}" \
         "${REQUESTS_CA_BUNDLE:-}" /etc/ssl/certs/ca-certificates.crt; do
  [ -n "${f}" ] && [ -f "${f}" ] && ca_sources="${ca_sources} ${f}"
done
echo "diag: ca_sources=${ca_sources:-none}"

if [ -n "${java_home_dir}" ] && [ -f "${cacerts}" ] && [ -n "${ca_sources}" ]; then
  imported=0
  for src in ${ca_sources}; do
    ca_tmp="$(mktemp -d)"
    # BEGIN CERTIFICATE ごとに 1 ファイルへ分割する（先頭のコメント行は n=0 で捨てる）。
    awk -v d="${ca_tmp}" '
      /-----BEGIN CERTIFICATE-----/ { n++ }
      n > 0 { print > sprintf("%s/ca-%03d.pem", d, n) }
    ' "${src}"
    src_alias="$(printf %s "${src}" | tr '/.' '__')"
    for cert in "${ca_tmp}"/ca-*.pem; do
      [ -s "${cert}" ] || continue
      # 既存 alias（再実行時）や取り込めない行は無視する。成功したものだけ数える。
      if "${java_home_dir}/bin/keytool" -importcert -noprompt -trustcacerts \
          -alias "proxyca-${src_alias}-$(basename "${cert}" .pem)" -file "${cert}" \
          -keystore "${cacerts}" -storepass changeit >/dev/null 2>&1; then
        imported=$((imported + 1))
      fi
    done
    rm -rf "${ca_tmp}"
  done
  echo "diag: newly imported ${imported} cert(s) into JDK cacerts"
fi

# プロキシが提示する証明書チェーンの発行者を診断（非致命）。
if command -v openssl >/dev/null 2>&1; then
  echo "diag: openssl probe services.gradle.org:443 (issuer/subject) ..."
  printf '' | openssl s_client -connect services.gradle.org:443 \
      -servername services.gradle.org 2>/dev/null \
    | grep -E '^(depth|verify|subject|issuer|s:|i:)' | head -n 20 || true
fi

# 検証: JDK 25 が解決でき、Gradle が toolchain を満たせること（**非致命**）。
# 検証で落ちてもセッション自体は立ち上げたいので警告に留め、末尾に要約を出す。
# `mise exec` は**必ず java にスコープする**（`mise exec java -- ...`）。ツール未指定の
# `mise exec -- ...` は mise.toml の全ツールを auto-install してしまい、スコープ外の
# kotlin-lsp（JetBrains ホストは Custom 未許可）や GitHub API レート制限で落ちる。
echo "verifying toolchain (non-fatal) ..."
mise exec java -- java -version || echo "WARN: 'java -version' failed"
if mise exec java -- ./gradlew --version; then
  echo "web-setup: OK — JDK 25 + Gradle が解決できました。'./gradlew check' で検証してください。"
else
  echo "WARN: 'gradlew --version' が失敗しました（TLS/プロキシ CA の可能性）。上の diag: 行を確認してください。セッションは起動します。"
fi
