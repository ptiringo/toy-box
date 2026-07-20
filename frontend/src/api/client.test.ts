import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiGet } from "./client";

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

    await expect(apiGet("/api/bloodHorses", token)).rejects.toBeInstanceOf(ApiError);
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
