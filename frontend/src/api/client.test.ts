import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiDelete, apiGet, apiPatch, apiPost, errorMessage } from "./client";

const token = async () => "id-token-123";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("apiGet", () => {
  it("Authorization: Bearer にIDトークンを載せてJSONを返す", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify([{ id: "a" }]), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const body = await apiGet<{ id: string }[]>("/api/bloodHorses", token);

    expect(body).toEqual([{ id: "a" }]);
    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBe("Bearer id-token-123");
  });

  it("401はApiError(status=401)として投げる", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 401 })));

    await expect(apiGet("/api/bloodHorses", token)).rejects.toMatchObject({
      status: 401,
    });
  });

  it("problem+jsonのdetailをApiErrorに載せる", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ title: "Forbidden", detail: "権限がありません", status: 403 }),
          {
            status: 403,
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );

    await expect(apiGet("/api/bloodHorses", token)).rejects.toMatchObject({
      status: 403,
      problem: { title: "Forbidden", detail: "権限がありません", status: 403 },
    });
  });

  it("トークン未取得（null）なら fetch せず ApiError(status=401)", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiGet("/api/bloodHorses", async () => null)).rejects.toMatchObject({
      status: 401,
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("apiPost / apiPatch / apiDelete", () => {
  it("POST は Bearer と Content-Type を載せ、ボディを JSON 化して送る", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: "w1", name: "はじまりの世界" }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const body = await apiPost<{ id: string }>("/api/worlds", token, { name: "はじまりの世界" });

    expect(body).toEqual({ id: "w1", name: "はじまりの世界" });
    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/worlds");
    expect(init.method).toBe("POST");
    expect(init.headers.Authorization).toBe("Bearer id-token-123");
    expect(init.headers["Content-Type"]).toBe("application/json");
    expect(init.body).toBe(JSON.stringify({ name: "はじまりの世界" }));
  });

  it("ボディなしの POST は Content-Type を付けない", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ account_id: "a1" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await apiPost("/api/me:provision", token);

    const [, init] = fetchMock.mock.calls[0];
    expect(init.body).toBeUndefined();
    expect(init.headers["Content-Type"]).toBeUndefined();
  });

  it("PATCH はメソッドとボディを送る", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ id: "w1", name: "2周目" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await apiPatch("/api/worlds/w1", token, { name: "2周目" });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.method).toBe("PATCH");
    expect(init.body).toBe(JSON.stringify({ name: "2周目" }));
  });

  it("DELETE の 204 は本文を読まずに解決する", async () => {
    // 204 で json() を呼ぶと落ちるため、呼ばれていないことを保証する。
    const response = new Response(null, { status: 204 });
    const jsonSpy = vi.spyOn(response, "json");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response));

    await expect(apiDelete("/api/worlds/w1", token)).resolves.toBeUndefined();
    expect(jsonSpy).not.toHaveBeenCalled();
  });

  it("POST の problem+json は ApiError に写す", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error_code: "world-name-taken", status: 409 }), {
          status: 409,
          headers: { "Content-Type": "application/problem+json" },
        }),
      ),
    );

    await expect(apiPost("/api/worlds", token, { name: "重複" })).rejects.toMatchObject({
      status: 409,
      problem: { error_code: "world-name-taken" },
    });
  });
});

describe("errorMessage", () => {
  it("ApiError は problem の文言、それ以外は既定文言に写す", () => {
    expect(errorMessage(new ApiError(409, { error_code: "world-name-taken" }), "既定")).toBe(
      "同じ名前の世界が既にあります。",
    );
    expect(errorMessage(new Error("network"), "既定")).toBe("既定");
  });
});
