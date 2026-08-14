import { apiPost, type GetToken } from "./client";

/** バックの MeResponse（accountId は wire 上 snake_case）。 */
export type Me = {
  account_id: string;
};

// uid ごとの実行中／成功済み Promise。StrictMode の二重発火と再マウントを 1 リクエストに畳む。
// 失敗は覚えない（下の catch で消す）ので、再試行ボタンから引き直せる。
const provisioning = new Map<string, Promise<Me>>();

/**
 * 初回セットアップ（POST /api/me:provision）を通す。これを通さないと他の API は 403
 * account-not-provisioned を返す。
 *
 * サーバは並行実行下でも冪等（#713 / PR #738 で UNIQUE を裁定者にする形に変わった）なので、
 * 競合を見越したリトライはしない。ここで畳むのは無駄なリクエスト（StrictMode の二重発火・
 * 再マウント）だけ。
 */
export function provisionMe(uid: string, getToken: GetToken): Promise<Me> {
  const running = provisioning.get(uid);
  if (running !== undefined) {
    return running;
  }

  const started = apiPost<Me>("/api/me:provision", getToken);
  provisioning.set(uid, started);
  started.catch(() => {
    provisioning.delete(uid);
  });
  return started;
}
