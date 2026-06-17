# 0007. HTTP 契約の enum をドメインから分離し Dto enum + マッパーで往復する

- Status: Accepted
- Date: 2026-06-17
- Deciders: Matsui

## Context（背景・課題）

血統登録の request / response DTO（`RegisterBloodHorseRequest` / `RegisterBloodHorseResponse`）が、ドメイン enum（`Sex` / `CoatColor` / `BreedType` / `DnaParentageResult`）を**そのままフィールド型に持っていた**。Jackson が列挙子名でデシリアライズするため検証が無料で得られる反面、HTTP 契約がドメイン enum の列挙子名に直接結合し、ドメイン側のリネームが wire 契約（および生成クライアント）を**無言で破壊する**弱点があった。

オニオンアーキテクチャでは adapter（境界）はドメインから独立した表現を持つべきで、この結合は方針に反する。一方で、本プロジェクトは YAGNI / 段階的導入を重んじる sandbox であり、ボイラープレートを無闇に増やしたくもない。そこで「どう decouple するか」を 3 案で比較した。

| 方式 | OpenAPI に許容値が出る | 未知値の自動 400 | ドメインリネーム耐性 | 列挙追加の compile 検知 |
|------|:--:|:--:|:--:|:--:|
| (1) ドメイン enum 直受け（現状） | ✅ | ✅ | ❌ | ―（同一型） |
| (2) Dto enum + マッパー【採用】 | ✅ | ✅ | ✅ | ✅（網羅 `when`） |
| (3) 生 String + マッパー | ❌（ただの string になる） | ❌（手書き検証が必要） | ✅ | ⚠️ |

- **(1) 直受け継続**: 最も簡潔だが、結合の弱点が残る。却下。
- **(3) 生 String + マッパー**: 完全に decouple できるが、Jackson の自動検証（未知値 400）と OpenAPI の enum スキーマ（許容値の公開）を**両方失い**、それを手書きで埋め直す必要がある。decouple のために検証とドキュメント品質を犠牲にするのは割が合わない。却下。
- **(2) Dto enum + マッパー**: adapter 層に契約専用の `〜Dto` enum を置き、網羅 `when` のマッパーで domain と往復する。Jackson 検証と OpenAPI スキーマを維持したまま decouple でき、ドメイン enum に列挙子が増減するとマッパーが compile エラーで気づける。採用。

## Decision（決定）

- HTTP 契約（request / response DTO）はドメインモデルから独立させる。とりわけ **enum はドメイン enum を wire に直接晒さず、`controller`（adapter）層に契約専用の `〜Dto` enum を置いてマッピングする**。
- 変換は `toDomain()` / `toApi()` の**網羅 `when`** で書く（ドメイン enum の列挙子増減を compile エラーで検知する）。
- **生 String 受けは採らない**。`〜Dto` enum なら Jackson の自動デシリアライズ検証（未知値は 400）と OpenAPI の enum スキーマ（許容値の公開）を維持したまま decouple できる。
- **decouple 対象は wire 契約のみ**。application 層のコマンド（`〜Command`）はドメイン enum を保持してよい（変換は controller 境界に限定する）。
- 列挙子名が現状ドメインと一致していても、それは「今は一致している」だけ。独立性はマッピング層で担保する（ドメインをリネームしても `when` 節を直せば wire 名は不変）。
- 命名は `〜Dto` サフィックスとする（同名 + alias import は、`DnaParentageResult` の import 行が EditorConfig の 100 桁制限を超えるため採らない）。OpenAPI スキーマ名は `SexDto` 等になる。

参考実装は `controller/horse/BloodHorseWireEnums.kt`（`SexDto` / `CoatColorDto` / `BreedTypeDto` / `DnaParentageResultDto`）。守るべきルールの結論は [.claude/rules/api-design.md](../../.claude/rules/api-design.md)「リクエスト/レスポンス DTO とドメインの分離」に置く。経緯は PR #285（issue #276）。

## Consequences（結果・影響）

- ドメイン enum をリネームしても wire 契約（生成クライアント）が壊れなくなった。マッピング漏れは compile エラーで顕在化する。
- Jackson の自動検証と OpenAPI の enum スキーマを維持できた。生 String 案で失うはずだった品質を犠牲にしていない。
- enum を 1 つ増やすたびに `〜Dto` 定義と `toDomain()` / `toApi()` のボイラープレートが要る。マッパーの `when` 分岐はテストでカバレッジゲート（`koverVerifyMature`、`controller` は成熟対象）にかかるため、**全列挙子を走査する単体テストを添える**必要がある（`BloodHorseWireEnumsTest` が参考）。
- VO で表す項目（番号・名前など）は引き続き素の文字列で DTO に受け、ユースケース内で各 VO の `create()` を通して検証する。enum（adapter で変換）と VO（ドメインで検証）で非対称になるが、検証ロジックを持つ VO はドメイン層に検証責務を残すための意図的な使い分けである。
- 本決定は BloodHorse 限定ではなく、今後の全 controller / DTO に適用する方針。jockey など他リソースへ横展開する際もこのパターンに従う。
