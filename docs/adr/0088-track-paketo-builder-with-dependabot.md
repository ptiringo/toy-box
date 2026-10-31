# 0088. Paketo builder をバージョン固定のまま Dependabot で週次更新する

- Status: Accepted
- Date: 2026-10-31
- Deciders: Matsui

## Context（背景・課題）

2026-10-23、上流の `paketobuildpacks/builder-noble-java-tiny:latest` が arm64 だけの単一 manifest に差し替わり、`bootBuildImage` が x64 ランナーで `exec format error` になった。PR #986 で builder を `0.0.200` に固定し、`imagePlatform = "linux/amd64"` を明示して復旧した。

固定すると、builder が供給する JRE と run image の版も一緒に止まる。2026-10-31 の実測では、固定中の `0.0.200` に対して上流は `0.0.205` まで進んでおり、run image（`ubuntu-noble-run-tiny`）は 0.0.150 → 0.0.153、Liberica buildpack は 11.10.1 → 11.10.4 の差があった。一方、Dependabot は `build.gradle.kts` の builder 文字列を更新しない（#990）。

検討して採らなかった案は次のとおり。

- **`latest` に戻して週次で定期デプロイする**: 追跡の仕組みは要らないが、同じコミットでもビルド時期でイメージが変わる。スモークテストを通らないまま本番へ入り、同じ SHA タグの上書きも起きる。
- **Renovate で任意の文字列を追う**: 既存の 4 エコシステムを Dependabot で運用しており、この 1 件のために更新ツールを増やす理由がない。
- **定期的に手で確認する**: 忘れると古いまま残る、という固定の問題そのものを解消しない。

## Decision（決定）

1. builder をバージョンタグに固定したまま、`gradle/paketo-builder/Dockerfile` の `FROM` 行で宣言し、`build.gradle.kts` はその行を読む。
2. この Dockerfile はバージョンの宣言専用で、ここからイメージをビルドしない。本番イメージは引き続き `bootBuildImage` で作る。
3. `.github/dependabot.yml` に `docker` エコシステムを追加し、週次で更新 PR を出す。cooldown は決定時点で 3 日（現行値の出所は同ファイル）。
4. amd64 を含むことの確認は、手順ではなく PR の Container Smoke Test に任せる。x64 ランナーで `imagePlatform = "linux/amd64"` のビルドと起動を行うため、amd64 が欠けた版はここで落ちるはずである。

## Consequences（結果・影響）

- 同じコミットからは同じ builder でビルドされ、builder の変更履歴が PR に残る。スモークテストを通らない版は本番に入らない。
- `main` へのマージで `deploy.yml` が走るため、更新 PR のマージがそのまま本番への反映になる。
- **更新 PR は手でマージする必要がある**（リポジトリは auto-merge を許可していない）。放置すると JRE と OS イメージが古いまま本番に残る。この放置を検知する仕組みは置いていない。
- 反映までの遅れは、週次の間隔と cooldown にマージ待ちの時間を足したものになる。
- ビルドに使わない Dockerfile がリポジトリに残る。誤解を避けるため、ファイル先頭のコメントに用途を書いている。
- **未検証**: Dependabot がこのタグ形式を更新できることは、マージ前には確かめられない。マージ後に新しい版の PR が届かなければ、この方式を見直す。
- **再検討条件**: Dependabot が Gradle の文字列や Paketo builder を直接追えるようになったら、Dockerfile をやめる。
