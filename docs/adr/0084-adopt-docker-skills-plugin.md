# 0084. Docker 公式の Agent Skills（docker/skills）をプラグインとしてリポジトリ管理で宣言する

- Status: Accepted
- Date: 2026-09-27
- Deciders: Matsui

## Context（背景・課題）

Docker 社が AI コーディングエージェント向けの公式スキル集 [docker/skills](https://github.com/docker/skills)（Apache-2.0）を公開した。Dockerfile の最適化・Compose の設定パターン・破壊的操作のガードレール等を、Docker 公式ガイドラインに沿って `SKILL.md` 形式で提供する。Claude Code には `docker` マーケットプレイスの `docker-skills` プラグインとして導入でき、決定時点（v0.3.0）では次の 11 スキルが **1 プラグインに同梱**されている。

- Dockerfile & Build: `docker-project-foundations` / `docker-build-strategies`
- Compose: `docker-compose-patterns`
- Docker Sandboxes: `docker-sandboxes-lifecycle` / `docker-sandboxes-network-credentials` / `docker-sandboxes-env`（実験的）/ `docker-sandboxes-kits`（実験的）
- Docker Agent: `docker-agent-config` / `docker-agent-run` / `docker-agent-deploy`
- 横断: `docker-destructive-guardrails`

toy-box での Docker の使いどころは、ローカル Postgres の `compose.yaml`（spring-boot-docker-compose）、Testcontainers、Docker を要求する pre-push / Gradle テストのガード（[ADR-0071](0071-pre-push-docker-fail-fast-guard.md) / [ADR-0080](0080-docker-guard-on-gradle-test-tasks.md)）である。本番イメージは Dockerfile を持たず `bootBuildImage`（Paketo buildpacks）で作る。

検討した代替案:

- **関連スキルだけを `.claude/skills/` に vendoring する**（`docker-compose-patterns` / `docker-destructive-guardrails` / `docker-build-strategies` 等）。使わない Sandboxes / Docker Agent 系のスキル description が常時ロードされずに済むが、上流追従が手動コピーになり、スキルの出所がリポジトリ内に紛れる。今回は採らず、上流の配布単位（プラグイン）のまま受け入れる。
- **各自が `/plugin install` でアドホックに入れる**。クローンした人ごとに構成が揃わず、[ADR-0003](0003-consolidate-mcp-config-in-repo.md) / [ADR-0046](0046-adopt-kotlin-lsp-plugin.md) の「リポジトリ管理の設定で宣言して共有する」方針に反するため採らない。

## Decision（決定）

`.claude/settings.json` に次を宣言し、クローンすれば同一構成で Docker Skills が有効化候補になるようにする。

- `extraKnownMarketplaces.docker`: GitHub の `docker/skills` をマーケットプレイスとして登録する。`ref` で**タグを固定**する（決定時点は `v0.3.0`。現行値の出所は `.claude/settings.json`）。
- `enabledPlugins`: `"docker-skills@docker": true`。

タグ固定にするのは、pre-1.0 で公開直後に版が短期間で進んでいる第三者のスキル（＝エージェントへの指示）を、レビューを経ずに取り込まないため。Dependabot はこの参照を追跡しないので、更新は上流のリリースを確認したうえで `ref` を手で上げる。

## Consequences（結果・影響）

- Compose の設定・Docker の破壊的操作・コンテナイメージまわりの作業で、エージェントが Docker 公式ガイドラインに沿った判断をしやすくなる。
- **常時ロードの footprint が増える**: プラグインは 11 スキルを同梱しており、このリポジトリでは使わない Sandboxes / Docker Agent 系を含む全スキルの description がセッションに載る。スキルの誤発火や footprint が問題になった場合は、`enabledPlugins` を false にするか、関連スキルのみの vendoring（Context の代替案）へ切り替えを再評価する。
- **ガイドラインの衝突**: 本番イメージは Dockerfile ではなく `bootBuildImage` で作る方針であり、Dockerfile 前提のスキル（`docker-project-foundations` / `docker-build-strategies`）の推奨はそのままは当てはまらない。スキルの助言よりリポジトリの既存方針（`build.gradle.kts` の `bootBuildImage` 設定、CLAUDE.md / ADR）を優先する。
- **信頼承認**: プラグインの取り込みは初回にユーザーの信頼承認を経る（無確認では走らない）。取得元は `github.com`（devcontainer の egress 許可リスト `.devcontainer/allowed-domains.txt` に既存）。
- **保守**: `ref` の更新は手動。上流が 1.0 に達する、あるいはプラグインが製品別に分割されたら、固定の要否と取り込む範囲を再評価する。
- 運用ルールの結論は CLAUDE.md「MCP サーバー設定」に記載（経緯は本 ADR）。
