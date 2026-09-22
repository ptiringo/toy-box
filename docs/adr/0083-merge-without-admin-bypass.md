# 0083. PR は --admin を付けずにマージし、required status check を迂回しない

- Status: Accepted
- Date: 2026-09-22
- Deciders: Matsui

## Context（背景・課題）

Issue #819 の検討中に main の ruleset を実測した（2026-09-21）。classic の branch protection は
設定されておらず、ruleset `main` が次を要求している（決定時点の値。現行値の出所はリポジトリ設定の
ruleset `main`）。

- required status check: `Run API Tests` / `Check EditorConfig Compliance` / `CodeQL`
- `required_approving_review_count: 0`（レビュー承認は不要）
- `bypass_actors: []`

ところが CLAUDE.md は「CLI でマージする場合はセルフ PR の BLOCKED 表示回避のため `--admin` を付ける」
と定めており、実際に常用していた。`--admin` は required status check を迂回するため、**壁は建って
いるのに毎回くぐっていた**ことになる。

BLOCKED の原因を実測で探したところ、次の 2 つはいずれも原因ではなかった。

- レビュー必須: 承認数は 0 である
- `require_extra_approval_for_unattributed_changes: true`: Copilot 等が開いた、人に帰属しない PR に
  追加の承認を求める設定だが、公式ドキュメントは "This setting has no effect if the ruleset requires
  zero approvals" と明示している
  （[Available rules for rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets)）

残る有力な原因は「required status check が完了するまで BLOCKED と表示される」ことで、これは
チェックが緑になれば解ける。`--admin` を付けていたのは惰性であり、実際に必要かは確かめられて
いなかった。

## Decision（決定）

**PR は `--admin` を付けずにマージする**。required status check が緑になるのを待ってから
`gh pr merge --merge` を実行する。

この決定を記録した PR 自体を `--admin` なしでマージし、それが可能であることを実証する。

## Consequences（結果・影響）

**良くなること**

- required status check が迂回されない壁として機能する。`Run API Tests`（`./gradlew check` 相当と
  差分カバレッジ）・`CodeQL`・`Check EditorConfig Compliance` のいずれかが赤なら main に入らない

**引き受けること**

- マージ前に required のチェックが緑になるのを待つ必要がある。待ち時間は最長の `CodeQL` に支配
  され、決定時点の実測で 2〜3 分
- **required に載るワークフローは `paths` で絞れない**という制約が実効性を持つ
  （[ADR-0082](0082-ci-path-filter-rules.md)）。`--admin` で迂回している限り、この制約は表示上の
  問題に留まっていた
- 何を required にするか（DB migration / Terraform に壁が無い等）の見直しは #932

**関連**

- [ADR-0066](0066-gh-cli-via-gh-token-on-web.md) — クラウド環境から `gh` で PR マージを回す決定。
  当時は `--admin` マージを前提にしていた。マージ手段が `gh` であることは変わらない
