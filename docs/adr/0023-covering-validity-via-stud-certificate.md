# 0023. 種付の有効性（種畜証明書の有効区域・有効期間）をファクトリの段階導入前提条件として検証する

- Status: Accepted
- Date: 2026-06-24
- Deciders: Matsui

## Context（背景・課題）

登録規程実施基準・第9条第1項(1) は、種畜証明書を有する種雄馬の種付による産駒として血統登録できるのは、その種付が
**証明書に記載された有効区域内かつ有効期間内**に行われた場合に限る、と定める（issue #316）。これを軽種馬登録ドメインに
取り込むにあたり、いくつか決めるべき点があった。

- **種畜証明書（種畜証明書）と種付証明書（`CoveringCertificateNumber`）の区別**。後者は既存で「種付の事実」を証する書面。
  前者は種雄馬が繁殖に供されることを証し、有効区域・有効期間を記載する**別書面**。両者を混同しないモデルが要る。
- **有効区域の表現**。一次資料には区域の具体的な分類体系（都道府県・全国など）が明示されていない。enum で固定区分を切るのは
  時期尚早で誤りを招く。
- **検証の置き場所**。本プロジェクトは「構築時に協力与件を引数で受け取れる前提条件はファクトリが自己検証する」方針
  （[ADR-0014](0014-self-validating-factory-over-confinement.md)）と、「集合制約（一意性等）はリポジトリポートを取る
  ドメインサービスが検証する」方針（[ADR-0022](0022-domain-service-repository-for-set-invariants.md)）を持つ。種付の
  有効性はどちらかを判断する必要がある。
- **既存の記録経路を壊さない**。`recordCovering` ドメインサービスは application 層（`RecordCoveringUseCase`）と Controller から
  呼ばれており、API 入口はまだ種畜証明書・種付場所を供給しない。今回のスコープはドメイン＋サービスに限り、app/controller の
  本格配線は別 PR とする。

検討した代替案:

- **有効区域を enum で固定**。一次資料の区分が不明なため却下（誤った語彙を固定するリスク）。
- **検証をドメインサービス `recordCovering` に置く**。種付の有効性は既存成績集合への問い合わせを要さず、協力与件（種畜証明書）
  を引数で受け取れば構築時に完結する。集合制約ではないため ADR-0022 の対象ではなく、ADR-0014 のファクトリ自己検証が適切。
- **新引数を必須にする**。`BreedingResult.create` / `recordCovering` の署名を破壊し app/controller が即コンパイル不能になる。
  スコープ（ドメイン＋サービス）を超えるため却下。

## Decision（決定）

1. **種畜証明書を値オブジェクト `StudCertificate`（番号 `StudCertificateNumber` / 有効区域 `validRegions` / 有効期間
   `ValidityPeriod`）としてモデル化する**。種付の事実を証する `CoveringCertificateNumber`（種付証明書）とは別型とする。
2. **有効区域は `BreedingRegion`（名前付きの値）の集合で表し、有効性は集合メンバーシップで判定する**（種付場所が
   `validRegions` に含まれるか）。「全国」が個別区域を包含するといった**区域の包含関係はモデル対象外**とし、区分体系が
   判明したら具体化する。
3. **有効性検証はファクトリ `BreedingResult.create` の構築時前提条件として自己検証する**（ADR-0014）。検証本体は
   `StudCertificate.authorizes(coveringDate, coveringPlace)` が担い、有効期間外＝`OutsideValidPeriod` / 有効区域外＝
   `OutsideValidRegion`（`CoveringValidityError`）を返す。ファクトリはこれを `RecordCoveringError.InvalidCovering` に
   包んで返す。`recordCovering` ドメインサービスは種畜証明書・種付場所を**素通し**するだけで、集合制約（種付年の一意性）は
   従来どおり自身が検証する。
4. **段階導入とする**。`BreedingResult.create` / `recordCovering` の種畜証明書・種付場所引数は省略可能（デフォルト null）とし、
   渡されたときだけ有効性を検証する。これにより API 入口が証明書を供給しない現状でも既存の記録経路は無改修でコンパイル・動作する。
   `Covering.coveringPlace` も nullable とし、供給された記録でのみ場所を保持する。API が種畜証明書・種付場所を供給する配線が
   整い次第、必須化する（エラー描画の段階的導入方針 `error-handling.md` と整合）。

## Consequences（結果・影響）

- 種畜証明書と種付証明書が型で分離され、用語集（`docs/ubiquitous-language.md`）にも両者の区別を明記した。`StudCertificate` /
  `StudCertificateNumber` / `ValidityPeriod` / `BreedingRegion` が型レベル用語カタログに追加される。
- 有効性検証はロール検証と同じファクトリに同居し、`RecordCoveringError` の語彙が 3 系統（ロール・有効性・一意性）に揃った。
  Controller の problem 変換は有効性違反を 422 Unprocessable Entity として描画する（整った入力だが意味的に処理できない。
  登録ロール違反と同じ扱い。`api-design.md`）。
- 段階導入の代償として、種畜証明書・種付場所が**省略可能**であり `Covering.coveringPlace` が nullable である。これは「API が
  まだ供給しない」ことに対する過渡的措置であり、供給配線（別 PR）で必須化する前提。それまで有効性検証は呼び出し側が証明書を
  渡したときのみ働く。
- 有効区域の包含関係（全国 ⊇ 個別区域）と、種畜証明書番号・種付証明書番号の桁数/形式検証は本 ADR のスコープ外。区分体系・形式が
  判明した時点で具体化する。
