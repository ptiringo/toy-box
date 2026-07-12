#!/usr/bin/env bash
# Claude Code on the web のクラウドセッション用セットアップ。
#
# クラウド環境 UI の「セットアップスクリプト」欄から呼ぶ本体。呼び出し方・UI 側に必要な設定
# （Custom 許可ドメイン・環境変数）は docs/claude-code-on-the-web.md 参照。クラウド VM は素の
# JDK 21 だが本リポジトリの Gradle toolchain は 25 を要求するため、mise で Temurin 25 を供給する。
# PATH 注入は既存の .claude/hooks/session-start-mise.sh（SessionStart フック）が毎セッション担うので、
# ここでは「mise 導入 → mise install java → mise activate 仕込み → プロキシ CA の信頼」を一度だけ行う。
#
# check-緑のクリティカルパスは JDK 25 のみに絞る（`mise install`（全ツール）ではなく
# `mise install java`）。pipx 系ツールの取得失敗で `./gradlew check` の土台を止めないため。
# kotlin-lsp（JetBrains ホスト）はスクリプト末尾で **best-effort** に追加導入する（#627）——
# 取得失敗しても setup を止めず、check 緑には影響させない。
#
# 冪等: 再実行しても mise 再導入や .bashrc への重複追記は行わない。
set -euo pipefail

# 呼び出し時の CWD に依存せずリポジトリルートで動くよう、スクリプト自身の位置から移動する。
# Claude Code on the web の setup スクリプトは CWD がリポジトリルートである保証がなく（公式未記載）、
# repo 相対パスで呼ぶと 127（No such file or directory）になる。後続の `mise trust` /
# `mise install`（mise.toml を読む）はリポジトリルートで実行する必要がある。
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# UTF-8 ロケールを使う。クラウド VM の既定ロケールは POSIX(C) で、JVM の sun.jnu.encoding
# （ファイル名エンコーディング）が非 UTF-8 になる。すると日本語のテストメソッド名から生成される
# .class のパスを書き出せず、Kotlin コンパイラが InvalidPathException で内部エラーになる。
# sun.jnu.encoding は OS ロケール由来で -D 指定が効かないことがあるため、ロケール自体を UTF-8 にする。
# ここでの export は本スクリプト内の JVM 実行にのみ効く。セッション側にも効かせるには
# クラウド環境 UI の環境変数に LANG / LC_ALL を設定すること（docs/claude-code-on-the-web.md 参照）。
for locale_candidate in C.utf8 C.UTF-8; do
  if locale -a 2>/dev/null | grep -qixF "${locale_candidate}"; then
    export LANG="${locale_candidate}"
    export LC_ALL="${locale_candidate}"
    break
  fi
done

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

# クラウドの egress は TLS を再署名する傍受プロキシ（issuer: Anthropic Egress Gateway CA）経由。
# セッションには CA 入り truststore が JAVA_TOOL_OPTIONS で渡るが、**この setup スクリプトの実行
# コンテキストには渡らない**ため、素のままだと Gradle の HTTPS ダウンロード（services.gradle.org）が
# 「PKIX path building failed」で落ちる。プロキシ CA を JDK cacerts に取り込んで JVM に信頼させる。
# CA の実体は環境変数（NODE_EXTRA_CA_CERTS 等）が指すバンドル。所在は公式未文書化のため候補を広く見る。
# バンドルは複数証明書の連結で keytool は先頭 1 件しか読まないため、分割して個別に import する。
java_home_dir="$(mise where java 2>/dev/null || true)"
cacerts="${java_home_dir}/lib/security/cacerts"

ca_sources=""
for ca_file in "${NODE_EXTRA_CA_CERTS:-}" "${SSL_CERT_FILE:-}" "${CURL_CA_BUNDLE:-}" \
               "${REQUESTS_CA_BUNDLE:-}" /etc/ssl/certs/ca-certificates.crt; do
  { [ -n "${ca_file}" ] && [ -f "${ca_file}" ]; } || continue
  # 同じバンドルを複数の環境変数が指すので重複を除く。
  case " ${ca_sources} " in
    *" ${ca_file} "*) continue ;;
  esac
  ca_sources="${ca_sources} ${ca_file}"
done

