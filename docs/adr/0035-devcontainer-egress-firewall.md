# 0035. devcontainer の egress を firewall で default-deny + 許可リストに制限する

- Status: Accepted
- Date: 2026-06-27
- Deciders: Matsui

## Context（背景・課題）

[#302](https://github.com/ptiringo/toy-box/issues/302) で整備した devcontainer はネットワーク制限なしで
Claude Code を動かす土台で、firewall は明示的にスコープ外だった。エージェントが任意の外部へ通信できる状態は、
データ持ち出しや意図しない外部アクセスのリスクになる。[#304](https://github.com/ptiringo/toy-box/issues/304)
でこの egress を絞る。

論点:

- 許可リストの出所。Claude Code の sandbox `allowedHosts`（`.claude/settings.local.json`）は各自ローカルの
  非コミット設定で、Claude Code 自身のコマンド実行制御に効くレイヤー。devcontainer の OS レベル egress 制御
  （iptables/ipset）とは制御層も管理方法（非コミット個人設定 vs コミット共有）も非対称。
- 現 devcontainer は terraform MCP 用に Docker-in-Docker(DinD) を含む。DinD は自前で iptables の NAT/FORWARD を
  動的に書くため、default-deny の firewall と相互作用する（Anthropic 公式リファレンスの init-firewall.sh は
  DinD を想定しない）。
- JetBrains Gateway はバックエンド配信・プラグイン取得で `*.jetbrains.com` への egress を要する。

## Decision（決定）

devcontainer 起動時に `init-firewall.sh`（`ipset`/`iptables`）で **OUTPUT チェーンを default-deny + 許可リスト**化する。

- **許可ドメインの唯一の出所は `.devcontainer/allowed-domains.txt`（コミット共有）**。GitHub のみ IP レンジが
  動的なため `api.github.com/meta` の CIDR を別途取り込む。Claude Code sandbox `allowedHosts` とは別レイヤーとして
  併存させ、一本化しない（性質が非対称なため）。
- **OUTPUT に集中し `FORWARD` は管理しない**（DinD best-effort）。dev コンテナ自身のプロセス（Claude Code・Gradle・
  mise）の egress を絞ることに集中し、docker が書くチェーンは温存して DinD を壊さない。代償として **DinD 子コンテナ
  経由の egress は厳密には絞られない**（known-caveat）。Claude Code 本体は子コンテナにいないため主目的は守られる。
- **JetBrains Gateway は許可リスト追加で対応**。確立済み接続（`ESTABLISHED,RELATED`）は許可するため着信は妨げず、
  バックエンドの egress 先のみ許可リストに含める。
- iptables ルールは再起動で消えるため `postStartCommand` で毎起動時に root 実行する（`--cap-add=NET_ADMIN/NET_RAW`）。
  スクリプト末尾の自己テストで配線を検証する。

## Consequences（結果・影響）

- Claude Code・ビルド・ツール導入の egress が許可リスト内に制限される。許可漏れは `allowed-domains.txt` 追記 +
  `init-firewall.sh` 再実行で解消する（手順は `.devcontainer/README.md`）。
- DinD 子コンテナ egress は未制約という抜け道が残る（firewall を回避したければ子コンテナに入る）。完全制御が必要に
  なれば FORWARD 管理を別 issue で検討する。
- 許可リストは 2 系統（firewall / Claude Code sandbox）併存となり、編集時は対象レイヤーを意識する必要がある。
- 本 firewall は IPv4（`iptables`）のみを制御し IPv6（`ip6tables`）は対象外。コンテナに IPv6 接続性があれば IPv6 経由の egress は絞られない（known-gap）。Docker 既定の IPv6 無効化と自己テストのバックストップで実害可能性は低い。完全対応は fast-follow issue で扱う。

## 関連

- [#304](https://github.com/ptiringo/toy-box/issues/304) / [#302](https://github.com/ptiringo/toy-box/issues/302)
- 設計: `docs/superpowers/specs/2026-06-27-devcontainer-egress-firewall-design.md`（リポジトリ管理外）
- Anthropic reference devcontainer の init-firewall.sh を下敷きに DinD 向け調停を追加
