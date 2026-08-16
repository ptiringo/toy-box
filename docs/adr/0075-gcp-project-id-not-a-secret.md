# 0075. GCP プロジェクト ID は秘匿対象にせず、識別子の扱いは開示済みかどうかで分ける

- Status: Accepted
- Date: 2026-08-16
- Deciders: Matsui

## Context（背景・課題）

[ADR-0074](0074-billing-guardrails-spend-cap-and-budget-alert.md) の作業中に、「攻撃面を減らすため GCP プロジェクト ID（`ptiringo-toy-box`）をリポジトリから消したい」という検討が持ち上がった。公開リポジトリに本番プロジェクトの識別子が平文で並んでいる状態は、直感的には落ち着かない。

一方で同じ作業で、**請求先アカウント ID は ADR の記述から削除し、HCP Terraform の workspace 変数 `billing_account_id` を出所とする**扱いに変えた。同じ「GCP の識別子」でありながら逆の判断をしており、その線引きがどこにも書かれていない。放置すると同じ検討が再燃する。

### プロジェクト ID は原理的に秘匿できない

消したところで、動いているシステムが利用者へ配っている。

- **ID トークンに入っている**。`src/main/resources/application.yml` の OAuth2 リソースサーバ設定は `issuer-uri: https://securetoken.google.com/${GCP_PROJECT_ID}` / `audiences: ${GCP_PROJECT_ID}`（[ADR-0064](0064-authn-via-identity-platform-authz-in-app.md)）。ログインした利用者は全員、自分の JWT の `iss` / `aud` としてプロジェクト ID を受け取っている。
- **ログイン画面がブラウザへ配信している**。`frontend/src/auth/firebase.ts` の `authDomain` は `<project-id>.firebaseapp.com`。値はリポジトリではなく env（`VITE_FIREBASE_AUTH_DOMAIN`）由来だが、Vite のビルド成果物に埋め込まれてブラウザに届くため、リポジトリから消しても利用者からは見える。
- **公開リポジトリの org 名とリポジトリ名から推測できる**。実際 `ptiringo` / `toy-box` の連結そのものである。

（Cloud Run の URL に現れるのはプロジェクト**番号**でプロジェクト ID とは別値だが、「プロジェクトを指す識別子はエンドポイントに露出する」という点では同じ性質を持つ。）

### 知られても攻撃者にできることが増えない

プロジェクト ID は IAM の宛先を指す識別子であって認証 material ではない。API を叩くには結局 IAM で認可されたアイデンティティが要る。Google 自身も秘密として扱っておらず、API レスポンスにも URL にも現れる。

### 消すコストは実在する

`ptiringo-toy-box` はリポジトリ内に 17 行 / 9 ファイル存在する（このファイルを除く。うち 6 行は ADR 本文の記述で、残りが実配線）。

| 種別 | ファイル |
| --- | --- |
| infra | `infra/main.tf` / `infra/providers.tf` / `infra/modules/identity-platform/variables.tf` |
| CI/CD | `.github/workflows/deploy.yml` |
| frontend | `frontend/src/pages/LoginPage.tsx`（ログイン画面の表示文字列） |
| 指示ファイル | `.claude/rules/gcp-guardrails.md` |
| ADR | `0036` / `0064` / `0074` |

消すには workflows の repository variable 化・HCP 変数の追加・frontend の env 配線が要り、deploy の回帰リスクを負う。加えて SA メール（`deployer@ptiringo-toy-box.iam.gserviceaccount.com` 等）と Firebase ドメインは ID を部分文字列として含むため、機械的な置換では消えない。security by obscurity の対価としては見合わない。

## Decision（決定）

**GCP プロジェクト ID（`ptiringo-toy-box`）を秘匿対象として扱わない。** リポジトリ・ADR・rules に平文で書いてよく、消す作業は行わない。

**識別子を書くかどうかは「動いているシステムが既に開示しているか」で決める。**

- **開示済みの識別子は書く**（プロジェクト ID、Cloud Run の URL、SA のメールアドレス）。隠す効果が無く、隠すコストだけが残るため。
- **未開示の識別子は書かない**（請求先アカウント ID）。誰にも配っていない値は、出さない選択にコストがかからない。出所は HCP Terraform の workspace 変数などリポジトリ外に置き、ドキュメントからは変数名で参照する。

秘密（API キー・トークン・認証情報）はこの線引き以前の問題として、いずれもリポジトリに置かない（[ADR-0004](0004-secrets-fnox-1password.md)）。

## Consequences（結果・影響）

- プロジェクト ID を隠す作業（workflows の variable 化・frontend の env 配線）を行わないので、deploy 経路に手を入れずに済む。ドキュメントは実値を書けるため、コマンド例をそのまま貼れる状態が保たれる。
- 攻撃面の削減は識別子の秘匿ではなく、**権限と監査**で行う。ローカルは最小権限 viewer SA の impersonation に限定し（[ADR-0036](0036-gcp-operation-guardrails.md)）、変更は正規ルート（GitHub Actions / HCP Terraform run）へ寄せる。実際の施策は #472（blast radius 縮小）/ #473（Cloud Audit Logs）/ #475（viewer SA impersonation の env 配線）で追う。
- 「開示済みかどうか」という線引きは判断を要する。新しい識別子を書くときは、それが利用者・ブラウザ・エンドポイントのいずれかに露出しているかを確認する。露出していないなら書かない。
- プロジェクト ID が公開前提である以上、**IAM の設定ミスがそのまま攻撃面になる**。プロジェクトを特定する手間が攻撃者側のコストとして期待できないため、権限側の緩みは identifier の秘匿では埋め合わせできない。
