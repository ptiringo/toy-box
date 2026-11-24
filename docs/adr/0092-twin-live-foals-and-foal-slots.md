# 0092. 双子の生産を「産駒あり」の中の印で表し、繁殖成績ごとの登録枠で頭数を縛る

- Status: Accepted
- Date: 2026-11-24
- Deciders: ptiringo

## Context（背景・課題）

実在の双子（母テスコパール、1991-03-20 生のリアルポルクス／リアルカストール。2 頭とも JRA 出走）を、モデルは表せなかった（#625）。分娩結果（`FoalingOutcome`）には「双子とも生存」が無く、replay ハーネスに流すと片方が黙って消えた。

調べると、裏表の穴がもう 1 つあった。産駒の登録（`RegisterFoalUseCase` / `RegisterBroughtInFoalUseCase`）は、同じ繁殖成績から何頭でも登録できた。双子でない分娩から 2 頭目を誤って登録しても通っていた。

方針は「制度のとおりに表す」（ユーザーの判断）。

### 典拠

登録規程実施基準（ https://www.jairs.jp/contents/pdf/tourokukiteijisshikijyun.pdf ）p.39–40、様式第14号「繁殖成績報告書」（2026-11-21 に原典で確認）。

- **表（p.39）の「産駒あり」欄**: 性・毛色・生年月日に加え「双子」の記入欄があり、「双子のもう一頭の成績については裏面に記入してください」と注記がある
- **裏（p.40）の「双子の繁殖成績」欄**: もう一頭の性・毛色・生年月日と、死亡（死産・生後直死を除く）／用途変更の事由
- 生年月日は表と裏で別々の欄になっている

つまり制度は、双子を「産駒なし」の区分（双子流産・双子死産・双子生後直死）と同じ並びの新しい区分ではなく、「産駒あり」の中の印として扱う。そのうえで、1 つの繁殖成績から登録できる産駒は双子なら 2 頭、そうでなければ 1 頭になる。

### 検討した案

- **分娩結果に双子の印を持たせるだけ**: 小さいが、「双子でなければ 1 頭まで」が守られないまま残る
- **様式の「産駒あり」欄を丸ごと写す（各産駒の性・毛色を報告時点で持ち、登録時に照合し、死亡・用途変更まで持つ）**: 双子の範囲を大きく超える
- **双子の印 ＋ 産駒から繁殖成績への逆リンクで登録頭数を縛る** ← 採用

## Decision（決定）

### 双子の生産

`FoalingOutcome.LiveFoal` に任意の `twin: TwinFoal?`（もう一頭の分娩日）を足す。新しいバリアントは作らない。双子でない生産は `twin = null` のままで、既存の報告・API・データの意味は変わらない。

`LiveFoal.foalLimit` が、その生産から登録できる産駒の上限（双子なら 2、そうでなければ 1）を表す。

### 登録枠と逆リンク

産駒（`BloodHorse`）に `bredFrom: FoalSlot?` を持たせる。`FoalSlot` は「生まれた繁殖成績」と「その中での枠」（`FoalPosition.PRIMARY` = 様式の表の産駒／`TWIN` = 裏のもう一頭）の組。繁殖成績から登録した内国産馬と持込馬だけが持ち、輸入馬・移行取り込み・直接の血統登録は `null`。

登録時、ユースケースが登録済みの枠を `BloodHorseRepository.findBredFromPositions` で読み、ドメインサービス（`registerFoal` / `registerBroughtInFoal`）に渡す。ドメインサービスは `allocateFoalSlot` で次の枠を割り当てる。

- 双子でない生産: `PRIMARY` だけ。埋まっていれば `FoalLimitReached(1)`
- 双子の生産: `PRIMARY` → `TWIN` の順。両方埋まっていれば `FoalLimitReached(2)`
- 生年月日: `PRIMARY` は分娩日、`TWIN` はもう一頭の分娩日

持込馬の登録 API（`bloodHorses:registerBroughtIn`）では、`FoalLimitReached` を 409 `foal-limit-reached`（`limit` 付き）にする。国内種付の産駒登録には今は API が無い（#989）。

### DB での多層防御（V28 / V29）

- `breeding_result.outcome_twin_foaling_date`: 生産（`LIVE_FOAL`）のときだけ持てる（CHECK）
- `blood_horse.bred_from_breeding_result_id` / `bred_from_position`: 2 列は揃って在るか揃って無い、枠は `PRIMARY` / `TWIN` のみ、持てるのは内国産と持込馬だけ（CHECK）。世界スコープの複合 FK を張る
- **UNIQUE `(world_id, bred_from_breeding_result_id, bred_from_position)`**: 同じ繁殖成績の同じ枠に 2 頭は入らない。アプリを通らない書き込みでも、1 つの繁殖成績に 3 頭以上は入らない。双子でない生産で `TWIN` を使わないことは、アプリが守る（DB は分娩結果を見ない）

## Consequences（結果・影響）

- 実在の双子を 2 頭とも登録できる（replay の 03-twins で 2 頭とも登録・命名まで通る）。
- 双子でない分娩から 2 頭目を登録する誤りは、繁殖成績を経る登録経路では防げる。

引き受けたこと:

- **直接の血統登録は頭数を数えない**: `POST /bloodHorses`（`RegisterInStudBookUseCase`）は父母と生年月日を直接受け取り、繁殖成績を経ないので、`bredFrom` を持たず頭数の制限が効かない。同じ母の同じ年の産駒を、この経路からなら何頭でも登録できる。塞ぐには「直接登録をやめるか、繁殖成績を引き当てさせるか」という別の設計判断が要るので、別の Issue で扱う。
- **既存行の逆リンクは埋めない**: 既に繁殖成績から登録された馬（主に持込馬）は `bredFrom = null` のまま。その繁殖成績では枠が空いて見え、もう 1 頭登録できてしまう。本番は sandbox のデータで実害がほぼ無いので許容した。
- **生年月日が違う双子の枠は登録順**: 申請者は枠を指定せず、先に登録した方が `PRIMARY` になる。様式上は表と裏で生年月日が違いうるので、2 頭目を先に登録すると生年月日が入れ替わる。稀なので許容した。
- **双子のうち 1 頭だけ生存**: 様式上の書き方が原典から読み切れない（裏の死亡欄は「死産・生後直死を除く」）。上限は上限であって 2 頭の登録を強制しないので、双子の生産として報告して 1 頭だけ登録することは許す。
- **並行登録**: 2 つの登録が同時に同じ枠を取りに行くと、片方は UNIQUE 違反の例外になり 500 になる。血統登録番号の一意性（V22）と同じ扱いで、今は手当てしない。
- **年次集計の生産数**: `BreedingResultSummaryView` の生産数（`live_foals`）は、双子の生産も 1 と数える。登録子馬数と一緒に #457 で見直す。
- **様式の残り**: 「産駒あり」各産駒の死亡・用途変更、「産駒なし」の 9 区分目「不明」（表にだけある。#634）は扱っていない。

関連: [ADR-0016](0016-not-covered-as-foaling-outcome-variant.md) / [ADR-0018](0018-uncovered-via-discriminated-single-create.md) / [ADR-0020](0020-sealed-origin-and-discriminated-origin-subobject.md) / [ADR-0022](0022-domain-service-repository-for-set-invariants.md) / [ADR-0087](0087-brought-in-horse-modeling.md)、#457、#634、#989
