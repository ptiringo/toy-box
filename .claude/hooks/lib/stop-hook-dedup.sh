#!/bin/bash
# Stop hook 共通ヘルパー: 失敗指紋の dedup（#519）
#
# 「前回失敗から作業ツリーが変わっていなければ check を再実行しない」ための
# 指紋計算と状態ファイル操作。Stop hook から source して使う（単体では実行しない）。
#
# - 指紋は HEAD ＋ tracked の未コミット差分 ＋ untracked（gitignore 除外済み）の
#   パスと内容から作る。untracked の「内容」まで含めるのは、新規ファイルは
#   コミットまで untracked のままで、パス一覧だけでは修正しても指紋が変わらず
#   再武装されないため。
# - 状態ファイルは git-dir 配下（非コミット領域）。worktree では git-dir が
#   .git/worktrees/<name> になるため worktree ごとに自然に分離される。
# - 失敗時のみ記録し、成功時は削除する（失敗指紋だけを持つ）。

# 作業ツリーの指紋を stdout へ出す。リポジトリ root で呼ぶこと。
compute_tree_fingerprint() {
    {
        git rev-parse HEAD 2>/dev/null
        git diff HEAD 2>/dev/null
        git ls-files --others --exclude-standard 2>/dev/null
        git ls-files --others --exclude-standard -z 2>/dev/null \
            | xargs -0 -r git hash-object -- 2>/dev/null
    } | git hash-object --stdin
}

# 指定 hook の状態ファイルパスを stdout へ出す（親ディレクトリ作成込み）。
dedup_state_file() {
    local hook_name="$1" state_dir
    state_dir="$(git rev-parse --git-dir 2>/dev/null)/claude-stop-hooks"
    mkdir -p "$state_dir" 2>/dev/null || return 1
    printf '%s/%s.fingerprint' "$state_dir" "$hook_name"
}

# 記録済みの失敗指紋と一致するか（一致＝ツリー無変更なのでスキップしてよい）。
should_skip_unchanged() {
    local hook_name="$1" fingerprint="$2" state_file
    state_file="$(dedup_state_file "$hook_name")" || return 1
    [ -f "$state_file" ] && [ "$(cat "$state_file" 2>/dev/null)" = "$fingerprint" ]
}

# check 失敗時に現在の指紋を記録する。
record_failure_fingerprint() {
    local hook_name="$1" fingerprint="$2" state_file
    state_file="$(dedup_state_file "$hook_name")" || return 0
    printf '%s\n' "$fingerprint" > "$state_file" 2>/dev/null
}

# check 成功時に状態ファイルを削除する。
clear_failure_fingerprint() {
    local hook_name="$1" state_file
    state_file="$(dedup_state_file "$hook_name")" || return 0
    rm -f "$state_file" 2>/dev/null
}
