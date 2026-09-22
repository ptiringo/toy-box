# 0082. CI の paths フィルタは required なワークフローに掛けず、掛けるなら denylist で自身を含める

- Status: Accepted
- Date: 2026-09-22
- Deciders: Matsui

## Context（背景・課題）

Issue #819。Kotlin を 1 行も触っていない PR でも Kotlin 系の重いジョブが走る。実測では Actions 時間が
約 9 分、待ち時間が 2〜3 分（最長の CodeQL に支配される）で、PR #816 / #825 / #830 / #887 / #893 /
#924 と繰り返し観測された。`paths` で絞るかを判断する必要があった。

判断を左右した事実は 4 つある。

**1. denylist と allowlist は壊れ方が逆向き**（#784 と同型）。`paths-ignore`（denylist）は新しい
パスが増えると余計に走る向きに壊れ、`paths`（allowlist）は走らない向き＝検査漏れに壊れる。PR #825
の実測（Markdown 1 ファイルの PR）では、`paths-ignore` を持つ Browser E2E / Container Smoke Test は
正しくスキップされており、denylist 方式そのものは機能していた。PR #816 で一緒に走ったのは列挙漏れで
あって、方式の失敗ではない。

**2. 走らなさすぎの穴には実害が出ている**。`paths` を持つワークフローのうち `sql-check` /
`db-doc-check` / `terraform-check` 等は自分自身を `paths` に含んでおらず、そのワークフローを変更した
PR でそのワークフローが走らなかった。その結果 `sql-check` は壊れたまま 17 日間 main に残っていた
（#876）。対照的に、`paths` を持たない `editorconfig-check` の破損は翌日に全 PR が赤くなって即発覚
した（#878）。

**3. required status check は `paths` で絞れない**。main の ruleset は決定時点で `Run API Tests` /
`Check EditorConfig Compliance` / `CodeQL` を required にしている（現行値の出所はリポジトリ設定の
ruleset `main`）。GitHub は `paths` で起動しなかったチェックを pending のまま残し、マージをブロック
する。公式の推奨は "Avoid requiring workflows that can be skipped."
（[Troubleshooting required status checks](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/collaborating-on-repositories-with-code-quality-features/troubleshooting-required-status-checks)）。
当初は `api-tests` / `codeql` / `e2e-tests` の 3 本すべてに `paths-ignore` を入れる設計だったが、
ruleset を実測して前 2 本が required であると分かり、撤回した。

**4. 絞る動機は起動面積に純化した**。「触っていない領域のジョブが無関係なツールの上流障害を拾う」と
いう論拠（PR #893 / #924）は、#917（PR #923）で CI から `mise exec` を外したことで消えた。残るのは
Actions 時間と待ち時間だけである。

また `editorconfig-check` は、#880 の `mise.lock` drift 検知が「全 PR で起動すること」に依存して
いる（lock 関連ファイルを触らない PR で書き換えが起きるため）。

### 検討した代替案

- **allowlist へ反転する**: 壊れ方が検査漏れ側に倒れる。pre-push の glob（allowlist 的）との二重
  管理（#920）も増える。#784 と判断を揃える観点からも採らない
- **何もしない**: 走らなさすぎの穴（事実 2）の実害が出ており、放置できない
- **job 内判定**: トリガの `paths` を job の最初のステップ（`git diff --name-only`）へ移し、対象外
  なら後続をスキップして success で終える。常に起動するので required にでき、絞りと両立する。
  ただし全ワークフローへの手入れと判定ロジックの二重管理を生む。何を required にするかの見直し
  （#932）とセットで判断すべきなので、ここでは採らない

## Decision（決定）

1. **required status check に載るワークフローは `paths` / `paths-ignore` で絞らない**
2. **絞るときは denylist（`paths-ignore`）を使い、allowlist（`paths`）へは反転しない**
3. **絞るワークフローの除外集合は揃える**: `**/*.md` / `docs/**` / `.claude/**` / `infra/**`。
   ジョブ固有の追加は認める（`container-smoke-test` の `src/test/**`）。`scripts/**` と
   `.github/workflows/**` は除外しない。CI の挙動を決めるファイルなので、触った PR では走らせる
4. **`paths`（allowlist）を持つワークフローは自分自身（`.github/workflows/<name>.yml`）を含める**
5. **`editorconfig-check` は絞らない**（#880 の drift 検知が全 PR 起動に依存する）

決定時点での適用は次のとおり。

| ワークフロー | 変更 |
|---|---|
| `e2e-tests` | `paths-ignore` を新設（`E2E` は required でない） |
| `browser-e2e` | 除外集合に `.claude/**` を追加 |
| `container-smoke-test` | 除外集合に `docs/**` を追加 |
| `adr-check` / `db-doc-check` / `sql-check` / `terraform-check` / `markdown-link-check` | `paths` に自分自身を追加 |
| `api-tests` / `codeql` | 絞らない。理由をトリガの直上にコメントで残す |

`mise-lock-check` は #917 で `.github/workflows/**` を `paths` に含めており、自分自身は既に対象だった。

## Consequences（結果・影響）

**良くなること**

- ドキュメント・Claude 指示・インフラだけの PR で `E2E` / `Browser E2E` / `Container Smoke Test` が
  走らない
- ワークフローを直した PR で、そのワークフローが走る。#876 型の「壊れたまま気づかない」を防ぐ
- 絞る 3 本の除外集合が揃い、列挙漏れが起きにくくなる

**引き受けること**

- **`Run API Tests` と `CodeQL` は絞れないので、Kotlin を触らない PR でも走り続ける**。#819 の元の
  論点（待ち時間 2〜3 分）の大半は残る。これは required を壁として維持する対価である
- **required の構成と `paths` の設計が連動する**。ワークフローを required に足すときはその `paths`
  を外す必要があり、required から外せば絞れるようになる。何を required にするかは #932
- `db-doc-check` の `paths` と pre-push の glob は同じ集合を保つ運用だったが（#918）、自分自身の
  1 行だけは CI 側にしか持たない。ローカルの `checkDbDoc` はワークフロー定義を検証しないため。
  二重管理そのものは #920

**関連**

- [ADR-0076](0076-browser-e2e-playwright-auth-emulator-boottestrun.md) — `browser-e2e` の除外集合を
  「`docs/**` / `infra/**` / `*.md`」と定めた。本 ADR で `.claude/**` を加えて拡張する（`frontend/**`
  に絞らないという主旨はそのまま）
- [ADR-0083](0083-merge-without-admin-bypass.md) — required を `--admin` で迂回しない。本 ADR が
  「required は絞れない」を制約として扱う前提
