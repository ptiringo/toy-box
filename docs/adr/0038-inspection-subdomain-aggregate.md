# 0038. 審査（個体識別・親子判定）を独立集約とし識別子の出所を審査側へ一本化する

- Status: Accepted
- Date: 2026-06-29
- Deciders: Matsui

## Context（背景・課題）

血統登録集約 `BloodHorse` は、登録馬の個体識別に必要な `MicrochipNumber`（マイクロチップ番号）と親子判定結果 `DnaParentageResult`（DNA 型鑑定）を自身の属性として保持していた。しかしこれらの情報は本来、JAIRS 登録規程実施基準（第 6〜7 条の 2）が「登録に関する馬の審査」として体系化する業務—個体識別・DNA 型/血液型検査・親子判定—に帰属する。同規程では検体・遺伝情報の所有権が JAIRS に帰属し、審査結果が登録原簿に記載されることが明示されており、「審査」と「登録」は時系列も所有権も異なる業務である。

また、審査は離乳前（血統登録より前）に行われるため、審査が完了した時点では `BloodHorse` 集約はまだ存在しない。`MicrochipNumber` を `BloodHorse` に残すと、審査側でも個体識別のために同じ番号を保持せざるを得ず、二重管理が生じる。この構造では識別子の出所が曖昧になり、ライフサイクルの不整合を招く。

## Decision（決定）

審査を固有 ID（`HorseInspectionId`）を持つ独立集約 `HorseInspection` として切り出す（軽量ライフサイクル＝確定済み審査の記録）。識別子（マイクロチップ）の出所を審査側に一本化し、`BloodHorse` は `inspectionId: HorseInspectionId` で審査集約を参照する。

### 集約の責務分担

- **`HorseInspection`**: マイクロチップ番号・親子判定（`ParentageDetermination`）・個体特徴記述子（`IdentificationFeatures`）を所有する。審査が先に確定し、その審査 ID を血統登録が引用する。
- **`BloodHorse`**: `inspectionId` で `HorseInspection` を参照する。マイクロチップ番号を直接保持しない。

### 親子判定の表現

親子判定は sealed `ParentageDetermination` で区分する。

- `ByDna`: DNA 型検査による親子判定（基本ルート）。`DnaParentageResult` を保持。
- `ByBloodType`: 血液型検査によるフォールバック（父母死亡・血液型のみ確認等）。詳細条件は #267。
- `ByOverseasInstitution`: 承認海外機関の判定によるフォールバック（輸入馬等）。詳細条件は #267。
- `NotApplicable`: 父母不明等、親子判定の対象外の場合。

血統登録は `ParentageDetermination.confirmsDeclaredParents()` が `true` を返すことを前提条件とする（親子判定で申告通りの父母が確認されていなければ登録できない）。

### パッケージ配置

`DnaParentageResult`・`MicrochipNumber` は `domain.studbook.model` の inspection パッケージへ移設する。`BloodHorse` に残すのは `inspectionId` 参照のみとする。

## Consequences（結果・影響）

### 波及範囲

- **`BloodHorse` 構造**: `microchipNumber` プロパティが `inspectionId` 参照に置き換わる。ファクトリ（`create` / `createImported` / `registerFoal`）の引数が `HorseInspection` を受け取る形に変わる。
- **永続化**: `blood_horses` テーブルの `microchip_number` 列を `inspection_id` へ差し替え（Flyway V3 相当のマイグレーション改訂）。`horse_inspection` テーブルを新設してマイクロチップ・親子判定・特徴記述子を収容する。集約間の DB 外部キーは張らず ID 参照のみとする（[ADR-0027](0027-persistence-spring-data-jdbc.md)・[ADR-0030](0030-jdbc-only-persistence-retire-inmemory.md) 既存方針を踏襲）。
- **登録ユースケース**: 審査集約を生成し、成功時に `HorseInspectionRepository` に保存してから `BloodHorse` を登録する 2 ステップ構造になる。
- **レスポンス組み立て**: `RegisteredBloodHorse` が `BloodHorse` と `HorseInspection` の両方を受け取る構成に変わる。

### フォローアップ

以下は本 ADR のスコープ外とし、フォロー issue で対応する。

- 審査の一級 API 配線（審査リソースの記録・参照エンドポイント、特徴記述子・フォールバック親子判定詳細の公開）
- 親子判定フォールバックの詳細ロジック（#267）
- DNA 再利用・繁殖登録時の再検査省略（#266）

### 設計上の利点

「審査が識別子を所有し、登録が審査を参照する」構図により、識別子の二重管理が解消し、業務実態（審査先行・登録後続）とモデル構造が対応する。審査を独立集約にすることで、将来的に審査単独でのライフサイクル管理（再審査・番号変更等）も収容しやすくなる。

## 関連

- Issue: #312 / #267 / #266
- [ADR-0009](0009-immutable-aggregates.md): イミュータブル集約のパターン
- [ADR-0020](0020-sealed-origin-and-discriminated-origin-subobject.md): sealed による出自区分の表現
- [ADR-0027](0027-persistence-spring-data-jdbc.md): Spring Data JDBC による永続化
- [ADR-0030](0030-jdbc-only-persistence-retire-inmemory.md): JDBC 一本化・InMemory 廃止
