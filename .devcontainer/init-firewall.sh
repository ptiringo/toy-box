#!/usr/bin/env bash
# devcontainer の egress を「デフォルト拒否 + 許可リスト」に制限する firewall 初期化スクリプト。
#
# 方針（設計: docs/superpowers/specs/2026-06-27-devcontainer-egress-firewall-design.md / ADR-0035）:
#   - コンテナ自身のプロセスが出す通信（iptables OUTPUT チェーン）を default-deny + 許可リスト化する。
#   - 許可ドメインの唯一の出所は .devcontainer/allowed-domains.txt（コミット共有）。
#   - GitHub は IP レンジが動的なため api.github.com/meta の CIDR を別途取り込む。
#   - FORWARD チェーンは管理しない（docker が動的に書くチェーンを温存）。DinD（terraform MCP の
#     docker run）を壊さないため。代償として DinD 子コンテナ経由の egress は厳密には絞られない（known-caveat）。
#   - iptables ルールはコンテナ再起動で消えるため postStartCommand から毎起動時に root で実行する。
#
# 要 root（postStartCommand で sudo 実行）。NET_ADMIN / NET_RAW capability が必要。
# 依存: iptables / ipset / dig(dnsutils) / jq / curl（post-create.sh が導入）。
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
allowed_domains_file="${script_dir}/allowed-domains.txt"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "error: root 権限が必要です（sudo .devcontainer/init-firewall.sh）" >&2
  exit 1
fi

if [[ ! -f "$allowed_domains_file" ]]; then
  echo "error: $allowed_domains_file が見つかりません" >&2
  exit 1
fi

# 一旦 OUTPUT を許可に戻してから自分の管理対象（OUTPUT ルールと ipset）を冪等に作り直す。
# （再実行時、前回の DROP ポリシーのままだと以降の fetch/解決がブロックされるため）
iptables -P OUTPUT ACCEPT
iptables -F OUTPUT
ipset destroy allowed-domains 2>/dev/null || true
ipset create allowed-domains hash:net

# --- 基盤の許可 ---
# loopback（docker 埋め込み DNS 127.0.0.11 もここ）
iptables -A OUTPUT -o lo -j ACCEPT
# 確立済み・関連（着信応答 / JetBrains Gateway の確立済み接続を止めない）
iptables -A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
# DNS（名前解決）
iptables -A OUTPUT -p udp --dport 53 -j ACCEPT
iptables -A OUTPUT -p tcp --dport 53 -j ACCEPT
# ホスト / Docker ネットワーク宛のローカル通信（JetBrains Gateway・DinD のローカル通信用）。
# RFC1918 プライベートレンジを許可する（インターネット egress の制限が目的のため private は通す）。
for net in 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16; do
  iptables -A OUTPUT -d "$net" -j ACCEPT
done

# --- GitHub の IP レンジを meta API から取得して ipset へ ---
# 取得失敗（レート制限等）でスクリプト全体を中断させない。中断すると OUTPUT DROP 適用前に
# firewall 未適用のまま起動し fail-open になるため。空なら取り込みをスキップし、
# allowed-domains.txt の github.com 等の dig 解決でカバーする。
echo "fetching GitHub IP ranges from api.github.com/meta ..."
gh_meta="$(curl -fsSL --connect-timeout 10 https://api.github.com/meta || true)"
if [[ -n "$gh_meta" ]]; then
  echo "$gh_meta" \
    | jq -r '[.hooks?, .web?, .api?, .git?, .packages?, .actions?, .importer?] | add | .[]?' \
    | { grep -v ':' || true; } \
    | sort -u \
    | while read -r cidr; do
        ipset add allowed-domains "$cidr" 2>/dev/null || true
      done
else
  echo "warn: api.github.com/meta を取得できませんでした。GitHub の IP レンジ取り込みをスキップします（allowed-domains.txt の dig 解決でカバー）。" >&2
fi

# --- allowed-domains.txt の各ドメインを解決して ipset へ ---
while read -r line; do
  domain="${line%%#*}"
  domain="$(tr -d '[:space:]' <<<"$domain")"
  [[ -z "$domain" ]] && continue
  ips="$(dig +short A "$domain" | grep -E '^[0-9]+\.' || true)"
  if [[ -z "$ips" ]]; then
    echo "warn: $domain を解決できませんでした（スキップ）" >&2
    continue
  fi
  while read -r ip; do
    ipset add allowed-domains "$ip" 2>/dev/null || true
  done <<<"$ips"
done <"$allowed_domains_file"

# --- default-deny の適用（OUTPUT のみ。FORWARD は触らない）---
iptables -A OUTPUT -m set --match-set allowed-domains dst -j ACCEPT
iptables -P OUTPUT DROP

echo "firewall 適用完了。"

# --- 自己テスト ---
echo "running self-test ..."
if curl -fS --connect-timeout 5 https://example.com >/dev/null 2>&1; then
  echo "self-test 失敗: 許可外ホスト example.com に到達できてしまいました" >&2
  exit 1
fi
if ! curl -fS --connect-timeout 5 https://api.github.com/zen >/dev/null 2>&1; then
  echo "self-test 失敗: 許可済みホスト api.github.com に到達できません" >&2
  exit 1
fi
echo "self-test 成功: 許可外は遮断・許可済みは疎通。"
