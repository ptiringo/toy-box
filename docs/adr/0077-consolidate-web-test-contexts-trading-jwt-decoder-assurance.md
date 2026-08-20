# 0077. web 環境を要するテストコンテキストを 1 つに畳み、本番 JwtDecoder Bean 生成の担保を手放す

- Status: Accepted
- Date: 2026-08-21
- Deciders: Matsui

## Context（背景・課題）

[ADR-0015](0015-gradle-build-performance-tuning.md) は「テスト速度の本筋は並列化ではなくコンテキストキャッシュの再利用最大化」と決め、速度を縮めたいときの効く順の 2 番目に「コンテキスト構成の共通化」を置いた。しかしそこは着手されないまま、スイートだけが育っていた。

[#690 のフェーズ1 実測](https://github.com/ptiringo/toy-box/issues/690#issuecomment-5351503881)で、`:test` 壁時計の 31.2% がコンテキスト構築であること、distinct な `ApplicationContext` が ADR-0015 当時の 6 個から **18 個**へ増えていることが分かった。ヒット率自体は 99.6%（hit 4861 / miss 18 / failure 0）と最適に近く、**問題は再利用率ではなく構成の種類**にある。同じ実測でテスト並列化の利得上限は **-19%** と見積もられており、構成の共通化がそれを上回るなら並列化より先に手を付ける価値がある。これを確かめるのが #817 である。

### 18 個の内訳（実測 = 静的分析と一致）

アノテーション構成から数えた distinct と、`logging.level.org.springframework.test.context.cache=DEBUG` の実測が完全に一致した。

| 群 | 個数 | 内訳 |
|---|---|---|
| `@WebMvcTest` スライス | 11 | `controllers` 引数ごとに 1 個（`JockeyController` だけ `JockeyControllerTest` と `GlobalExceptionHandlerTest` で共有済み） |
| `@SpringBootTest` | 7 | `NONE` 素 / `NONE`+リスナ記録 / `NONE`+失敗注入 / 既定（`MOCK`）/ `RANDOM_PORT` / `RANDOM_PORT`+`TestJwtDecoderConfiguration` / `local` プロファイル |

ADR-0015 当時（distinct 6）と突き合わせると、増加 +12 個の内訳は **WebMvc スライス +7 / `@SpringBootTest` +5** である。前者は「コントローラが 4 種 → 11 種に増えた」ことの機械的帰結で、**揃っていないだけの重複ではない**（`@WebMvcTest` は 1 コントローラ = 1 コンテキストになる）。したがって削減余地は `@SpringBootTest` 側の 7 個に限られる。

### 実測（CI・ubuntu-latest 4 コア・各 3 回の中央値）

ローカル（8 論理コア / 16GB）は測定に使えなかった。同一構成の反復で初回コンテキスト構築が 17.5s / 36.5s と振れ、1 回は `:test` が **74 分**停止した（`RegisterHorseTransactionRollbackTest` に 4482 秒が計上され、その間ログが 1 行も出ない。#690 が観測した 402 秒ブロック・#818 と同型で、より極端）。削減見込みに対しノイズが 1 桁大きい。CI は振れが 3〜11% に収まるため、4 条件をそれぞれ 3 回測って比較した。

| 構成 | distinct | Tomcat | Hikari プール | `:test` 中央値 | 効果 |
|---|---|---|---|---|---|
| 現状 | 18 | 2 | 7 | 84.93s | — |
| ApiApplication 側のみ統合 | 17 | 2 | 6 | 79.54s | -6.3% |
| SecurityConfig 側のみ統合 | 17 | 1 | 6 | 81.79s | -3.7% |
| **両方統合** | **16** | 1 | 5 | **67.55s** | **-20.5%** |

**-20.5% は 16 個にしたときだけ現れる。** 17 個は組み合わせを変えても -4〜6% どまりで、しかも Tomcat を 1 個に減らした側（-3.7%）の方が 2 個のまま（-6.3%）より遅い。したがって Tomcat インスタンス数でも HikariCP プール数でも説明がつかず、**機序は特定できていない**（`:test` の削減 17.38s のうち、コンテキスト構築時間の削減で説明できるのは 6.88s だけで、残り約 10.5s は「構築以外」が速くなった分である）。効果そのものは、最も不利なペア（ベースライン最小 83.54s vs 統合案最大 73.99s）で比べても -9.55s あり、振れの範囲を超えている。

### 引き受けるトレードと、却下した代替案

16 個にするには、既定（`MOCK`）の `@SpringBootTest` グループ（`ApiApplicationTests` / `McpDisabledByDefaultTest`）を `RANDOM_PORT` + `@Import(TestJwtDecoderConfiguration)` のグループへ畳む必要がある。`TestJwtDecoderConfiguration` は HS256 の `JwtDecoder` を差し込み、Spring Boot の `JwtDecoder` 自動構成は `@ConditionalOnMissingBean` なので、**本番の `issuer-uri` 設定から `JwtDecoder` Bean が生成されることの担保が消える**。

この担保はリポジトリ全体でこのコンテキストだけが持っていた。API E2E（`src/e2eTest`）は全クラスが `TestJwtDecoderConfiguration` を `@Import` し、ブラウザ E2E は `EmulatorJwtDecoder` を使う（[ADR-0076](0076-browser-e2e-playwright-auth-emulator-boottestrun.md)）ため、どちらも本番 decoder を通らない。

- **17 個構成（担保を維持）**: 効果が -3.7〜-6.3% と 1/3 以下。並列化の利得上限 -19% にも遠く、ADR-0015 の「効く順」を書き換えるだけの根拠にならない。
- **現状維持**: -20.5% を見送る。CI の全 PR とローカルの pre-push に効く幅なので、見送る理由が担保の薄さに見合わない。
- **担保を別の軽いテストで代替**: `webEnvironment = NONE` では OAuth2 リソースサーバの自動構成が効かず `JwtDecoder` が生えないため、既存の `NONE` 素グループには寄せられない。新しいコンテキストを足せば削減した分を打ち消す。

失う担保は「Bean が生成できる」ところまでで、**`issuer` / `audience` の値が正しいかも、JWKS を実際に引けるかも元々検証していない**（`TestJwtSupport` の KDoc に明記のとおり射程外）。これは #813（JWKS 取得・RS256 検証を通すテストが無い状態を埋めるか判断する）が埋めようとしている領域と地続きであり、そちらへ引き継ぐ。

## Decision（決定）

- `src/test` の `@SpringBootTest` のうち **web 環境を要する 5 クラス**（`ApiApplicationTests` / `McpDisabledByDefaultTest` / `HealthEndpointTest` / `OpenApiTest` / `SecurityConfigTest`）を **`RANDOM_PORT` + `@AutoConfigureRestTestClient` + `@Import(TestJwtDecoderConfiguration)` の単一構成**に揃え、1 つのコンテキストを共有させる。
- 手放す「本番 `issuer-uri` 設定で `JwtDecoder` Bean が生成されることの担保」は **#813 へ引き継ぐ**。#813 で JWKS 取得・RS256 検証を通すテストを入れれば、失った担保より強い形で復活する。
- **`@WebMvcTest` スライスの 11 個は削減対象にしない**。コントローラ数の機械的帰結であり、`controllers` 引数を束ねるとスライステストの意図（そのコントローラだけを載せる）が壊れる。

## Consequences（結果・影響）

- CI の `:test` が中央値 84.93s → 67.55s（**-20.5%**）。distinct コンテキスト 18 → 16、Tomcat 2 → 1、HikariCP プール 7 → 5。
- **本番 `issuer-uri` 設定で `JwtDecoder` Bean が生成されることを確かめるコンテキストが、リポジトリから無くなる**。設定の消失や自動構成の破損は、この変更以降 E2E でも検知できない（E2E も decoder を差し替えるため）。#813 が着手されるまでこの穴は開いたままである。
- 速度改善の**機序は未特定**のまま採用している。したがって「コンテキストを 1 個減らせば約 N 秒縮む」と一般化してはならない。実測では 18→17 と 17→16 で効果が 2 倍以上違った。次に構成をいじるときも**推論ではなく実測で確かめる**（ローカルはノイズが効果より大きいため CI で測る）。
- 5 クラスは「同一キーであること」に依存して速い。**どれか 1 つに `@MockkBean` や `@TestPropertySource` を足すと、そのクラスだけ別コンテキストへ分岐して効果が失われる**。この規律は `.claude/rules/testing.md` に書く（ArchUnit / detekt では強制できない）。
- `ApiApplicationTests` / `McpDisabledByDefaultTest` は `RestTestClient` も JWT も使わないが、キー一致のために `@AutoConfigureRestTestClient` と `@Import` を持つ。読み手には不自然に見えるため、各ファイルに理由と本 ADR への参照を残す。
