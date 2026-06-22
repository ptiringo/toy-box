# 0010. 集約をまたぐ前提条件を持つ生成口はドメインサービスに封じ込める（ArchUnit で強制、テストは Object Mother）

- Status: Accepted
- Date: 2026-06-20（2026-06-22 改訂: 強制手段を `internal` 可視性から ArchUnit ルールへ変更）
- Deciders: Matsui

## 改訂サマリ（2026-06-22）

本 ADR の骨子（集約をまたぐ前提条件を持つ生成口は、前提を検証するドメインサービスからのみ生成できるよう封じ込める）は維持する。ただし**封じ込めの強制手段を `internal` 可視性から ArchUnit ルールへ変更**した。生成口（`BloodHorse.of` ほか計 4 つ）は `public` のまま保ち、「`service.<context>` 以外からの呼び出しを違反とする」ArchUnit ルールで封じ込める。

変更理由（詳細は後述）:

1. **`internal` は意図を正確に強制していなかった（過剰許可）**。`internal` はモジュール（ソースセット）全体に公開するため、ドメインサービスだけでなく同一モジュールの `application` / `controller` / `infrastructure` からも生成口を直接呼べてしまう。「ドメインサービスを迂回した生成はコンパイル時に不能」という当初の主張は、実は**別モジュール（現状未存在）に対してしか成り立たず**、モジュール内の迂回は防げていなかった。
2. **`internal` は封じ込めをソースセット境界に結合させ、柔軟性を奪う**。テスト用 Fixture の置き場（issue #326）の検討で `java-test-fixtures` を実ビルドで PoC した結果、testFixtures ソースセットからは main の `internal` が見えない（friend 関係が成立しない）ことが判明し、「`internal` 封じ込め」と「fixture のモジュール間共有」は本質的にトレードオフだと分かった。封じ込めを可視性から切り離せば、この緊張が消える。
3. **本リポジトリの「規約は機械強制」方針（ArchUnit / detekt 多用）と整合**する。`internal` の言語レベルの硬さは失うが、ArchUnit ルールで機械強制は維持できる（規約＋レビューのみへは後退しない）。

以下の Context / Decision / Consequences は改訂後の内容に更新済み。当初（2026-06-20）は同じ封じ込めを `internal` で実現していた経緯を、関連する箇所に注記する。

## Context（背景・課題）

血統登録（issue #266 / PR #263）の実装で、軽種馬集約 `BloodHorse` の生成のしかたが問題になった。

血統登録は「血統及び個体識別を明らかにする登録」であり、`BloodHorse` を生成してよいのは次の前提条件を検証し終えた後に限られる。

- 父が雄であること
- 母が雌であること
- 申告された父母との DNA 型による親子判定に矛盾がないこと
- 仔の品種が父母の品種と整合すること

これらはいずれも**父・母という別集約（別の `BloodHorse`）を参照して初めて検証できる**。つまり前提条件の検証は単一集約内で完結せず、集約をまたぐ。本プロジェクトの規約（[architecture.md](../../.claude/rules/architecture.md)）では、複数の集約をまたぐドメインロジックはドメインサービス（`service/` のトップレベル関数）の責務である。検証は `registerInStudBook` が担う。同様の構図は輸入馬の `registerImportedHorse`、繁殖登録の `registerForBreeding`、種付記録の `recordCovering` にもあり、それぞれ `BloodHorse.ofImported` / `BreedingRegistration.of` / `BreedingResult.of` を生成する。

ここで「生成口を誰に・どう見せ、どこからの生成を許すか」が論点になった。生成ファクトリ（`of`）を無制限に公開すると、検証を経ずに集約を直接生成でき、「血統登録済み（前提条件を満たした）個体」という不変条件が守られず、ドメインサービスを迂回した不正な生成が可能になる。

対比として、騎手集約 `Jockey` は生成ファクトリ `Jockey.create` を `public` にして誰からでも呼べる。`Jockey` の不変条件（姓名がブランクでない）は**集約内で完結**するため、ファクトリ自身が検証すれば足り、生成口を封じ込める必要がない。`BloodHorse` との違いは「不変条件が集約内で閉じるか、集約をまたぐか」にある。

