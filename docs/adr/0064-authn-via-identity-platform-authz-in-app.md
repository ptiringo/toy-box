# 0064. 認証は GCP Identity Platform に委譲し、認可の権限は自前 DB に持つ

- Status: Accepted
- Date: 2026-07-10
- Deciders: Matsui

## Context（背景・課題）

本プロジェクトには認証・認可の実装が一切なく（`spring-boot-starter-security` への依存もゼロ）、全エンドポイントが無認証で開いている。学習を主眼に、認証（誰であるか）と認可（何をしてよいか）を薄く一気通貫で導入したい。

認証の入口として 4 案を検討した。

- **自前ユーザー + JWT 発行**: 外部依存ゼロで発行側・検証側の双方を学べるが、資格情報（パスワードハッシュ）を自分で抱える。実務で最も多い形ではない。
- **外部 IdP に委譲（リソースサーバ）**: 資格情報を持たず、JWT の検証だけを担う。実務で最も一般的。
- **Spring Authorization Server を自前で立てる**: 学習密度は最高だが、authorization code + PKCE まで含むため「薄く一気通貫」から遠い。
- **セッション Cookie（form login）**: コード量は最小だが、API 主体・Cloud Run（複数インスタンス）・MCP アダプタと相性が悪い。

**外部 IdP への委譲**を選んだうえで、IdP の実体をさらに検討した。Keycloak のセルフホストは realm / client / role / scope を自分で組めて学習密度が高いが、docker-compose と Testcontainers のセットアップを抱える。Auth0 / Okta の無料枠はセットアップが最も楽だが、テナント設定が git に残らず再現性が下がる。**GCP Identity Platform** は Cloud Run と同じ GCP 内で完結し、Terraform 管理の射程にも入るため、これを採る。

ただし Identity Platform（Firebase Auth 系）が発行する ID トークンは、素の状態では `sub` / `email` / `email_verified` 程度しか持たない。ロールを載せるには Admin SDK で custom claims を書き込む必要があり、ローカルテストにはエミュレータかモックが要る。加えて custom claims 方式は、権限を剥奪してもトークンが期限切れになるまで反映されない。

なお、Identity Platform の issuer が OIDC discovery を公開しているかは事前に確認した。`https://securetoken.google.com/<PROJECT_ID>/.well-known/openid-configuration` は 200 を返し、`issuer` が自分自身と一致し、`jwks_uri` も正しく指す。したがって `jwk-set-uri` を手で指定する必要はない。

## Decision（決定）

**認証は GCP Identity Platform に委譲し、本 API は OAuth2 リソースサーバとして ID トークン（JWT）を検証するだけとする。資格情報は保持しない。**

- 検証は `spring.security.oauth2.resourceserver.jwt.issuer-uri` と `.audiences` の 2 プロパティで構成する。issuer は `https://securetoken.google.com/<GCP_PROJECT_ID>`、audience は GCP プロジェクト ID。discovery が公開されているため `issuer-uri` だけで JWKS まで解決でき、`JwtDecoder` を自前で書かない。
- **ロール・権限の真実の出所は自前 DB とし、IdP の custom claims は使わない。** JWT から受け取るのは `sub`（身元）だけ。
- 認証はフィルタ層（`SecurityFilterChain`）で完結させ、失敗は RFC 9457 の `application/problem+json` で 401 を返す。認可（何をしてよいか）はフィルタ層では判断しない。
- `SecurityConfig` は adapter リング（`com.example.api.controller`）に置く。`com.example.api.config` に置くと、RFC 9457 の problem を組み立てる `problem()` ビルダ（adapter リング）を内側から参照することになり、ArchUnit の `onionLayers` に違反する。
- `permitAll` は運用・CI が壊れるエンドポイントに限る。`/actuator/health`（Cloud Run のヘルスチェック）、`/v3/api-docs/**` と `/swagger-ui/**`（`generateOpenApiDocs` が forked bootRun 経由で取得するため、認証を掛けると OpenAPI lint の CI ゲートが壊れる）、`/mcp/**`（MCP クライアントがトークンを持てない）。
- `@WebMvcTest` slice では認証フィルタを無効化する（`@AutoConfigureMockMvc(addFilters = false)`）。認証は専用テストと e2e が、認可は各ユースケースの単体テストが検証する。
- テストは実 JWKS を引かず、HS256 の `JwtDecoder` Bean をテスト側で定義して差し替える。Boot の `JwtDecoder` 自動構成は `@ConditionalOnMissingBean` なので、`spring.main.allow-bean-definition-overriding` は要らない。

## Consequences（結果・影響）

- 資格情報（パスワード）を自分で保持しないため、その保管・ローテーション・漏洩対応の責務を負わない。反面、Identity Platform の可用性と仕様に依存する。
- 権限変更が即座に反映される。`Actor`（誰で・何ができるか）はリクエストごとに DB から組み立て、キャッシュしない。単純さを優先した選択であり、キャッシュは必要になってから入れる。
- 認可を JWT だけで完結させられないため、保護されたリクエストは必ず DB を 1 回引く。完全な stateless ではない。
- **認可の設計はこの ADR の範囲外**。`Actor` をどのリングに置くか、コンテキスト間で identity をどう受け渡すかは、実装と合わせて別の ADR に記録する。
- Identity Platform 側にユーザーができても、自前 DB の account 行は自動では生えない。初回アクセス時の自動プロビジョニングは採らず、dev / test は Flyway の seed で賄う。それ以外のプロビジョニング経路は未決。
- `@WebMvcTest` slice で認証フィルタを無効化するため、controller がユースケースに `Actor` を渡し忘れるバグは slice では検出できない。コンパイル時の必須引数と e2e が backstop になる。
- MCP エンドポイントは `permitAll` のまま残る。MCP クライアントへの認証の載せ方（[ADR-0035](0035-mcp-interface-adapter.md) の adapter）は別途決める必要がある。
- 本番（Cloud Run）は `GCP_PROJECT_ID` を環境変数で受け取る。プロジェクト ID は秘密ではないため Secret Manager には置かない。この値が注入されないと issuer が既定値に落ち、全トークンが 401 になる。
- 結論は指示ファイル側に置き、経緯は本 ADR に残す（`permitAll` の範囲は CLAUDE.md「認証」、slice テストの方針は `.claude/rules/testing.md`）。
