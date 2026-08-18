import { describe, expect, it, vi } from "vitest";
import { createWorld, deleteWorld, listWorlds, renameWorld } from "./worlds";

const token = async () => "id-token-123";

function stubFetch(status: number, body: unknown): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: body === undefined ? {} : { "Content-Type": "application/json" },
    }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("世界 API", () => {
  it("一覧は GET /api/worlds を叩く", async () => {
    const fetchMock = stubFetch(200, [{ id: "w1", name: "はじまりの世界" }]);

    await expect(listWorlds(token)).resolves.toEqual([{ id: "w1", name: "はじまりの世界" }]);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/worlds");
    expect(fetchMock.mock.calls[0][1].method).toBe("GET");
  });

  it("作成は POST /api/worlds に name を送る", async () => {
    const fetchMock = stubFetch(201, { id: "w2", name: "2周目" });

    await expect(createWorld(token, "2周目")).resolves.toEqual({ id: "w2", name: "2周目" });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/worlds");
    expect(fetchMock.mock.calls[0][1].body).toBe(JSON.stringify({ name: "2周目" }));
  });

  it("改名は PATCH /api/worlds/{id} に name を送る", async () => {
    const fetchMock = stubFetch(200, { id: "w1", name: "改名後" });

    await expect(renameWorld(token, "w1", "改名後")).resolves.toEqual({
      id: "w1",
      name: "改名後",
    });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/worlds/w1");
    expect(fetchMock.mock.calls[0][1].method).toBe("PATCH");
  });

  it("削除は DELETE /api/worlds/{id} を叩く", async () => {
    const fetchMock = stubFetch(204, undefined);

    await expect(deleteWorld(token, "w1")).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0][0]).toBe("/api/worlds/w1");
    expect(fetchMock.mock.calls[0][1].method).toBe("DELETE");
  });
});
