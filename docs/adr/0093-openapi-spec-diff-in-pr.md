# 0093. PR で OpenAPI 仕様の差分を oasdiff で可視化する（落とすかは保留）

- Status: Accepted
- Date: 2026-11-26
- Deciders: Matsui

## Context（背景・課題）

Issue #1085。PR #1068（Dependabot が上げた springdoc-openapi 3.2.0→3.2.1 と spring-ai 2.0.2→2.0.3）で、生成される OpenAPI 仕様が大きく退行したのに CI は緑のままだった。main と PR の依存で `build/openapi.json` を生成して比べると、次のように変わっていた（決定時点の実測）。

| 指標 | main | PR #1068 |
| --- | ---: | ---: |
| `required` 配列 | 39 | 3 |
| `required` の要素数 | 129 | 3 |
| `allOf`（サブタイプから `OriginDto` などの親への参照） | 10 | 0 |

原因は swagger-core 3.0 が Jackson 3 に移り、Jackson 3 用の Kotlin モジュールがクラスパスに無かったこと。Kotlin のコードを 1 行も変えずに、依存の更新だけで仕様が変わった。

これを止められるゲートは無かった。

- `openapi-lint.yml` の vacuum（[ADR-0054](0054-vacuum-openapi-lint.md)）は仕様の**品質**（記述漏れ・命名）を見る。**前の版との差分**は見ない。`required` が消えても仕様としては妥当なので通る
- `OpenApiTest` は取得の可否とタイトルしか見ない
- フロントエンドは OpenAPI から型を生成していないので、型エラーとしても表に出ない

足りないのは「この PR で仕様がどう変わったか」を見る仕組みだった。

検討した代替案:

- **仕様をコミットし、PR の git diff で見る**: 差分は最も見やすい。だが [ADR-0054](0054-vacuum-openapi-lint.md) は生成物をコミットしないと決めている。コミットすると「コードを変えたのに仕様の再生成を忘れた」という鮮度の問題が新たに生まれ、その検査がまた要る（tbls の `checkDbDoc` と同型）。採らない
- **`jq -S` で正規化した JSON の diff だけを出す**: 依存ツールが増えない。だが #1068 の生 diff は 677 行あり、どの変更が API 利用者を壊すのかは読み手が判断するしかない。差分を出すだけでなく、壊れ方を分類してほしい。単独では採らない（後述のとおり補助として残す）
- **OpenAPITools の openapi-diff（Java）**: JVM で動き、Gradle に組み込める。だが破壊的変更の分類は「互換 / 非互換」の 2 値で、どの規則に当たったかを示す ID を持たない。後で特定の変更だけを例外にする（下記の「今後の判断」）ときに困る
- **oasdiff**: Go の単一バイナリで、mise で版を固定できる。変更を規則 ID（`response-property-became-optional` 等）と重大度（ERR / WARN / INFO）で分類し、`--fail-on` で閾値を、`--err-ignore` で個別の例外を指定できる。可視化から始めて、後でゲートへ進める道が同じツールの上にある。これを採る

## Decision（決定）

PR ごとに merge-base と head の両方で仕様を生成し、その差分を Job Summary に出す。今は**可視化だけ**とし、差分や破壊的変更があってもジョブは落とさない。

- **ワークフロー**: `.github/workflows/openapi-diff.yml` を新設する。`openapi-lint.yml` には混ぜない。lint は main への push でも走るが、差分は PR でしか意味を持たない。生成も 2 回になるので、所要時間の見込み（timeout）が違う
- **比較の相手**: merge-base（PR が分岐した点）と PR の head。base ブランチの先端と比べると、分岐後に main へ入った別 PR の仕様変更まで「この PR の差分」として混ざる
- **差分の本体**: `scripts/openapi-spec-diff.sh` に切り出す（[ゲートの規約](../../.claude/rules/gates.md)。ローカルで同じ出力を再現できる）。出すものは次の 4 つ
  1. 構造の指標（`required` / `allOf` / `$ref` などの出現数）の比較。oasdiff の判定規則に頼らない素朴な網で、#1068 はこの表だけで気づける
  2. oasdiff の検出件数（重大度 × 規則 ID）
  3. oasdiff の breaking / changelog の一覧（折りたたみ）
  4. 正規化 JSON の生 diff（折りたたみ・行数上限つき）。oasdiff が拾わない変更（description のみの変更など）の受け皿
