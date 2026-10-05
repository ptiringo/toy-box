# 0085. dprint プラグインを npm 指定子でピンし、更新時に公開後 7 日の cooldown を置く

- Status: Accepted
- Date: 2026-10-05
- Deciders: Matsui

## Context（背景・課題）

[ADR-0063](0063-dprint-config-file-formatting.md) は dprint プラグインを `plugins.dprint.dev` の版付き Wasm URL＋SHA-256（`url@<sha256>`）で `dprint.json` にピンすると決めた。dprint 本体を 0.60.1 へ追従する作業（#948）で、次の 2 点が分かった。

- **`dprint config update` が npm 指定子で書き出す**。決定時点の dprint では、更新コマンドは plugins を `npm:@dprint/json@<version>@<sha256>` の形（npm レジストリ配布）に書き換える。URL 形式を維持すると、更新のたびに URL へ手で書き戻し、sha256 を別途取得する手作業が要る。dprint 本体も 0.60.0 で npm プラグインのプロキシ設定対応を入れるなど、npm 配布を主経路として扱っている。
- **cooldown が npm プラグインにだけ効く**。`dprint config update --minimum-dependency-age <期間>` は「公開から日が浅い版を選ばない」オプションで、ヘルプ上 npm プラグインの版解決にのみ作用する。リポジトリは Dependabot に cooldown を置き、リリース直後の欠陥版を掴むリスクを避ける方針を取っている（#716、`.github/dependabot.yml`）。URL 形式のままではこの方針を dprint プラグインに適用できない。

検討した代替案:

- **URL 形式を維持する**: ADR-0063 を変えずに済むが、更新の手作業が残り、cooldown も適用できない。却下。
- **npm 指定子に移るが cooldown は置かない**: 手作業は消えるが、素の `config update` は最新版を選ぶ（#948 時点では前日公開の版を選んだ）。Dependabot 側の方針と食い違うため却下。

## Decision（決定）

- `dprint.json` の plugins は **npm 指定子＋SHA-256**（`npm:<package>@<version>@<sha256>`）でピンする。ADR-0063 の「版＋ハッシュで固定する」原則は維持し、取得元だけを `plugins.dprint.dev` から npm レジストリに替える。
- プラグインの更新は **`mise run dprint-update`**（`dprint config update --minimum-dependency-age P7D --yes`）で行う。決定時点の cooldown は 7 日で、Dependabot の minor 相当の値に揃えた。現行値の出所は `mise.toml` の `[tasks.dprint-update]`。
- cooldown はプラグインにだけ掛ける。dprint 本体（`mise.toml` の `aqua:dprint/dprint`）は mise に同等の仕組みがないため対象外とする。

## Consequences（結果・影響）

- プラグインの追従がコマンド 1 回で済み、出力をそのままコミットできる。
- 「出たばかりの版を掴まない」方針が Dependabot と dprint プラグインで揃う。代わりに、上流の修正版を最大 7 日待つことになる。急ぐ修正は `dprint config update --minimum-dependency-age=0` で個別に取り込む。
- 取得元が npm になるため、npm の公開アカウント乗っ取りによる悪性版の混入が新たなリスクになる。すでにピンした版のすり替えは SHA-256 が検知し、新しい版を掴むリスクは cooldown が緩和する。
- devcontainer の許可ドメイン（`.devcontainer/allowed-domains.txt`）には `registry.npmjs.org` が既にあり、`plugins.dprint.dev` は無かった。npm 指定子への移行で devcontainer からもプラグインを取得できる。sandbox 等で取得先ホストを絞っている各自のローカル環境では、許可先に `registry.npmjs.org` が要る。
- ADR-0063 のうち「プラグイン供給と再現性」の取得元の記述を本 ADR が改訂する。それ以外（採用ツール・スコープ・ゲートの場所）は ADR-0063 のまま有効。
