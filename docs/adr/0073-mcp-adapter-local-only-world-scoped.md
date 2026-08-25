# 0073. MCP アダプタを local プロファイル限定の世界スコープ探索ツールとして再導入する

- Status: Accepted
- Date: 2026-08-11
- Deciders: ptiringo

## Context（背景・課題）

[ADR-0035](0035-mcp-interface-adapter.md) で adapter リングに追加した `mcp` パッケージは、#704（既存
テーブルの世界スコープ化）で削除された。理由は認証ではなく**テナント分離に追従できなかったこと**にある。
#704 で application 層のユースケースは第 1 引数に `Actor`（`accountId` + `worldId`）を取る形へ変わったが、
MCP には HTTP リクエストも JWT も無く、`Actor` を組む材料が無い。`worldId` をツール引数に足せば形の上では
コンパイルが通るものの、`/mcp/**` は `permitAll`（[ADR-0064](0064-authn-via-identity-platform-authz-in-app.md)）で
アカウントの特定手段が無いため、**誰でも任意の世界の UUID を渡して他人の世界を読める穴**になる。当時は
穴を開けるより削除を選び、ArchUnit の `adapter("mcp", MCP)` 登録も `mcp` が空になったため外して、再導入を
#712 へ持ち越していた。

ADR-0035 は「認証は当面入れない（follow-up）」としていた。当時この判断が成立したのは、そもそも認可判断が
存在せず、`permitAll` が「全データを読める口」という以上の意味を持たなかったからである。
[ADR-0067](0067-per-player-world-tenant-isolation.md) でテナント分離（世界の所有関係）が入った時点で前提が
変わり、無認証の口は**テナント境界を素通りする穴**へ意味を変えた。ADR-0035 の follow-up を先送りしたまま
MCP を戻すことはできない。

一方で実態を確認すると、MCP は既に「ローカルからしか使われていないもの」だった。本番（Cloud Run）は
`deploy.yml` が `--no-allow-unauthenticated` でデプロイし、`infra/` に `allUsers` を invoker として与える
リソースは存在しない。つまり MCP エンドポイントは本番では外部から到達できず、実際の利用はローカルの
`bootRun` に限られている。「プレイヤー（エンドユーザー）に MCP を開く」という需要は現時点で無く、欲しいのは
**開発者が自分の箱庭を LLM エージェント越しに覗く探索ツール**である。ADR-0035 が walking skeleton で
実証しようとした学習目的（`Result` → tool result 変換・adapter リングとしての ArchUnit 適合・slice テスト）も
そのまま残っている。

### 却下案 1: MCP を世界スコープの外に置く横断ビューにする

MCP だけは `Actor` を要求しない専用のクエリ経路を持ち、全世界を横断して読める「管理者ビュー」にする案。
`Actor` を組む問題は消えるが、application 層に世界スコープを持たない読み取りポートを新設することになり、
ADR-0067 が「唯一の認可判断は世界の所有関係」と定めた前提と正面から矛盾する。プレイヤーの箱庭という
モデルにおいて、箱庭の外から全部見えるビューは存在してはいけないものであり、そこに口を開ければ
「世界スコープを通らない読み取り経路」が恒久的に残る。採らない。

### 却下案 2: MCP に OAuth（`mcp-security`）を載せる

`spring-ai-community/mcp-security` で OAuth2 保護を掛け、MCP クライアントにもトークンを持たせる案。
筋は通るが、Identity Platform は**プレイヤー向けの ID トークン発行者**であって MCP クライアント向けの
authorization server ではない。MCP の OAuth（動的クライアント登録・authorization server metadata）に
乗せるには IdP 側の設計から起こす必要があり、「開発者が自分の世界を覗く」という現在の用途に対して過大に
なる。プレイヤー向けの機能として MCP を開く段になれば必要になるが、その時点で改めて設計する。

### 却下案 3: MCP アダプタ自体を撤回する（ADR-0035 を Superseded にする）

#704 の削除をそのまま確定させ、`mcp` を作らない案。最も単純だが、ADR-0035 が置いた「REST と並ぶ第 2 の
インターフェースアダプタを持ち、application 層を無変更で再利用できることを確かめる」という学習目的は
まだ果たし切れていない（walking skeleton がユースケース 1 本で消えた）。世界スコープ化という制約が増えた
状態で同じ骨格が成立するかは、むしろ確かめる価値が上がっている。撤回はしない。