- **oasdiff の供給**: mise の `github:oasdiff/oasdiff` で版を固定する（aqua-registry に未登録のため github backend）。決定時点の版は 1.39.0（現行値の出所は `mise.toml`）。`mise.lock` には Linux を含む 6 platform のエントリをそろえ、checksum は公式の `checksums.txt` と一致することを確かめた。CI の `install_args` は backend 修飾名のまま書く
- **外部参照を取りにいかない**: oasdiff の `--allow-external-refs` は既定で true。springdoc の出力は外部 `$ref` を持たないので false にし、仕様の中身しだいで外へ通信しないようにする
- **結果の置き場所**: Job Summary。PR コメントにはしない。コメントには `pull-requests: write` が要り、fork からの PR では `pull_request` イベントのトークンが書けないため `pull_request_target` に寄せることになる。PR のコードを書き込み権限つきで動かす形になるので、可視化の段階では引き受けない
- **base 側の生成失敗では落とさない**。base の不具合は PR の責任ではない。summary にその旨を書いて緑で抜ける。head 側の生成失敗は赤にする（`openapi-lint.yml` も同じ理由で落ちるので整合する）
- 生成物はコミットしない（[ADR-0054](0054-vacuum-openapi-lint.md) を維持）。比較に使った 2 本の仕様は Artifact として 14 日残す

## Consequences（結果・影響）

- **良くなること**: 依存の更新が仕様を変えたとき、PR の Job Summary に数字と規則 ID で現れる。#1068 の head で実測すると、指標の表に `required` 39→3 / `allOf` 10→0 が出て、oasdiff は ERR 148 件（すべて `response-property-became-optional`）/ WARN 36 件（うち `response-property-all-of-removed` が 32 件）/ INFO 124 件を検出した
- **引き受けること**:
  - 生成を 2 回回すので、このジョブは lint の倍ほどかかる。required status check には載せない
  - Job Summary は、チェックの詳細を開かないと目に入らない。可視化の段階ではこの弱さを受け入れる。見落としが続くなら、下記のゲート化か PR コメントで補う
  - base 側も head 側の JDK（`.java-version`）で生成する。JDK を上げる PR では、base が新しい JDK で動かない限り比較できない（その場合は上の「base 側の生成失敗」として扱われる）

### 今後の判断: 破壊的変更で落とすか（未決定）

可視化だけでは、緑のジョブの summary を誰も開かなければ #1068 と同じことが起きうる。落とす場合の案と根拠を残す。決定はしていない。

- **案: `oasdiff breaking --fail-on ERR` で落とし、意図した変更は err-ignore ファイルで通す**
  - #1068 は ERR 148 件なので、この閾値で止まっていた（実測で exit 1）
  - WARN まで含めると（`--fail-on WARN`）`allOf` の消失も拾えるが、WARN には request 側の型変更・プロパティ削除など、API を意図して変えるときに普通に出るものが含まれる。ERR だけでも #1068 は止まるので、閾値は ERR が妥当
  - この API の利用者はリポジトリ内のフロントエンドだけで、外部クライアントはいない。意図した破壊的変更はこれからも起きる。例外の手段（`--err-ignore` に規則と対象を書く、またはラベルで迂回する）を同時に用意しないと、正当な変更が通らない
- **判断の材料にすること**: 可視化のまま何本か PR を観察し、ERR が「意図しない退行」と「意図した変更」のどちらで出ることが多いかを見る。意図した変更が大半なら、ゲートは摩擦にしかならない
- **決定時点の方針**: すぐにはゲート化せず、可視化のまま観察してから決める（2026-11-26）。観察と判断は Issue #1088 で追う