### 強制手段の検討

「集約をまたぐ前提条件を持つ生成口は、対応するドメインサービスからのみ呼べる」をどう強制するか。

- **規約文書で「直接生成するな」と書くだけ**: 文章のみのルールは強制力がなく、迂回をツールが防げない。却下。
- **生成口を `private` にし、ドメインサービスを集約と同じパッケージ（同一ファイル）に置く**: パッケージプライベートで閉じられるが、ドメインサービスは `service/` リング、モデルは `model/` リングという本プロジェクトのパッケージ分割（オニオン 4 リング）と矛盾する。却下。
- **生成口を `internal` にする**（当初 2026-06-20 採用、後に撤回）: Kotlin の `internal` はモジュール（Gradle のソースセット単位）に閉じる。`model/` の生成口を `internal` にすれば、同一モジュールの `service/` からは呼べるが、別モジュールからは呼べない。当初これを採用したが、次の二つの問題が後に判明した。
  - **過剰許可**: `internal` はモジュール全体に見えるため、ドメインサービスに限らず `application` / `controller` / `infrastructure` からも生成口を呼べてしまう。本来の意図「ドメインサービスからのみ」は強制できておらず、防げていたのは別モジュール（現状存在しない）からの呼び出しだけだった。
  - **ソースセット結合**: テスト用 Fixture の置き場（issue #326）で `java-test-fixtures` の採否を検討した際、testFixtures ソースセットからは main の `internal` が見えないことを実ビルドで確認した（`associateWith(main)` や明示 `-Xfriend-paths` でも不可。`java-test-fixtures` は main を JAR バリアントとして consume し、friend-path はクラスディレクトリを指すため friend 関係が成立しない）。`internal` 封じ込めは fixture のモジュール間共有と本質的にトレードオフで、将来のモジュール分割の柔軟性を可視性の硬さに縛りつける。
- **生成口を `public` にし、呼び出し元を ArchUnit で縛る**（2026-06-22 採用）: 生成口は `public` のまま保ち、「`service.<context>` の外から当該生成口を呼ぶことを違反とする」ArchUnit ルールで封じ込める。`internal` の言語レベルの硬さは失うが、(1) 本来の意図「ドメインサービスからのみ」を**正確に**表現でき、(2) 封じ込めを可視性・ソースセットから切り離せ、(3) 既存の `UUID.randomUUID()` 直接呼び出し禁止ルールと同じ ArchUnit パターンで書けて本リポジトリの機械強制方針と整合する。採用。

## Decision（決定）

- 集約の生成口の扱いは、その集約の**不変条件が集約内で完結するか、集約をまたぐか**で決める。
  - **集約内で完結する不変条件のみ**を持つ集約は、検証ファクトリ（`create` 等）を `public` にし、封じ込めない（例: `Jockey.create`。姓名ブランク検証は集約内で閉じる）。
  - **集約をまたぐ前提条件**を満たして初めて生成してよい集約は、生成口（`of` 等）を **`public` のまま**にしつつ、前提条件を検証するドメインサービス（同一コンテキストの `service/` 配下）からのみ呼べるよう **ArchUnit ルールで封じ込める**。これにより「検証済みでなければ生成しない」という規約を、ドメインサービスを迂回した直接生成の禁止として機械強制する。
- コンストラクタは `private` とし、生成は companion object のファクトリ経由に限る（[ADR-0009](0009-immutable-aggregates.md) のイミュータブル集約・`private constructor` と組み合わさる）。
- 封じ込め対象の生成口と、それを呼べるドメインサービス領域:
  - `BloodHorse.of` / `BloodHorse.ofImported` ← `horseracing.service.horse`（`registerInStudBook` / `registerImportedHorse`）
  - `BreedingRegistration.of` ← `horseracing.service.breeding`（`registerForBreeding`）
  - `BreedingResult.of` ← `horseracing.service.breeding`（`recordCovering`）
