# 0014. 集約をまたぐ前提条件は自己検証ファクトリで検証し、生成口の封じ込めを行わない

- Status: Accepted
- Date: 2026-06-22
- Deciders: Matsui
- Supersedes: [ADR-0010](0010-confine-aggregate-creation-to-domain-service.md)

## Context（背景・課題）

[ADR-0010](0010-confine-aggregate-creation-to-domain-service.md) は、集約をまたぐ前提条件（父=雄・母=雌・DNA 親子整合・品種整合など）の検証をドメインサービス（`registerInStudBook` 等）に置き、検証を経ない生成を防ぐために生成口（`BloodHorse.of` 等）を `internal` にして「ドメインサービスからのみ生成できる」よう**封じ込める**と決めた。

この封じ込めは相応の機構（生成口を隠す可視性制御、テストから生成口を叩くための Object Mother、可視性が効かないなら ArchUnit ルールへの移行検討）を必要とし、`internal` の過剰許可やソースセット結合といった副作用も抱えていた。そこで「そもそも封じ込めは必要か」を問い直した。

要点は、**これらの前提条件はすべて、父・母（協力集約）を引数で受け取れば、その場で検証できる**という事実である。`registerInStudBook` が実際に読んでいるのは `sire.sex` / `dam.sex` / `entry.dnaParentage` / 父母仔の `breedType` だけで、いずれも引数として渡された `BloodHorse` と `StudBookEntry` から計算できる。リポジトリ参照は不要（父母の引き当ては呼び出し側＝アプリケーション層が済ませる）。

つまり、検証を**集約自身の生成ファクトリ**に置き、父・母を引数で受け取って自己検証すれば（`Result` を返す）、騎手集約の `Jockey.create`（姓名ブランクを自己検証する public ファクトリ）と同じ形になる。そうすると:

- 検証専用のドメインサービスは不要になる（ファクトリが検証を内包する）。
- 生成口を隠す必要がなくなる（検証を経ないと生成できないので、`public` のままで不変条件が守られる）。
- 封じ込めの機構（`internal` / ArchUnit ルール / Object Mother で internal を叩く段取り）が丸ごと不要になる。

ADR-0010 の封じ込めは、「検証をファクトリの外（ドメインサービス）に出した」という設計選択の**帰結として**必要になっていた。検証をファクトリに戻せば、封じ込めという問題自体が消える。

検討した観点:

- **ドメインサービスに残すべきか（ADR-0010 維持）**: 「複数集約をまたぐロジックはドメインサービス」という規約に忠実だが、ここでの検証は本質的に「与えられた父母に対して妥当な仔を構築できるか」という**構築時バリデーション**であり、Evans の Factory が協力オブジェクトを引数に取って検証する正統な用法に収まる。封じ込めのコストに見合わない。
- **生成口を public に緩めるが検証はサービスのまま（規約＋レビュー）**: 機械強制を捨てる後退で、本リポジトリの方針に合わない。そもそも検証をファクトリに置けば機械強制（型）で守れる。
- **自己検証ファクトリへ移す（採用）**: 検証をファクトリに内包し `public` 自己検証ファクトリへ統一。封じ込め不要。`Jockey.create` と 1 パターンに揃う。

## Decision（決定）

集約をまたぐ前提条件でも、**協力集約を引数で受け取って構築時に検証できるものは、その集約の `public` 生成ファクトリ（`create`）で自己検証する**。検証を満たさなければ生成せず `Result<集約, 〜Error>` を返す。生成口の封じ込め（`internal` 可視性・ArchUnit による呼び出し元制限）は**行わない**。

