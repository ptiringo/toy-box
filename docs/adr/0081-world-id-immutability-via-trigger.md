# 0081. world_id の不変性を BEFORE UPDATE トリガで強制する

- Status: Accepted
- Date: 2026-09-10
- Deciders: Matsui

## Context（背景・課題）

Issue #727。[ADR-0067](0067-per-player-world-tenant-isolation.md) でデータを世界（セーブデータ＝テナント）
ごとに閉じ、#704 で `world_id` 列と複合 FK を入れた。しかし複合 FK が封じたのは「他人の世界の行を
**参照**する」経路だけで、「自分の行を他人の世界へ**移す**」経路は空いたままだった。

Spring Data JDBC の UPDATE は主キー（＋ `version`）で行を特定する。したがって `save(worldId, aggregate)`
に誤った `worldId` を渡すと、既存行の `world_id` が黙って書き換わる。通常経路では起きない（読み取りが
常に `actor.worldId` で絞られるため、ユースケースが掴める集約は自分の世界のものに限られる）が、それは
「今の呼び出し方がたまたま正しい」だけで構造的な保証ではない。抑止は
`JdbcBloodHorseRepository.save` の KDoc の注意書きだけで、コメントは機械強制ではなかった。

**守備範囲は当初の想定より狭くない**。集約間の複合 FK を持つテーブル（`blood_horse` 等）は、`world_id`
を書き換えると参照先が移動先の世界に無くなるため、副作用として FK 違反で弾かれる（#727 の作業中に実測。
`blood_horse` を対象にした契約テストがトリガ導入前から緑になり、空振りに気づいた）。一方、他集約を
参照しないテーブル（`horse_inspection` / `jockey` / `idempotency_record`）はどの制約にも掛からず素通り
する。CHECK / UNIQUE / FK を多層防御に使ってきた既存路線（[ADR-0043](0043-aggregate-to-table-mapping-guidelines.md) /
[ADR-0053](0053-foreign-key-backstop-across-aggregates.md)）に照らすと、ここだけ防御が言葉で止まっていた。

検討した代替案:

- **列レベルの `REVOKE UPDATE(world_id)`**。宣言的で「DB にロジックが宿らない」点が魅力だが、
  **所有者は権限検査を迂回する**。本番（Prisma Postgres への env 注入）もテスト（Testcontainers）も
  アプリがテーブル所有者と同一ユーザーで接続しているため効かない。ロールを分けるのは運用の変更であり、
  この Issue のスコープを超える。
- **アプリ側で `save` の前に現在の `world_id` を読んで照合する**。read-then-write の窓が空くうえ、
  同じコードが全リポジトリに散る。DB 単独では依然として破れる。
- **CHECK 制約**。`OLD` を参照できないため表現できない。PostgreSQL には列単位の immutable 指定も無い。

## Decision（決定）

`world_id` を書き換える UPDATE を **`BEFORE UPDATE` トリガで拒否する**。

- 拒否関数は `iam.reject_world_id_update()` の **1 本だけ**作り、全コンテキストのトリガから共有する。
  置き場所を `iam` にするのは、`world_id` がコンテキスト間参照ではなく全コンテキストを横断する
  テナント軸だからで、V19 が `world_id` → `iam.world` のクロススキーマ FK を
  [ADR-0048](0048-per-context-db-schema-namespaces.md) の例外として認めたのと同じ論法による。
- トリガは `FOR EACH ROW` かつ `WHEN (OLD.world_id IS DISTINCT FROM NEW.world_id)` で張る。Spring Data
  JDBC の UPDATE は全列を書くため、通常の更新でも `world_id` は同値で毎回書き込まれる。`WHEN` 句が
  本番経路を巻き込まないことが前提条件になる。
- **`RAISE EXCEPTION` の `ERRCODE` に `23514`（check_violation）を明示する**。既定の SQLSTATE は `P0001`
  （PL/pgSQL Error クラス）で、Spring の `SQLStateSQLExceptionTranslator` はこのクラスを知らず
  `UncategorizedSQLException` に落とす。`23514` なら `DataIntegrityViolationException` に訳され、
  「起きたらプログラミングエラー」という位置づけどおり制約違反として届く（ユースケースには業務エラー
  ではなくインフラ例外として伝わり 500 になる。意図した挙動）。
- トリガを張る対象は `world_id` を持つ全テーブル。マイグレーション（V23）には**列挙して明示的に書く**。
  `DO` ブロックで動的に張らないのは、マイグレーションが表すのは適用時点のスキーマであり、後から増えた
  テーブルには効かないため。将来の張り忘れは `WorldScopeSchemaRulesTest` が `pg_trigger` を動的に列挙して
  `./gradlew check` で落とす（照合はトリガ名ではなく呼び出す関数名と `tgtype` のビットで行い、命名規約への
  依存を避ける）。

**このリポジトリで初のトリガ導入であり、線引きを決めておく。トリガに書いてよいのは不変条件の backstop
（＝アプリのバグで壊れうる構造的な前提を DB 単独で守ること）に限り、業務ロジックは DB に置かない。**
判断の分かれ目は「アプリのコードに対応物があるか」で、対応物があるものは DB へ二重化せずアプリに置く。

## Consequences（結果・影響）

- 行の世界間移動がアプリのバグでは起こせなくなった。テナント分離の最後の穴（#704 の積み残し）が閉じ、
  `.claude/rules/migrations.md` が「レビュー担保」としていた項目が 1 つ機械強制へ移った。
- 引き換えに、**DB にロジックが宿る構成を許容した**。トリガはスキーマを読むだけでは挙動が見えづらく、
  マイグレーションを追わないと「なぜこの UPDATE が落ちるのか」が分からない。上の線引きを守り、
  トリガの本数を増やさないことでコストを抑える。
- `world_id` を正当に移動させる要件が将来出た場合（世界の統合・移管など）、この決定を覆す必要がある。
  そのときは新しい ADR を起こす。トリガを一時的に無効化する運用手順は用意しない（無効化の手段を
  用意すること自体が backstop を骨抜きにするため）。
- 複合 FK を持つテーブルでは FK とトリガの二重防御になる。冗長だが、FK による拒否は
  「参照先が無くなること」の副作用にすぎず、`world_id` の不変性そのものを表明していないため残す。