## Decision（決定）

**MCP を「開発者自身のローカル探索ツール」と位置づけ、テナント分離の内側に置いて再導入する。
`accountId` は設定で固定し、ツール引数で受けるのは `worldId` だけとする。有効化は `local` プロファイル
限定とする。**

- **`accountId` は設定（`toy-box.mcp.subject-id`）で固定する。** MCP には JWT が無いため、REST が JWT の
  `sub` から解決している「誰が操作しているか」を設定で与える。値は `application-local.yml` が
  `MCP_SUBJECT_ID` 環境変数から受け取る（個人の subject をリポジトリに書かないため）。設定バインドは
  `McpProperties`（`@ConfigurationProperties("toy-box.mcp")`）が担う。
- **`Actor` は `McpActorFactory` が 3 手で組む。** 設定の subject → `AccountRepository.findBySubjectId` →
  `AccountId`、ツール引数の `worldId` → `WorldQueries.existsOwnedBy` → `Actor`。REST の
  `ActorArgumentResolver` と同じ 3 手だが、入力の出所だけが違う（JWT の `sub` が設定に、パスの `{worldId}` が
  ツール引数に替わる）。adapter 同士の参照は規約で禁止されているためコードは共有せず、この 3 手を MCP 側に
  持つ。設定漏れ・未登録 subject はいずれも配線の誤りであり業務エラーではないため fail-loud で落とす。
- **`local` プロファイル限定を二重に効かせる。** `application.yml` は `spring.ai.mcp.server.enabled: false`
  で口ごと閉じ、`application-local.yml` が `true` で開ける（エンドポイントの有無）。加えて `McpConfig` /
  `McpActorFactory` / 各ツール Bean に `@Profile("local")` を付ける（ツールの登録有無）。どちらか一方でも
  外れれば MCP は動かない。
- **`/mcp/**` の `permitAll` も `local` 限定にする。** `SecurityConfig` は
  `Environment.acceptsProfiles(Profiles.of("local"))` のときだけ `/mcp/**` を permitAll のリストへ足す。
  既定プロファイルではそもそもエンドポイントが存在しないため、これは実質的には意図の表明にあたる。
- **ローカルの `bootRun` は `local` プロファイルで起動する**（`build.gradle.kts` の `bootRun` に
  `args("--spring.profiles.active=local")`）。本番（Cloud Run）はプロファイルを指定しないため既定＝MCP off の
  ままとなる。
- **公開するツールは 2 本**: `list_worlds`（`WorldMcpTools`。自分のアカウントが持つ世界の一覧。他のツールが
  要求する `worldId` を調べる入口であり、これがあるおかげで設定に世界の UUID を書かずに済む）と
  `get_jockey`（`JockeyMcpTools`。ADR-0035 の walking skeleton を世界スコープに載せて再建したもの）。
- **ArchUnit の `adapter("mcp", MCP)` を復活させる**（`OnionLayerRulesTest.onionLayers`）。#704 で外した
  adapter 登録を戻し、`mcp` は再び adapter リングとして機械強制の対象になる。

### なぜ #704 が塞いだ穴が開き直さないか

この API の唯一の認可判断は `WorldQueries.existsOwnedBy(accountId, worldId)`（ADR-0067）であり、MCP でも
それは変わらない。**`accountId` を呼び出し側が指定できない**ことが要で、他人の世界の UUID をツール引数に
渡しても所有関係が成立せず弾かれる。存在しない世界と他人の世界は区別せず、いずれも
`NoSuchElementException`（MCP の `isError` ツール結果）に潰す（403 と 404 を分けないのと同じ理由。ADR-0067）。
#704 が懸念した「`worldId` を引数に足すと認証なしで他人の世界を読める」は、`accountId` が可変であることに
由来していた懸念であり、設定で固定した時点で消える。

application 層には**新しいポートも新しい認可判断も足していない**。MCP は REST が使っているのと同じ
`ListWorldsUseCase` / `GetJockeyUseCase` / `WorldQueries` をそのまま呼ぶ。したがって「MCP からだけ通れる
抜け道」は構造上作られない。

## Consequences（結果・影響）