- ArchUnit ルール（`ArchitectureTest.bloodHorseCreationConfinedToHorseService` / `breedingCreationConfinedToBreedingService`）は、許可されたドメインサービス領域の**外**から上記生成口を呼ぶことを違反とする。実装上の注意:
  - 生成口は引数に inline value class（ID 値クラス等）を取るため、JVM メソッド名が `of-Havw-KM` のようにマングルされる。ルールはハッシュ手前の**ベース名**で突き合わせ、署名変更でハッシュが変わっても壊れないようにする。
  - Kotlin の companion object メソッドは呼び出しターゲットの owner が `〜$Companion` になるため、囲みクラスを辿って集約本体と突き合わせる。
- テストは `ImportOption.DoNotIncludeTests` で ArchUnit の対象外のため、Object Mother（`〜Fixture`）からの生成口呼び出しは封じ込めの対象にならない。`internal` 生成口を直接叩く責務を Object Mother に集約していた当初の事情は解消され、Fixture は `public` 生成口を素直に呼んで実物の集約を組む。Fixture は **`src/test`** に置き、対象集約と同一パッケージに同居させる（`java-test-fixtures` は採らない。issue #326 / [testing.md](../../.claude/rules/testing.md) 参照）。

守るべきルールの結論は [CLAUDE.md](../../CLAUDE.md)「Entity パターン」および [architecture.md](../../.claude/rules/architecture.md)「その他の強制ルール」に置く。参考実装は `domain/horseracing/model/horse/bloodhorse/BloodHorse.kt`（`of` / `ofImported` は public）、`service/horse/RegisterInStudBook.kt`（正規生成経路）、`test` の `BloodHorseFixture`、ルールは `architecture/ArchitectureTest.kt`。経緯は issue #268（当初の昇格）/ #266 / #326（強制手段の再設計）、PR #263 / #340。

## Consequences（結果・影響）

- 「血統登録済みの `BloodHorse`」という不変条件が、文章ルールではなく **ArchUnit による呼び出し元制約**で守られる。`registerInStudBook` 等を迂回した生成は、`service.<context>` の外から行えばビルド（`./gradlew test`）で失敗する。`internal` 時代と違い、`application` / `controller` 等モジュール内からの迂回も含めて防げる（本来の意図を正確に強制）。
- 強制が言語可視性ではなくテスト時の ArchUnit に移ったため、**コンパイル時の即時失敗ではなくテスト実行時の失敗**になる。代わりに生成口の可視性が `public` で自由になり、ソースセット分割・将来のモジュール分割が封じ込めの設計と衝突しなくなる。
- 生成口を封じ込めるか否かの判断基準は「不変条件が集約内で完結するか」のまま不変。新しい集約（種牡馬・繁殖牝馬などのロール）を追加するときも、集約をまたぐ前提が要るなら生成口を `public` のままドメインサービスに封じ込め、ArchUnit ルールへ対象を 1 行足す。
- ArchUnit ルールは inline value class のメソッド名マングリングと companion の owner 解決という Kotlin 固有の事情を吸収する必要があり、述語が空振り（偽陰性）しないことの担保が重要になる。本決定では許可パッケージを一時的に変えるミューテーション確認で、両ルールが実際に違反を検出することを確かめた。恒久的な違反サンプルによる回帰テスト（既存の `AggregateNotDataClassRuleTest` / `DtoDomainEnumRuleTest` に相当）は、ルール検証用サンプル fixture（「B 群」）の置き場方針と合わせて別 issue で整備する。
- Object Mother 経由のテスト構築という方針自体は維持。Fixture は `src/test` 同居のまま、`public` 生成口を直接叩く（[testing.md](../../.claude/rules/testing.md)）。
- 父母不明個体（輸入馬・基礎輸入馬）の登録経路（issue #267 / PR #358）でも本決定の骨子は維持され、`BloodHorse.ofImported` を `registerImportedHorse` に封じ込める形で同じ ArchUnit ルールの対象に含めている。
