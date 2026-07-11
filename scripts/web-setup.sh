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
# 「PKIX path building failed」で落ちる。システム CA バンドルを JDK cacerts に取り込んで
# プロキシ CA を JVM に信頼させる。バンドルは複数証明書の連結で keytool は先頭 1 件しか
# 読まないため、分割して個別に import する。
# ガード: システム CA バンドルは Debian/Ubuntu（＝クラウド VM）のパス。macOS 等では存在せずスキップ。
sys_ca_bundle=/etc/ssl/certs/ca-certificates.crt
# JAVA_HOME は mise が設定した値を内側の bash で展開させる（外側で展開させない）。
# shellcheck disable=SC2016
java_home_dir="$(mise exec java -- bash -lc 'printf %s "${JAVA_HOME}"')"
if [ -n "${java_home_dir}" ] && [ -f "${sys_ca_bundle}" ] && [ -f "${java_home_dir}/lib/security/cacerts" ]; then
  echo "importing system CA bundle into JDK cacerts (trust the egress proxy CA) ..."
  ca_tmp="$(mktemp -d)"
  # BEGIN CERTIFICATE ごとに 1 ファイルへ分割する（先頭のコメント行は n=0 で捨てる）。
  awk -v d="${ca_tmp}" '
    /-----BEGIN CERTIFICATE-----/ { n++ }
    n > 0 { print > sprintf("%s/ca-%03d.pem", d, n) }
  ' "${sys_ca_bundle}"
  for cert in "${ca_tmp}"/ca-*.pem; do
    [ -s "${cert}" ] || continue
    # 既存 alias（再実行時）や取り込めない行は無視する。
    "${java_home_dir}/bin/keytool" -importcert -noprompt -trustcacerts \
      -alias "syscacert-$(basename "${cert}" .pem)" -file "${cert}" \
      -keystore "${java_home_dir}/lib/security/cacerts" -storepass changeit \
      >/dev/null 2>&1 || true
  done
  rm -rf "${ca_tmp}"
fi

# 検証: JDK 25 が解決でき、Gradle が toolchain を満たせること。
# `mise exec` は**必ず java にスコープする**（`mise exec java -- ...`）。ツール未指定の
# `mise exec -- ...` は mise.toml の全ツールを auto-install してしまい、スコープ外の
# kotlin-lsp（JetBrains ホストは Custom 未許可）や GitHub API レート制限で落ちる。
echo "verifying toolchain ..."
mise exec java -- java -version
mise exec java -- ./gradlew --version

echo "web-setup done. Run './gradlew check' to validate the session."
