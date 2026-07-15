# 0067. 認可の主体 Actor を共有カーネルに置き application 層でロール認可する

- Status: Accepted
- Date: 2026-07-15
- Deciders: Matsui

## Context（背景・課題）

[ADR-0064](0064-authn-via-identity-platform-authz-in-app.md) で「認証は GCP Identity Platform に委譲し、ロール・権限の真実の出所は自前 DB に持つ」と決めたが、**認可そのものの設計（`Actor` をどのリングに置くか、どの層で判断するか、識別子をコンテキスト間でどう受け渡すか）は範囲外**として先送りしていた。#606 でその設計を確定させる。

制約と前提:

- **コンテキスト間依存は全面禁止**（ArchUnit `BoundedContextRulesTest`）。一方で「誰が・何ができるか」を表す認可の主体は、`studbook` / `racing` などすべてのコンテキストのユースケースが読む横断的な値である。どのコンテキストにも属させられない。
- **リソース単位の認可**（例: 馬名登録で生産者本人か、を対象馬をロードして判断する）は、対象を引き当てないと判断できないため、フィルタ層（DispatcherServlet の外）では原理的に書けない。ロール認可（`can(permission)` の単純判定）に限っても、権限の出所が DB である以上フィルタ層に持たせる必然性はない。
- 認可判断を `iam` コンテキストに閉じ込めると、他コンテキストのユースケースは `iam` を直接参照できない（コンテキスト間依存禁止）ため、adapter が仲介せざるを得ず、実質「アダプタ層認可」に逆戻りする。

## Decision（決定）

**認可の主体 `Actor`（`AccountId` / `Permission` 込み）を共有カーネル `domain.shared` に置き、認可は application 層のユースケースが `Actor.can(permission)` で判断する。`Account` 集約は `iam` コンテキストに置く。**

- **`Actor` / `AccountId` / `Permission` は `domain.shared`（共有カーネル）に置く。** コンテキスト間依存が全面禁止の下で、全コンテキストが読む横断的な値を置ける先はここしかない。これを許すため ArchUnit の `dddBuildingBlocksResideInDomainModel` を分割し、**`@ValueObject` だけは共有カーネルへの配置を許可**する（`@AggregateRoot` / `@Entity` / `@Repository` / `@DomainEvent` は従来どおり `domain.*.model` のみ）。`domain/shared/model/` のようなサブパッケージは切らない。
- **権限の語彙（定数）は各コンテキストが持つ**（`StudbookPermissions` / `RacingPermissions`）。共有カーネルに置くのは `Permission` 値クラスだけとし、全コンテキストの語彙を集めて共有カーネルが腐るのを防ぐ。値は DB の `iam.role_permission.permission` と文字列で一致する契約。
- **`Account` 集約と `AccountRepository` は `iam` コンテキストに置く。** アカウントのライフサイクル（作成・ロール付与）は `iam` の関心事。`Actor` を共有カーネルに、`Account` を `iam` に置く非対称は意図したもの。
- **認可は application 層のユースケースが担う。書き込みユースケースは `Actor` を第 1 引数に取り、`binding {}` の外で early-return する**（`val p = XxxPermissions.YYY; if (!actor.can(p)) return Err(XxxUseCaseError.Forbidden(p))`）。権限不足は各エラー型の `Forbidden` バリアントで表し、`application.shared.AuthorizationError` マーカーを実装させて adapter 層で一様に 403 へ写す。読み取り（GET）は認証のみで権限不要のため `Actor` を取らない。
- **`Actor` はリクエストごとに JWT の `sub` から組み立てる。** `ActorArgumentResolver`（adapter）が `sub` → `Account` → 展開済み権限を引き当てて注入する。`account` 未登録なら 403（`account-not-provisioned`）。認証情報が無いままここに来るのは `authenticated` エンドポイントの設定漏れなので fail-loud で 500 にする（空の `sub` で DB を引いて 403 に化けさせない）。
- **403 の描画はフィルタ層の `AccessDeniedHandler` を足さず、中央の MVC 例外 funnel に載せる。** ユースケースの `Forbidden` は `orThrowProblem()` が、`ActorArgumentResolver` の `account-not-provisioned` は `ErrorResponseException` が、いずれも DispatcherServlet の内側で `GlobalExceptionHandler` を通り `application/problem+json` になる。`ExceptionTranslationFilter` には到達しない。これは `AuthorizationE2eTest` で実配線のまま実測した（REGISTRAR→201 / VIEWER→403 forbidden / 未登録→403 account-not-provisioned / VIEWER の GET→200）。

