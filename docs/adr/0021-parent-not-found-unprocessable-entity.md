# ADR-0021: 父母不在（sire/dam 参照先不在）を 422 Unprocessable Entity で確定する

- ステータス: Accepted
- 日付: 2026-06-23
- 関連: Issue #275, ADR-0018, ADR-0017, ADR-0016

## コンテキスト

軽種馬（`BloodHorse`）を血統登録（内国産馬登録 `RegisterInStudBookUseCase` / 生産産駒登録 `RegisterFoalUseCase`）する際、リクエストボディで指定された父（`sire_id`）・母（`dam_id`）が `BloodHorseRepository` に存在しない場合がある。このとき返す HTTP ステータスを 422 とするか 404 とするかが未確定のまま、実装は暫定的に 422 を返していた（`SireNotFound` / `DamNotFound` → `422 Unprocessable Entity`）。

この「未確定」を解消し、判断の所在を明示するのが本 ADR の目的である。

なお、この論点は ADR-0018（「種付せず」記録における繁殖登録の参照先不在を 422 とする）で確立した一般原則の、別ユースケースへの適用にあたる。父母 ID もまた「URL パスのリソース識別子」ではなく「リクエストボディ内の他リソースへの参照」であり、同じ判断軸が適用できる。

## 決定

父母の参照先不在は **422 Unprocessable Entity** で確定する。

- リクエストボディの `sire_id` が指す軽種馬が存在しない場合は `422`（`sire-not-found`、拡張プロパティ `sire_id`）
- リクエストボディの `dam_id` が指す軽種馬が存在しない場合は `422`（`dam-not-found`、拡張プロパティ `dam_id`）

これに伴い、404 と 422 の判断基準を `.claude/rules/api-design.md`（エラーレスポンス節）に昇格して明文化する。

## 理由

ADR-0018 で確立した判断軸をそのまま踏襲する。

- **URL パス上のリソース不在は 404 Not Found**: `/api/bloodHorses/{id}:registerName` のように URL パスで識別される操作対象が存在しない場合は 404 が自然（例: `HorseNotFound`、`BreedingResultNotFound`）。
- **リクエストボディ内の参照先不在は 422 Unprocessable Entity**: リクエストは構文的に正しくサーバーに到達して処理されたが、ボディ内で参照する別リソースが見つからず意味的に処理できない場合は 422 が適切（例: `BreedingRegistrationNotFound`、本 ADR の `SireNotFound` / `DamNotFound`）。

父母 ID は登録対象の `BloodHorse` を識別するパス要素ではなく、ボディ内で参照する協力リソースである。したがって 422 が判断軸に合致する。

### 404 を採らなかった理由

「父というリソースが存在しない」という見方から 404 を採る余地はあるが、それを採ると ADR-0018 で確立した「ボディ内参照の不在＝422」と矛盾し、`BreedingRegistrationNotFound`（422）との一貫性も崩れる。リソース参照の不在に対するステータスは、参照が URL パスにあるかボディにあるかで一貫して切り分ける方が、横断的に判断がぶれない。

## 影響

- `RegisterInStudBookUseCaseError.SireNotFound` / `DamNotFound`、`RegisterFoalUseCase` の同等バリアントは 422 にマッピングする（既存実装どおり。挙動の変更はない）。
- `.claude/rules/api-design.md` のエラーレスポンス節に「404 vs 422 の判断基準」表を追記し、常時参照のルールとして昇格する。
- `BloodHorseResponse.kt` の `toProblemDetail()` の doc コメントに本 ADR を参照させる。
- 今後リクエストボディ内で他リソースを参照するユースケースは、ADR-0018 / 本 ADR の方針（ボディ内参照の不在＝422）に従う。