- **ADR-0035 の follow-up「MCP の認証」がここで決着する。** ただし決着の形は「認証を載せた」ではなく
  「認証を要さない位置（開発者のローカル環境）に閉じ込め、テナント分離の内側へ入れた」である。
  ADR-0035 の「本番デプロイは `--no-allow-unauthenticated` なので全プロファイルで有効化してもリスクが無い」
  という論拠も、本 ADR の既定 off に置き換わる（到達不能であることに寄りかからず、口自体を開けない）。
- **ADR-0064 の「MCP エンドポイントは `permitAll` のまま残る」を更新する。** permitAll は `local` プロファイル
  限定になった。CLAUDE.md の「認証」節の `permitAll` 一覧にもこの限定を反映する。
- **プレイヤー向けに MCP を開くときは、改めて OAuth の設計を起こす必要がある。** 本 ADR の設計は
  「`accountId` が設定で固定される 1 人用」に最適化されており、複数プレイヤーが同時に使う形へは拡張できない。
  却下案 2 の検討はその時点でやり直しになる。
- **Kover の記述を訂正する。** ADR-0035 の「`mcp` パッケージは Kover の成熟ゲート対象外。成熟時点で
  `variant("mature")` の includes に追加する」は **includes 方式時代の記述**であり、現行の excludes 反転方式
  （[ADR-0040](0040-coverage-gate-operation-model.md)）では探索段階のパッケージだけを列挙して残り全部を
  ゲートするため、`com.example.api.mcp` は列挙されておらず**既にゲート母集団に入る**。`mcp` を excludes へ
  追加して逃げることはせず、`koverVerifyMature` の下限（現行値の出所は `build.gradle.kts`）を満たす
  （slice 相当のテストで賄う）。
- **本プロジェクト初の `@ConfigurationProperties` 利用になる。** `ApiApplication` に
  `@ConfigurationPropertiesScan` を足すとアプリ全体のスキャン方針を変えることになるため、`McpConfig` の
  `@EnableConfigurationProperties(McpProperties::class)` で局所的に有効化した。以後 `@ConfigurationProperties`
  を増やすときは、スキャンへ切り替えるか局所有効化を続けるかを判断すること（現状は 1 件なので局所で足りる）。
- **二重の守りは両方ともテストで押さえる。** 「`local` では MCP ツールが登録される」（`McpServerWiringTest`、
  `@ActiveProfiles("local")`）と「既定プロファイルでは MCP の Bean がひとつも存在しない」
  （`McpDisabledByDefaultTest`）を別々に検証する。片方だけだと、`@Profile` を外しても `enabled: false` に
  隠れて緑のまま通ってしまう。
- **今回踏んだ罠（後続の実装者向け）**: **`bootRun` にプロファイルを指定すると、springdoc-openapi-gradle-plugin
  の forked bootRun（`generateOpenApiDocs`）へ波及する。** 当初
  `systemProperty("spring.profiles.active", "local")` としたところ、forked 側のログにも
  `The following 1 profile is active: "local"` が出て MCP ツールが登録されていた（実測）。プラグインの実装
  （`OpenApiGradlePlugin.kt`）は `customBootRun` の `args` / `systemProperties` を「**非空ならそちらで
  `bootRun` のものを完全に置き換える／空なら `bootRun` 側へフォールバックする**」規則で扱う。本プロジェクトの
  `customBootRun` は既に非空の `args`（`--server.port=8090`）を持っているため、`bootRun` 側を
  `args("--spring.profiles.active=local")` にすることで波及を断てる。**`systemProperties.set(emptyMap())` に
  よる打ち消しは効かない**（空は「未指定」と同じ扱いで `bootRun` 側へフォールバックするため）。OpenAPI 仕様の
  生成物に MCP が混ざると vacuum の lint ゲート（[ADR-0054](0054-vacuum-openapi-lint.md)）まで巻き込むので、
  `bootRun` に何かを足すときは forked 側への波及を毎回確かめること。
- **認識している弱点**: `local` プロファイルで `bootRun` している間、`localhost:8080/mcp` に到達できる
  プロセスは設定された subject のアカウントとして振る舞える（認証が無いため）。これは開発者自身の端末に
  閉じた話であり、本番・CI・テストのいずれでも口が開かないことを前提に受け入れる。この前提が崩れる変更
  （例: `local` プロファイルを CI で使う、MCP を別ポートで常駐させる）を入れるときは本 ADR を見直すこと。