## Consequences（結果・影響）

- `Actor` が書き込みユースケースの必須第 1 引数になり、controller の渡し忘れがコンパイルエラーになる。#606 以降、全書き込み `invoke` が `(Actor, Command)` の 2 引数へ揃った。ArchUnit `commandHandlingInvokesAreTransactional` の対象シグネチャも `[Command]` 単項から `[Actor, Command]` へ更新した。
- 認可分岐（権限不足で 403）を MockMvc なしのユースケース単体テストで網羅できる。
- `@WebMvcTest` slice は認証フィルタを無効化しているため（[ADR-0064](0064-authn-via-identity-platform-authz-in-app.md)）、controller の `Actor` 渡し忘れは slice では検出できない。コンパイル時の必須引数と `AuthorizationE2eTest` が backstop になる。slice で `Actor` を取るハンドラを叩くには、本番同様の認証状態を `SecurityContext` に用意する必要がある（テスト支援 `TestSecurityContext`）。
- `AccessDeniedHandler` を持たないことが実測で裏づけられた。将来 `SecurityConfig` に認可を足したくなっても、まず「フィルタ層に 403 経路は無い」という現状の不変条件を崩す判断になる。
- **却下した代替案**:
  - `@PreAuthorize`（メソッドセキュリティ）: ロール認可までは書けるが、リソース単位の認可（対象をロードして判断）に踏み込んだ時点で SpEL に業務ロジックが漏れて破綻する。認可の一貫した置き場にならない。
  - 認可判断を `iam` コンテキストに閉じ込める: 他コンテキストが `iam` を参照できず adapter 仲介＝実質アダプタ層認可に戻る。レイヤーが増えるだけ。
  - IdP の custom claims に権限を載せる: [ADR-0064](0064-authn-via-identity-platform-authz-in-app.md) で却下済み（権限剥奪がトークン期限まで反映されない）。
  - `Actor` を `domain/shared/model/` のようなサブパッケージに隔離する: 共有カーネルに `@ValueObject` を直接許す方が素直で、ArchUnit の穴（後述）も増やさない。
- **未解決（#606 の射程外）**:
  - Identity Platform にユーザーができたときの `iam.account` 行の**自動プロビジョニング**。dev / test はテストコードから作り、本番の経路は別途決める。
  - **リソース単位の認可**（例: 馬名登録の生産者本人チェック）は #607。本 ADR はロール認可の基盤まで。
- **既知の弱点（レビュー担保）**:
  - `ArchSupport.DOMAIN_MODEL = "com.example.api.domain..model.."` は `domain.shared.model` にもマッチするため、そこに `@AggregateRoot` 等を置くとルールをすり抜ける（既存の穴。`Actor` を共有カーネルに置いたことで到達しやすくなった）。
  - `Permission` / `SubjectId` は無検証の公開コンストラクタ。`<context>:<resource>:<action>` 形式は KDoc 止まりだが、`can()` は完全一致で fail-closed のため実害は小さい。
- 結論は指示ファイル側に置き、経緯は本 ADR に残す（認可の在り処は `CLAUDE.md`「認証」節、`Actor` 第 1 引数と `Forbidden` 規約は `.claude/rules/architecture.md`、`AuthorizationError` マーカーは `.claude/rules/error-handling.md`）。
