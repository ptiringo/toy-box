# 0024. horseracing を studbook（JAIRS 登録）と racing（JRA 騎手・競走）の 2 コンテキストへ分割する

- Status: Accepted
- Date: 2026-06-24
- Deciders: Matsui

## Context（背景・課題）

[ADR-0013](0013-racehorse-registration-as-separate-context.md) で、**競走馬登録(JRA)＝ JAIRS 中心ドメインとは別の境界づけられたコンテキスト**であることを確定した。JAIRS（ジャパン・スタッドブック・インターナショナル）管掌の血統登録・馬名登録・繁殖登録と、JRA 管掌の競走馬登録・出走・番組は管掌組織も登録原簿も別であり、コンテキスト境界を一次資料の管掌区分に一致させる、という判断である。

この境界を**現行のパッケージ構成**に当てると、`horseracing` という名前と中身が二重にずれていることが顕在化した。

1. **名前が広すぎる**: 現行 `horseracing` の実体は JAIRS の軽種馬登録（血統登録 / 馬名登録 / 繁殖登録）に閉じている。一方 "horse racing"（中央競馬・競走馬登録・出走・番組）は ADR-0013 が JRA 側に割り当てた概念で、将来 `racing`(JRA) コンテキストへ属する。つまり `horseracing` は JRA 側に取られるべき名前を、JAIRS の実体が借りている状態だった。
2. **JRA 概念がねじれて同居している**: 現 `horseracing/model` 配下に、JAIRS 管掌でない概念が混ざっている。

| サブパッケージ | 実体 | 管掌 | あるべきコンテキスト |
|---|---|---|---|
| `horse/bloodhorse` | 血統登録 | JAIRS | studbook |
| `breeding` | 繁殖登録・繁殖成績 | JAIRS | studbook |
| `jockey` | 騎手（騎手免許は競馬法で JRA 管掌） | **JRA** | racing |
| `race` | 競走・成績 | **JRA** | racing |

騎手は「馬の登録原簿」とは無関係（騎手免許は人に対する JRA の免許であり JAIRS の管掌外）で、`race` ともども本来 JRA 側の概念である。このまま `horseracing → studbook` へ単純リネームすると、`jockey` / `race` が `studbook` に取り残され、かえって不正確になる（今の曖昧な `horseracing` より悪化する）。

`racing`(JRA) の本実装（競走馬登録など）に着手する**前に**名前と境界を整えておくと、名前の取り合いが起きず、JAIRS 側が綺麗に閉じる。

### 検討した代替案

- **(却下) `horseracing → studbook` の機械的リネームのみ（jockey/race は studbook に残す）**。JRA 管掌の `jockey` / `race` を「軽種馬登録原簿」を意味する `studbook` の中に置くことになり、ADR-0013 で確定した管掌区分の境界と矛盾する。曖昧さを別の不正確さに置き換えるだけ。
- **(却下) リネームせず `horseracing` のまま据え置く**。`racing`(JRA) を新設する段で `horseracing` という名前が JRA 側と衝突し、結局このタイミングで解く問題を先送りするだけ。
- **(却下) JAIRS 側を `keishuba` / `registration` / `breedingregistry` と命名する**。`studbook`（血統書＝登録原簿）は JAIRS の英名（Japan Stud Book International）および #320 の Stud Book 編纂と符合し、ADR-0013 の語彙とも一貫する。`registration` は racing 側の競走馬「登録」とも被り、`keishuba` は和語のローマ字で他コンテキスト名（英語）と不揃い。

## Decision（決定）

**現行 `horseracing` コンテキストを、管掌区分に沿って 2 つの境界づけられたコンテキストへ分割する。**

1. **JAIRS 側（血統登録・繁殖登録）→ `studbook`**: `horseracing.{horse, breeding}` を `studbook` へリネームする。`studbook` は JAIRS 管掌の軽種馬登録（血統登録・馬名登録・繁殖登録・繁殖成績報告）を担う、本プロジェクトの参考実装コンテキストである。
2. **JRA 側（騎手・競走）→ `racing`（新設）**: `horseracing.{jockey, race}` を新設 `racing` コンテキストへ移す。ADR-0013 では「`racing` は競走馬登録を実装するとき新設」としていたが、`jockey` / `race` の正しい置き場として**先に器だけ作る**。`racing` は ArchUnit のコンテキスト分離対象（layer 直下のサブパッケージ名で判定）に自動で乗る。

この分割は **ADR-0013 が概念として確定した JAIRS/JRA 境界を、パッケージ構成へ適用する**ものである。ADR-0013 は「`jockey` / `race` が今ねじれた側にいる」とは名指ししていなかったため、その対応付け（騎手・競走＝JRA 側）を本 ADR で明示して確定する。ADR-0013 は引き続き有効（Supersede しない）。

- 対象は `domain` / `application` / `infrastructure` 各層の `horseracing` パッケージのリネーム＋ split。ロジック変更を伴わない機械的移送が中心。
- `controller` 層はコンテキスト分割の対象外（単一のアダプタリングであり、ArchUnit のコンテキスト判定は `application` / `domain` / `infrastructure` 直下のパッケージ名のみを見る）。`controller/jockey` 等のサブパッケージはフラットなまま据え置く。
- Kover の mature ゲート対象（`build.gradle.kts` の `variant("mature")` includes）と `.claude/rules/testing.md` のゲート対象記述も、リネーム後のパッケージ名（`studbook` / `racing.jockey`）へ更新する。
- `racing` の本格的なドメイン（競走馬登録など）の設計は本 ADR の対象外。ADR-0013 の通り別途の判断に委ねる。

## Consequences（結果・影響）

- **良くなる点**: コンテキスト名が管掌区分（JAIRS=studbook / JRA=racing）と一致し、`studbook` が JAIRS 登録に閉じる。`racing`(JRA) を本実装する段で `horseracing` という名前の取り合いが起きない。`studbook` ⇔ `racing` 間に相互参照は存在しないため、分割で新たなコンテキスト分離違反は生じない（移送前に確認済み）。
- **引き受けるトレードオフ**: 既存 ADR（0006 / 0009 / 0010 / 0013 / 0020 / 0022 等）の本文には旧パッケージ名 `horseracing` が残る。ADR は決定時点の記録として改訂しない方針のため、これらは本 ADR を読み替えの起点とする（`horseracing.{horse,breeding}` → `studbook`、`horseracing.{jockey,race}` → `racing`）。
- **影響範囲**: `jockey` は実装が最も厚く（controller / application / infrastructure / repository / テスト）かつ Kover mature ゲート対象のため、移動に伴いゲート定義の更新が要る。`race` はスタブ（`RaceResult.OrderOfFinish` が `Nothing` の TODO）で移動コストはほぼない。
- **関連**: 概念境界の典拠は [ADR-0013](0013-racehorse-registration-as-separate-context.md) と #351（JRA 一次資料調査）。#320（Stud Book 編纂・登録原簿）と命名が符合。実施は #354。
