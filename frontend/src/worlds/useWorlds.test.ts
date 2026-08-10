import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";

// getToken はモジュールスコープの安定した参照にする。実物の AuthContext（useMemo）と同じく、
// レンダーのたびに新しい関数を返さない — そうしないと reload の依存配列（[getToken]）が
// 呼び出しごとに変わったと誤検知し、useEffect が再フェッチを連鎖させてしまう。
const getToken = async () => "t";
vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ getToken }),
}));

vi.mock("../api/worlds", () => ({
  listWorlds: vi.fn(),
  createWorld: vi.fn(),
  renameWorld: vi.fn(),
  deleteWorld: vi.fn(),
}));

import { createWorld, deleteWorld, listWorlds, renameWorld } from "../api/worlds";
import { useWorlds } from "./useWorlds";

const listMock = vi.mocked(listWorlds);
const createMock = vi.mocked(createWorld);
const deleteMock = vi.mocked(deleteWorld);
const renameMock = vi.mocked(renameWorld);

describe("useWorlds", () => {
  it("マウント時に一覧を読み込む", async () => {
    listMock.mockResolvedValue([{ id: "w1", name: "はじまりの世界" }]);

    const { result } = renderHook(() => useWorlds());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });
    expect(result.current.worlds).toEqual([{ id: "w1", name: "はじまりの世界" }]);
    expect(result.current.error).toBeNull();
  });

  it("作成が成功したら一覧を引き直す", async () => {
    listMock.mockResolvedValueOnce([]).mockResolvedValueOnce([{ id: "w2", name: "2周目" }]);
    createMock.mockResolvedValue({ id: "w2", name: "2周目" });

    const { result } = renderHook(() => useWorlds());
    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    let created = false;
    await act(async () => {
      created = await result.current.create("2周目");
    });

    expect(created).toBe(true);
    expect(createMock).toHaveBeenCalledWith(expect.any(Function), "2周目");
    expect(result.current.worlds).toEqual([{ id: "w2", name: "2周目" }]);
  });

  it("作成が 409 なら error に文言が入り、一覧は引き直さない", async () => {
    listMock.mockResolvedValue([]);
    createMock.mockRejectedValue(new ApiError(409, { error_code: "world-name-taken" }));

    const { result } = renderHook(() => useWorlds());
    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });
    listMock.mockClear();

    let created = true;
    await act(async () => {
      created = await result.current.create("重複");
    });

    expect(created).toBe(false);
    expect(result.current.error).toBe("同じ名前の世界が既にあります。");
    expect(listMock).not.toHaveBeenCalled();
  });

  it("改名が成功したら一覧を引き直す", async () => {
    listMock
      .mockResolvedValueOnce([{ id: "w1", name: "はじまりの世界" }])
      .mockResolvedValueOnce([{ id: "w1", name: "改名後" }]);
    renameMock.mockResolvedValue({ id: "w1", name: "改名後" });

    const { result } = renderHook(() => useWorlds());
    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    let renamed = false;
    await act(async () => {
      renamed = await result.current.rename("w1", "改名後");
    });

    expect(renamed).toBe(true);
    expect(renameMock).toHaveBeenCalledWith(expect.any(Function), "w1", "改名後");
    expect(result.current.worlds).toEqual([{ id: "w1", name: "改名後" }]);
  });

  it("削除が成功したら一覧を引き直す", async () => {
    listMock
      .mockResolvedValueOnce([{ id: "w1", name: "はじまりの世界" }])
      .mockResolvedValueOnce([]);
    deleteMock.mockResolvedValue(undefined);

    const { result } = renderHook(() => useWorlds());
    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });

    await act(async () => {
      await result.current.remove("w1");
    });

    expect(result.current.worlds).toEqual([]);
  });

  it("一覧の取得に失敗したら error に文言が入る", async () => {
    listMock.mockRejectedValue(new ApiError(500));

    const { result } = renderHook(() => useWorlds());

    await waitFor(() => {
      expect(result.current.loading).toBe(false);
    });
    expect(result.current.error).toBe("世界の一覧を取得できませんでした。");
  });
});
