import { expect, test } from "@playwright/test";
import { createTestUser } from "./emulator";

/**
 * ログイン → 初回セットアップ → 世界の作成 → 馬一覧、までの通し（#725）。
 *
 * 守るのは「配線が繋がっていること」。分岐やエラー表示は jsdom 側（vitest）が担保する。
 */
test("ログインから馬一覧までを通しで辿れる", async ({ page }) => {
  const user = await createTestUser();

  // 未ログインで保護ルートを直叩きするとログイン画面へ落ちる。
  await page.goto("/worlds");
  await expect(page).toHaveURL(/\/login$/);

  // 実際に signInWithEmailAndPassword が走る（Emulator が ID トークンを発行する）。
  await page.getByLabel("メールアドレス").fill(user.email);
  await page.getByLabel("パスワード").fill(user.password);
  await page.getByRole("button", { name: "サインイン" }).click();

  // RequireProvisioned が POST /api/me:provision を通すと世界一覧が描かれる。
  // :provision は「はじまりの世界」を 1 つ作るため、この時点で一覧は空ではない。
  await expect(page.getByRole("heading", { name: "世界一覧" })).toBeVisible();

  // 2 つ目の世界を作る。
  await page.getByLabel("新しい世界の名前").fill("E2E の世界");
  await page.getByRole("button", { name: "作る" }).click();
  const createdWorld = page.getByRole("button", { name: "E2E の世界" });
  await expect(createdWorld).toBeVisible();

  // 作った世界の馬一覧へ遷移する。
  await createdWorld.click();
  await expect(page).toHaveURL(/\/worlds\/[0-9a-fA-F-]+\/bloodHorses$/);
  await expect(page.getByRole("heading", { name: "軽種馬登録原簿" })).toBeVisible();

  // 直近レスポンスが 200 ＝ Emulator の ID トークンがバックの検証を通り、世界スコープの
  // API まで到達した、ということ。ここが 401 なら projectId の不一致を疑う。
  await expect(page.locator(".status")).toHaveText("200");
});