- 生成ファクトリは `public`。コンストラクタは `private` のまま（[ADR-0009](0009-immutable-aggregates.md)）で、生成は検証する `create`（前提条件なしの経路は `createImported` 等）に限る。検証を経ない構築経路は存在しないため、不変条件は型で守られる（`Jockey.create` と同じ）。
- 検証専用だったドメインサービス（`registerInStudBook` / `registerImportedHorse` / `registerForBreeding` / `recordCovering`）は**削除**し、ロジックを各集約のファクトリへ移す:
  - `BloodHorse.create(sire, dam, entry, registrationNumber): Result<_, RegisterInStudBookError>`
  - `BloodHorse.createImported(entry, registrationNumber): BloodHorse`（前提条件なし）
  - `BreedingRegistration.create(registrationNumber, broodmare): Result<_, BreedingRegistrationError>`
  - `BreedingResult.create(breedingRegistration, stallion, coveringDate, certificateNumber): Result<_, RecordCoveringError>`
- 失敗バリアント型（`RegisterInStudBookError` 等）はファクトリと同じ model パッケージへ移す。**名前は据え置く**（対応するアプリケーション層のユースケース `RegisterInStudBookUseCase` / `RecordCoveringUseCase` が表す業務操作の名であり、削除したサービス名への依存ではないため）。
- **ドメインサービスは「単一集約の構築ではない、複数集約のオーケストレーション」に限って残す**。例: `registerFoal`（分娩結果が `LiveFoal` かを判定し、出生日を導出して `BloodHorse.create` へ橋渡しする）、`confirmRaceResult`。リポジトリからの**引き当て（coordination）はアプリケーション層**、**構築時バリデーション（validation）はファクトリ**、**集約をまたぐ手続き（orchestration）はドメインサービス**、と役割を分ける。
- テスト用 Object Mother（`〜Fixture`）は `src/test` に置き対象集約と同一パッケージに同居させる（`java-test-fixtures` は採らない。本体は単一モジュールで共有需要が無い。issue #326）。前提条件検証を経ずに任意の馬を用意したいときは、前提条件を持たない `BloodHorse.createImported` を使う。

守るべきルールの結論は [CLAUDE.md](../../CLAUDE.md)「Entity パターン」および [architecture.md](../../.claude/rules/architecture.md) に置く。参考実装は `model/horse/bloodhorse/BloodHorse.kt`（`create` / `createImported`）、`model/jockey/Jockey.kt`（`create` の先例）、`service/horse/RegisterFoal.kt`（残すオーケストレーション）。経緯は issue #326 / #268 / #266、PR #371 / #263。

## Consequences（結果・影響）

- 生成パターンが `Jockey.create` 型（private constructor＋自己検証 public ファクトリ＋`Result`）に**一本化**される。封じ込めの機構（`internal` と public の使い分け、ArchUnit による呼び出し元制限、Object Mother から `internal` を叩く段取り、inline value class のメソッド名マングリング対応）が**すべて不要**になり、全体が単純になる。
- 「血統登録済み」等の不変条件は、ドメインサービスへの封じ込めではなく**ファクトリの自己検証（型）**で守られる。検証を経ない構築経路（`private constructor`）には外部から到達できない。
- 「複数集約をまたぐドメインロジックはドメインサービス」という原則は**精緻化**される（[architecture.md](../../.claude/rules/architecture.md)）。協力集約を引数で受け取って行う**構築時バリデーションはファクトリの責務**であり、ドメインサービスは単一集約の構築ではないオーケストレーション／手続きに限る。
- トレードオフ: 集約のファクトリが兄弟集約を引数で参照し、関係ルール（父=雄など）を持つことになる。これは「与えられた父母に対して妥当な仔を構築できるか」という構築の本質であり、ファクトリの自然な責務として受け入れる。集約は依然として**フィールドでは**他集約を ID 参照のみとする（jMolecules ルールは維持）。
- リポジトリからの父母の引き当て（coordination）はアプリケーション層に残り、層の責務分担は明確なまま。
- ADR-0010 が解こうとした「ドメインサービス迂回の防止」は、迂回すべきサービスが無くなったため**問題ごと消滅**する。
