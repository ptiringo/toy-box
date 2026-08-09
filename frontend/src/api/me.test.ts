import { afterEach, describe, expect, it, vi } from "vitest";
import { provisionMe } from "./me";

const token = async () => "id-token-123";

afterEach(() => {
  vi.restoreAllMocks();
});

function okResponse(): Response {
  return new Response(JSON.stringify({ account_id: "a1" }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

describe("provisionMe", () => {
  it("同一 uid の同時呼び出しは 1 リクエストに畳む", async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse());
    vi.stubGlobal("fetch", fetchMock);

    const [a, b] = await Promise.all([provisionMe("uid-1", token), provisionMe("uid-1", token)]);

    expect(a).toEqual({ account_id: "a1" });
    expect(b).toEqual({ account_id: "a1" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("失敗は覚えず、次の呼び出しでやり直せる", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 500 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(provisionMe("uid-3", token)).rejects.toMatchObject({ status: 500 });
    // リトライはしない（サーバが並行実行下でも冪等になったため。#713 / PR #738）。
    expect(fetchMock).toHaveBeenCalledTimes(1);

    // 失敗をキャッシュしないので、再試行ボタン相当の呼び直しでもう一度リクエストが飛ぶ。
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(okResponse()));
    await expect(provisionMe("uid-3", token)).resolves.toEqual({ account_id: "a1" });
  });

  it("成功は覚えるので、再マウント相当の呼び直しではリクエストしない", async () => {
    const fetchMock = vi.fn().mockResolvedValue(okResponse());
    vi.stubGlobal("fetch", fetchMock);

    await provisionMe("uid-4", token);
    await provisionMe("uid-4", token);

    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("uid が違えばそれぞれ実行する", async () => {
    // 2 uid とも実際に fetch されるため、Response を毎回新規に作る
    // （同一インスタンスを使い回すと 2 回目の .json() で body 読み取り済みエラーになる）。
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(okResponse()));
    vi.stubGlobal("fetch", fetchMock);

    await Promise.all([provisionMe("uid-5", token), provisionMe("uid-6", token)]);

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
