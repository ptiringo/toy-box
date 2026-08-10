import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ signOutUser: vi.fn() }),
}));

const useWorldsMock = vi.fn();
vi.mock("../worlds/useWorlds", () => ({ useWorlds: () => useWorldsMock() }));

import { WorldsPage } from "./WorldsPage";

afterEach(() => {
  vi.restoreAllMocks();
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/worlds"]}>
      <Routes>
        <Route path="/worlds" element={<WorldsPage />} />
        <Route path="/worlds/:worldId/bloodHorses" element={<div>馬一覧</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

// テストコードも tsc の対象なので、ヘルパの戻り値に正確な型を付ける（Record<string, unknown> で
// スプレッドすると create 等が unknown に落ちて npm run build が失敗する）。
type WorldsStub = {
  worlds: { id: string; name: string }[];
  loading: boolean;
  error: string | null;
  create: ReturnType<typeof vi.fn>;
  rename: ReturnType<typeof vi.fn>;
  remove: ReturnType<typeof vi.fn>;
};

function stubWorlds(overrides: Partial<WorldsStub> = {}): WorldsStub {
  const value: WorldsStub = {
    worlds: [{ id: "w1", name: "はじまりの世界" }],
    loading: false,
    error: null,
    create: vi.fn().mockResolvedValue(true),
    rename: vi.fn().mockResolvedValue(true),
    remove: vi.fn().mockResolvedValue(true),
    ...overrides,
  };
  useWorldsMock.mockReturnValue(value);
  return value;
}

describe("WorldsPage", () => {
  it("世界を一覧し、選ぶと馬一覧へ遷移する", async () => {
    stubWorlds();

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "はじまりの世界" }));
    expect(screen.getByText("馬一覧")).toBeInTheDocument();
  });

  it("0 件なら空状態を出す", () => {
    stubWorlds({ worlds: [] });

    renderPage();

    expect(screen.getByText("世界がありません")).toBeInTheDocument();
  });

  it("名前を入力して作成できる", async () => {
    const value = stubWorlds();

    renderPage();

    await userEvent.type(screen.getByLabelText("新しい世界の名前"), "2周目");
    await userEvent.click(screen.getByRole("button", { name: "作る" }));

    expect(value.create).toHaveBeenCalledWith("2周目");
  });

  it("改名はインライン編集で行う", async () => {
    const value = stubWorlds();

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "改名" }));
    const input = screen.getByLabelText("変更後の名前");
    await userEvent.clear(input);
    await userEvent.type(input, "改名後");
    await userEvent.click(screen.getByRole("button", { name: "保存" }));

    expect(value.rename).toHaveBeenCalledWith("w1", "改名後");
  });

  it("削除は確認してから実行し、取り消したら実行しない", async () => {
    const value = stubWorlds();
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false);

    renderPage();

    await userEvent.click(screen.getByRole("button", { name: "削除" }));
    expect(value.remove).not.toHaveBeenCalled();

    confirmSpy.mockReturnValue(true);
    await userEvent.click(screen.getByRole("button", { name: "削除" }));
    expect(value.remove).toHaveBeenCalledWith("w1");
  });

  it("エラーがあれば alert で表示する", async () => {
    stubWorlds({ error: "同じ名前の世界が既にあります。" });

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("同じ名前の世界が既にあります。");
    });
  });
});
