---
name: tennis-sources
description: テニス（tennis コンテキスト）、とくに ATP の選手登録・ランキング資格をモデリングするときの権威ソースマップと一次資料の読み方。ATP/ITF/WTA の役割分担（どの登録がどの機関の管掌か）を確認したいとき、ルールブックの該当章の所在を知りたいとき、選手登録・エントリー資格・ランキング算定など「制度の事実」を典拠付きで確定的に書く前に使う。「ATP の典拠」「ルールブックのどこ」「IPIN とは」「選手登録は誰の管掌」「ランキング資格の根拠」等が合図。確定した不変条件そのものはここに転記せず Issue/ADR/用語集へのリンクで辿る。
---

# tennis-sources（テニスの権威ソースマップと一次資料の読み方）

`tennis`（スポーツ）コンテキストで、とくに **ATP の選手登録／ランキング資格** をモデリングするときの
**権威ソースの所在と読み方** を集約する。制度の事実を典拠付きで書く前に、ここから原典（ルールブック）に当たる。

軽種馬登録の [keishuba-sources](../keishuba-sources/SKILL.md) と役割は同じ。テニスは ATP/ITF/WTA が
それぞれ **公式ルールブック** という一次資料を持つため、JAIRS に近い「典拠マップ＋一次資料の読み方」が取れる。

このスキルの役割は **「どこに・何が・どう読めば書いてあるか」までで、確定した不変条件そのものは持たない**。
登録要件・資格・ランキング算定などの検証済み知見は、原典改訂やモデル変更で乖離しないよう Issue / ADR / 用語集を
唯一の出所とし、ここからはリンクで辿る（後述「検証済み知見の所在」）。tennis は現状 `Player` / `PlayerId` のみの
**探索段階**で、確定した不変条件はまだ薄い。

> **大原則**: 選手登録・エントリー資格・ランキング算定など制度の事実を確定的に書く前に、WebSearch の要約や
> 自分の記憶を鵜呑みにせず **公式ルールブックの条番号・章・ページまで確認してから書く**。ルールブックは毎年改訂され、
> 条番号も動く。LLM の記憶は年度・条番号・機関の管掌を取り違えやすい。WebSearch は探索の方向当たりに留める。

---

## 1. 権威ソースの区別（混同しやすい・重要）

テニスには単一の「登録機関」が無く、**3 機関が層で分担**する。JAIRS/JBBA/JRA の区別に相当する要点。

| 機関 | 正式 | 役割 | このドメインでの扱い |
| --- | --- | --- | --- |
| **ATP** | ATP Tour, Inc.（男子プロツアー） | ATP Tour / ATP Challenger Tour の運営。**ATP 大会のエントリー資格＝会員登録**（ATP Player Member / ATP Registered Player）、**PIF ATP Rankings** の算定 | **本スキルの主対象**。「選手登録／ランキング資格」の中核 |
| **ITF** | International Tennis Federation（国際テニス連盟・世界統括団体） | **IPIN（選手の生涯不変の識別・登録）**、World Tennis Tour（プロ入口の下位サーキット。ATP/WTA へ接続）、Juniors / Seniors / Wheelchair、Rules of Tennis（競技規則本体）、アンチドーピング、グランドスラム規程・Davis Cup / BJK Cup・五輪 | 境界の最小参照。ただし **選手の「登録＝身元」は ITF の IPIN が起点**（下記の 2 層に注意） |
| **WTA** | WTA Tour（女子プロツアー） | 女子ツアーの運営・WTA Rankings・WTA Rulebook | 境界の最小参照。男女で管掌機関が分かれる点の確認用 |

### 「選手登録」は 2 層ある（最重要の区別）

「選手登録」を一語で潰さない。JAIRS の「血統登録（身元）」と JRA の「競走馬登録（出走資格）」が別レイヤーなのと同型:

- **身元の登録（ITF / IPIN）**: プロ入口で取得する **生涯不変の一意 ID**。ITF 系サーキット（World Tennis Tour /
  Juniors / Seniors / Wheelchair）で必須。1 選手 1 IPIN を生涯保持。
