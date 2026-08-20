---
paths:
  - "src/test/**"
  - "detekt-rules/src/**"
  - ".github/workflows/**"
  - "scripts/**"
  - "lefthook.yml"
  - ".claude/hooks/**"
  - ".claude/settings.json"
  - ".claude/rules/gates.md"
---

# ゲートの非空振り規約

このリポジトリは「規約は機械強制する」前提で運用している。したがって**空振りしているゲートは、守れているように見えて実は何もしていない**状態になり、「ゲートが緑である」ことを根拠に使う後続の判断まで汚染する（#780 では、空振りしていたガードが防ぐはずの競合が実際に起き、偽の BUILD FAILED を見たエージェントが自分の変更を疑って原因を探しにいく二次被害まで出た）。

対象は ArchUnit / detekt カスタムルール / Claude hook / CI ジョブ / lefthook のすべて。

## 原則

- **ゲートを新設・変更したら、わざと違反する状態を作って落ちることを見るまで完了としない**。緑のまま通ったなら、それは「違反が無い」かもしれないし「検出が空振りしている」かもしれない。両者は出力で区別できない
- **確認したことは PR 本文に実測（FAIL したときの出力の抜粋）付きで残す**。「確認した」という記述は後から検証できないが、出力は検証できる
- **「緑だった」は非空振りの根拠にならない**。ゲートを導入した PR が緑であることは、そのゲートが動いた証拠ではない（#780 のガードは導入以降ただの一度も発火していなかった）

## 種別ごとのミューテーションのやり方

「何を壊せば FAIL するはずか」を先に決めてから壊す。想定どおりに落ちなければ、落ちない理由が分かるまでゲートは未完成とみなす。このとき**そもそも違反が成立しているか**を先に疑う。壊し方を間違えると「ゲートが空振りしている」ではなく「違反になっていないので落ちない」を見ることになり、正しいゲートを壊れていると誤診する（実測: 下の Claude hook の例を `.md` の行末空白で試すと exit 0 になる。`.editorconfig` が `[*.md] trim_trailing_whitespace = false` としているため違反ではない。`.kt` で試せば exit 2 で発火する）。

- **ArchUnit**: 許可パッケージ定数を一時的に bogus 値へ変えるか、規約を守っているコードのアノテーションを外し、テストが FAIL することを確かめてから戻す。`noClasses().should()` はマッチが 0 件なら違反 0 件＝パスするため、述語のバグは無言の偽陰性になる。Kotlin 固有の空振り原因（inline value class のメソッド名マングリング・companion object の owner）は `.claude/rules/architecture.md` の「ArchUnit で Kotlin の呼び出しを縛るときの空振り」にある
- **detekt カスタムルール**: `detekt-test` の `rule.lint(...)` に違反スニペットを食わせ、`findings` が期待どおり出ることと、適合コードでは出ないことの両方を見る（先例: `ActorScopedUseCaseTest` / `WorldScopedPortSignatureTest`）
- **Claude hook**: hook は stdin の JSON で駆動するので、想定するペイロードを流し込んで単体実行し、exit code と stderr を見る。

  ```bash
  echo '{"tool_input":{"file_path":"path/to/violating.kt"}}' | .claude/hooks/post-edit-editorconfig.sh
  echo $?   # 違反を検出したなら 2
  ```

  実行中のプロセスやリポジトリの状態を判定に使う hook は、**その判定文字列が実際に何にマッチするか**まで確かめる（#780 のガードは `org.gradle.wrapper.GradleWrapperMain` がどのプロセスにも当たらず、構文としては正しいまま一度も発火しなかった）
- **CI ジョブ**: 検査の本体はワークフローのインラインではなく `scripts/*.sh` へ切り出す。違反状態を作ってローカルで直接実行するだけで発火を確認できる（先例: `scripts/check-adr-numbering.sh` と `.github/workflows/adr-check.yml`）。加えて **`paths` フィルタがその変更でジョブを起動するか**を別途確かめる。フィルタが外れているとゲート本体が正しくてもジョブごと走らない
- **lefthook**: 違反ファイルを stage して `lefthook run pre-commit`（push 側は `lefthook run pre-push`）を実行し、当該コマンドが skip されずに落ちることを見る。**glob が効かず、対象ファイルだけのコミットで素通りする**穴が繰り返し出ている（#800 / #804）。glob を書いたら「そのファイル 1 本だけを stage したとき」に走るかを必ず確かめる

## 一度きりのミューテーションで終わらせない

一時的に壊して戻す確認は、そのとき見た空振りしか防げない。違反サンプルを恒久的な fixture として置ける種別（ArchUnit / detekt カスタムルール）では、FAIL 側を検証する回帰テスト（ArchUnit なら違反サンプル fixture ＋ `assertNotSatisfied` 系、detekt なら違反スニペットの `lint`）を併せて置く（先例: `AggregateNotDataClassRuleTest` / `DtoDomainEnumRuleTest` / `ControllerPackageLayoutRuleTest`）。

## この規約自体について

この規約はレビュー担保であり、機械強制していない（「ミューテーションを実施したか」を検査するゲートは無い）。