if [ -n "${java_home_dir}" ] && [ -f "${cacerts}" ] && [ -n "${ca_sources}" ]; then
  imported=0
  for ca_src in ${ca_sources}; do
    ca_tmp="$(mktemp -d)"
    # BEGIN CERTIFICATE ごとに 1 ファイルへ分割する（先頭のコメント行は n=0 で捨てる）。
    awk -v d="${ca_tmp}" '
      /-----BEGIN CERTIFICATE-----/ { n++ }
      n > 0 { print > sprintf("%s/ca-%03d.pem", d, n) }
    ' "${ca_src}"
    src_alias="$(printf %s "${ca_src}" | tr '/.' '__')"
    for cert in "${ca_tmp}"/ca-*.pem; do
      [ -s "${cert}" ] || continue
      # 既に取り込み済み（再実行時）や証明書でない断片は無視する。成功したものだけ数える。
      if "${java_home_dir}/bin/keytool" -importcert -noprompt -trustcacerts \
          -alias "proxyca-${src_alias}-$(basename "${cert}" .pem)" -file "${cert}" \
          -keystore "${cacerts}" -storepass changeit >/dev/null 2>&1; then
        imported=$((imported + 1))
      fi
    done
    rm -rf "${ca_tmp}"
  done
  echo "imported ${imported} CA cert(s) into JDK cacerts (trust the egress proxy CA)"
fi

# 検証: JDK 25 が解決でき、Gradle が toolchain を満たせること。
# `mise exec` は**必ず java にスコープする**（`mise exec java -- ...`）。ツール未指定の
# `mise exec -- ...` は mise.toml の全ツールを auto-install してしまい、スコープ外の
# kotlin-lsp（JetBrains ホストは Custom 未許可）や GitHub API レート制限で落ちる。
echo "verifying toolchain ..."
mise exec java -- java -version
mise exec java -- ./gradlew --version

# kotlin-lsp（JetBrains 公式 Kotlin Language Server）を best-effort で導入する。Claude Code の
# kotlin-lsp@claude-plugins-official プラグイン（.claude/settings.json で有効化済み）が PATH 上の
# `kotlin-lsp --stdio` を起動し、編集時診断・コードナビを提供する。採否は ADR-0046、クラウド有効化は #627。
#
# **check-緑のクリティカルパス（上の verify まで）が済んだ後に置き、失敗しても setup を止めない**。
# JetBrains ホスト（download-cdn.jetbrains.com）取得失敗や実験版の不調で `./gradlew check` の土台を
# 巻き込まないため（LSP はゲートではない補助操舵）。UI の Custom 許可ドメインに
# download-cdn.jetbrains.com が要る（docs/claude-code-on-the-web.md 参照）。
#
# **java とスコープを混ぜず `http:kotlin-lsp` だけを名指しする**。ツール未指定の `mise install` は
# mise.toml の全ツールを入れにいき GitHub API レート制限等で落ちる。`if` で包むのは非ゼロ終了を
# else に流して `set -e` によるスクリプト終了を避けるため（best-effort）。
echo "installing kotlin-lsp via mise (best-effort; JetBrains host) ..."
if mise install http:kotlin-lsp; then
  # 導入しただけでは LSP は起動しない。Claude Code の LSP プラグインのランタイム（Node）は
  # **mise 非活性の base PATH** で `kotlin-lsp --stdio` を spawn するが、mise の shims も installs
  # の bin も base PATH には無いため、素のままだと ENOENT で LSP が立たず編集時診断が出ない。
  # SessionStart の mise hook（session-start-mise.sh）は $CLAUDE_ENV_FILE 経由で **Bash ツールの
  # シェル**にしか PATH を通さず、LSP の spawn 環境には効かない（PATH 供給の非対称）。
  # そこで base PATH 上の ${HOME}/.local/bin（mise 本体もここ）へバイナリ実体を symlink して
  # ランタイムから解決可能にする。実体は bundled JBR 同梱の self-contained ランチャで、Linux では
  # /proc/self/exe で自分の install ディレクトリを解決するため直リンクで動く（mise 実行時解決に依存
  # しない）。`ln -sfn` は冪等で、再実行のたび現行 `mise where` に貼り直すのでバージョン更新にも追随する。
  kotlin_lsp_bin="$(mise where http:kotlin-lsp 2>/dev/null)/bin/kotlin-lsp"
  if [ -x "${kotlin_lsp_bin}" ]; then
    mkdir -p "${HOME}/.local/bin"
    ln -sfn "${kotlin_lsp_bin}" "${HOME}/.local/bin/kotlin-lsp"
    echo "kotlin-lsp installed & linked into ~/.local/bin. クラウドで Kotlin LSP プラグインが有効になる。"
  else
    echo "warn: kotlin-lsp のバイナリ実体が見つからず PATH リンクを張れなかった（LSP は無効のまま）。" >&2
  fi
else
  echo "warn: kotlin-lsp の導入に失敗（download-cdn.jetbrains.com 未許可 or 取得失敗の可能性）。" >&2
  echo "warn: 編集時診断は無効のまま続行する（./gradlew check には影響しない）。" >&2
fi

echo "web-setup done. Run './gradlew check' to validate the session."