- **ツアー会員登録（ATP / WTA）**: ATP/WTA の大会にエントリーするための **会員資格**（ATP なら Player Member /
  Registered Player）。ランキング上位到達で Commitment 等の義務が発生。

> この 2 層をどうモデルに写すか（同一 `Player` の別ロールか、別集約か、IPIN を identity にするか等）は
> **モデリングの設計判断**。記憶で断定せず、原典で管掌を確認し、判断は ADR / Issue に残す。

---

## 2. 典拠マップ

### ATP（主対象）

入口 = `https://www.atptour.com/en/corporate/rulebook`（全文 PDF＋章別 PDF＋目次／索引／改訂差分 PDF を配布）。
章別 PDF は `https://www.atptour.com/-/media/files/rulebook/<年>/2026-rulebook-chapter-<n>_...pdf` の形。
**ファイル名末尾に改訂日スタンプ（`_19dec25` 等）が付き改訂で変わる**ので、直リンクをハードコードせず入口ページ／
WebSearch で現行 URL を引き直す（URL 構成は 2026 年版・2026-07 確認時点）。

| 章（ローマ数字） | 章タイトル | このドメインで見るところ |
| --- | --- | --- |
| **I** | ATP Circuit Regulations | **選手登録の核心**。`1.01` 大会区分／**`1.07` Commitment, Membership Obligations and Bonus Pool**（A. Player Entry and Commitment to Rules → **A.6) が「ATP Player Member か ATP Registered Player でなければ ATP Tour / Challenger Tour にエントリー不可」＝登録要件**） |
| II | Branding | （スコープ外） |
| III | Financial | 賞金・手数料・Commitment の構成 |
| IV | World Championships | Nitto ATP Finals 等の出場資格 |
| VII | The Competition | `7.01` ATP Fees / **Entry Fees**（エントリー手続・料金） |
| VIII | The Code | 行動規範・ペナルティ |
| **IX** | **PIF ATP Rankings** | **ランキング算定の核心**。`9.01` Definitions ほか（p.267〜） |

- 未確認の章（V・VI 等）は上記に載せていない。全章立ては目次 PDF（`2026-rulebook-toc_...pdf`）／索引 PDF
  （`2026-rulebook-index_...pdf`）で確認し、**推測で章タイトルを埋めない**。
- ATP 全文ルールブックは ITF もミラー配布している（`https://www.itftennis.com/media/15604/atp-2026-rulebook.pdf`）。

### ITF（境界の最小参照＋IPIN の起点）

- 規程一覧（governance）: `https://www.itftennis.com/en/about-us/governance/rules-and-regulations/`
- IPIN の説明: `https://www.itftennis.com/en/about-us/organisation/about-ipin/` ／ IPIN ポータル `https://ipin.itftennis.com/`
- IPIN FAQ（全サーキット）: `https://www.itftennis.com/media/10144/ipin-faqs-all-circuits.pdf`
- World Tennis Tour 規程（プロ入口サーキット）: `https://www.itftennis.com/media/15546/2026-wtt-regulations.pdf`
- グランドスラム規程: `https://www.itftennis.com/media/5986/grand-slam-rulebook-2026-f2.pdf`

### WTA（境界の最小参照）

- 規程入口: `https://www.wtatennis.com/wta-rules`（年版 WTA Rulebook PDF を配布。PDF は `photoresources.wtatennis.com` 配下）

---

## 3. 一次資料の読み方（取得手順）

**PDF 自体は組版由来のテキスト PDF**（JAIRS の CID 画像 PDF と違い、入手できれば `pdftotext` でクリーンに抽出できる）。
ネックは抽出ではなく **取得**にある。

- **`atptour.com` / `wtatennis.com` / `itftennis.com` は WebFetch も素の `curl` も 403 で弾く**（CDN / bot 保護）。
  「破損」ではなく到達不可。JAIRS（CID で読めない）とは失敗の原因が異なる点に注意。
- **WebSearch は Anthropic 経由で到達**し、現行 URL・章構成・条番号・要約を返す。**方向当たりと URL 特定はこれで行う**
  （要約は鵜呑みにせず、確定記述は原典の条番号・ページで裏取り）。
