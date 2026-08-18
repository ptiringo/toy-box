#!/usr/bin/env bash
# pre-push の glob 判定に使う「これから push される変更ファイル」を列挙する（#804）。
#
# 背景: lefthook の既定の判定材料 `{push_files}` は、リンクされた作業ツリー（git worktree）で
# 誤った比較対象を選ぶ。lefthook は次の順に基準を決めるが、
#
#   1. `git diff --name-only HEAD @{push}`
#   2. 1 が失敗したら `<git-dir>/refs/remotes/origin/HEAD` を読む
#   3. 2 も空なら `git branch --remotes` の出力から最初に `HEAD -> ...` に一致した行を使う
#   4. それも無ければリポジトリの全追跡ファイル
#
# worktree では 2 が必ず外れる。lefthook が見る `<git-dir>` は `.git/worktrees/<name>` で、
# `refs/remotes/origin/HEAD` はそこではなく共有の git-common-dir にあるためである。すると 3 へ
# 落ちるが、`git branch --remotes` はアルファベット順に並ぶので、**origin より前に来る名前の
# リモートが 1 つでもあると、そちらの `HEAD -> ...` が先に一致する**。無関係な履歴との差分に
# なるため事実上ほぼ全ファイルが「push 対象」と見なされ、glob が素通りする。
#
# その結果 `.md` だけの push でも Kotlin 向けのゲート（docker-available / full-test）が起動し、
# Docker を落としているとドキュメントだけの push が塞がれる。upstream を設定していない間ずっと
# 起きる（初回 push に限らない。切り分けの実測は #804）。
#
# 実際に踏んだのは archive 済みの `infra` リモート（toy-box-infra）で、これは #804 の調査後に
# 削除した。よって現状の 3 は `origin/HEAD -> origin/main` を拾う。それでもこのスクリプトを
# 残すのは、fork や backup など origin より前に並ぶリモートを足すと再発するうえ、症状が
# 「テストが余計に走る」だけで無音だからである（2 の欠落は worktree である限り直らない）。
#
# そこで比較対象を lefthook 任せにせず、ここで明示する。
#
# 使い方: lefthook.yml の pre-push から `files:` として呼ぶ（標準出力にファイルパスを 1 行 1 件）。
# 終了コード: 常に 0（列挙できない状況では安全側に倒すため、後述のとおり全追跡ファイルを出す）。
#
# 基準の決め方:
#   1. `@{push}` が解決できればそれ（= 実際に push 済みの地点。lefthook の第一候補と同じ）
#   2. 解決できなければ origin/main との merge-base（= 作業ブランチが分岐した地点）
#   3. どちらも取れなければ全追跡ファイルを出す
#
# 3 は「判定できないならゲートを走らせる」という安全側の倒し方である。空を返すと lefthook は
# コマンドを丸ごとスキップするため、テストゲートが無音で無効化されてしまう（#782 の趣旨どおり、
# ゲートは壊れたときに素通りするのではなく発火する側へ倒す）。
#
# 2 の基準では、すでに push 済みのコミットの変更も差分に含まれることがある（push のたびに分岐点
# から数え直すため）。過剰にゲートが走る向きの誤差なので許容する。
set -euo pipefail

# 作業ブランチの分岐元。CLAUDE.md のとおり main が唯一の統合先。
readonly DEFAULT_BASE="origin/main"

base=""
if resolved=$(git rev-parse --verify --quiet '@{push}' 2>/dev/null) && [ -n "$resolved" ]; then
  base="$resolved"
elif resolved=$(git merge-base HEAD "$DEFAULT_BASE" 2>/dev/null) && [ -n "$resolved" ]; then
  base="$resolved"
fi

if [ -z "$base" ]; then
  git ls-files
  exit 0
fi

git diff --name-only "$base" HEAD --