- **全文を読みたいとき**: これらのホストを各自のローカル設定（`.claude/settings.local.json` 等）の許可ホストに足して
  取得し、`pdftotext` で抽出する（テキスト PDF なので画像化は不要）。**許可ホストは共有ファイルに書かない**
  （CLAUDE.md「Claude 指示ファイル・スキルの記述方針」）。`pdftotext` が無ければ別途インストールする。
- **章単位で読む**: ATP は章別 PDF が分かれているので、選手登録なら Chapter I、ランキングなら Chapter IX と
  必要章だけ取得すれば足りる（全文は数百ページ）。改訂差分は `...rulebook-changes_...pdf` で追える。

---

## 4. 用語の区別（混同しやすい）

| 用語 | 意味 | 混同しやすい点 |
| --- | --- | --- |
| ATP / WTA / ITF | 男子ツアー / 女子ツアー / 国際連盟 | 「登録」の管掌が層で分かれる（§1）。ATP と ITF を同一視しない |
| IPIN | ITF 発行の生涯不変の選手識別・登録番号 | ATP/WTA の会員登録とは別レイヤー（身元 vs ツアー会員） |
| Player Member / Registered Player | ATP の会員区分（大会エントリー資格） | ITF の IPIN と混同しない |
| ATP Ranking（PIF ATP Rankings） | ATP の週次ランキング | WTA Ranking・ITF の各サーキットの順位と別体系 |
| Commitment Player | 上位（例: Top 30）に課される出場義務対象 | ランキングの派生概念。恒久属性ではなく年度・順位で変動 |
| World Tennis Tour | ITF のプロ入口サーキット（旧 ITF Pro Circuit） | ATP Tour / ATP Challenger Tour と別。ここから上位ツアーへ接続 |

- 「選手登録」を一語で書かない（§1「2 層」を明示）。
- 年度・条番号は改訂で動く。**「〇〇年版・第 x.xx 条」まで添える**（機関名だけ・条番号なしで断定しない）。

---

## 5. 検証済み知見の所在（リンクで辿る・転記しない）

確定した語彙・不変条件・モデリング判断は、ここに転記すると乖離するため **下記が唯一の出所**。
書く前に突き合わせ、足りなければ原典に当たって更新する。

- **用語の定義・別名**: `docs/ubiquitous-language.md`（tennis コンテキスト節）。現状は **探索段階で `Player` /
  `PlayerId` のみ**。ビルディングブロックは「コードが唯一の出所」で `UbiquitousLanguageCatalogTest` が同期を担保。
- **モデリングの題材・段階・不変条件の探索**: GitHub Issue。`gh issue view <n>` で参照。
  - **#363** tennis コンテキストのドメインモデリングに着手（選手登録・大会開催を軸に）
  - **#365** プロテニス選手の登録をモデル化する
  - **#366** テニス大会の開催をモデル化する
  - **#407** 本スキルの起票元
- **コンテキスト分割の判断（先行事例）**: ADR-0013（競走馬登録を別コンテキストに）／ADR-0024（studbook と racing の
  分割）。tennis を独立した境界づけられたコンテキストとして保つ根拠の型。詳細は `.claude/rules/architecture.md`。

---

## やってはいけないこと

- 記憶や WebSearch 要約だけで登録要件・エントリー資格・ランキング算定を確定的に書く（原典の年度・条番号・ページで裏取り）。
- ATP / ITF / WTA の管掌を混同する（男女でツアーが分かれ、身元登録は ITF の IPIN、ツアー会員は ATP/WTA）。
- 「選手登録」を 1 レイヤーに潰す（IPIN＝身元 と ATP 会員＝ツアー資格 は別。§1）。
- 未確認の章タイトル・条番号を規則性から推測して埋める（目次／索引 PDF で確認する）。
- 改訂日スタンプ付きの PDF 直リンクをハードコードして「リンク切れ」と諦める（入口ページ／WebSearch で引き直す）。
- これらのホストを WebFetch / 素の `curl` で取ろうとして「到達不可」で止まる（WebSearch で方向当たり＋現行 URL 特定、全文は各自のローカル許可ホストで取得して `pdftotext`）。
- 確定した不変条件をこのスキルに転記して二重管理する（用語集・ADR・Issue へリンクで逃がす）。
