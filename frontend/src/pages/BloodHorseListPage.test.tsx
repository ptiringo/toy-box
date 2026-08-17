import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({
    getToken: async () => "t",
    signOutUser: vi.fn(),
  }),
}));

const useWorldsMock = vi.fn();
vi.mock("../worlds/useWorlds", () => ({ useWorlds: () => useWorldsMock() }));

vi.mock("../api/client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api/client")>();
  return { ...actual, apiGet: vi.fn() };
});

import { apiGet } from "../api/client";
import { BloodHorseListPage } from "./BloodHorseListPage";

const apiGetMock = vi.mocked(apiGet);

// 呼び出し履歴はテストをまたいで積み上がるので、各テストの前に落とす（実装＝mockResolvedValue は残す）。
beforeEach(() => {
  apiGetMock.mockClear();
});

function renderAt(path: string) {
  useWorldsMock.mockReturnValue({
    worlds: [{ id: "w1", name: "はじまりの世界" }],
    loading: false,
    busy: false,
    error: null,
    create: vi.fn(),
    rename: vi.fn(),
    remove: vi.fn(),
  });
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/worlds/:worldId/bloodHorses" element={<BloodHorseListPage />} />
        {/* ルート定義から :worldId が外れた場合を再現するための経路（App.tsx には無い）。 */}
        <Route path="/bloodHorses" element={<BloodHorseListPage />} />
        <Route path="/worlds" element={<div>世界一覧ページ</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function renderPage() {
  return renderAt("/worlds/w1/bloodHorses");
}

describe("BloodHorseListPage", () => {
  it("世界スコープのパスを叩き、世界名とテーブルを描く", async () => {
    apiGetMock.mockResolvedValue([
      {
        id: "h1",
        registration_number: "0000000001",
        sex: "FEMALE",
        coat_color: "BAY",
        breed_type: "THOROUGHBRED",
        date_of_birth: "2020-01-01",
        breeder: "テスト牧場",
        name: null,
      },
    ]);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("0000000001")).toBeInTheDocument();
    });
    expect(apiGetMock).toHaveBeenCalledWith("/api/worlds/w1/bloodHorses", expect.any(Function));
    // どの世界を見ているかがヘッダに出る。
    expect(screen.getByText("はじまりの世界")).toBeInTheDocument();
    // wire 値ではなく日本語ラベルで描かれる。
    expect(screen.getByText("牝")).toBeInTheDocument();
    expect(screen.getByText("鹿毛")).toBeInTheDocument();
    expect(screen.getByText("サラブレッド")).toBeInTheDocument();
    expect(screen.getByTitle("直近レスポンス")).toHaveTextContent("200");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("未命名")).toBeInTheDocument();
  });

  it("404 world-not-found なら見つからない旨と世界一覧への導線を出す", async () => {
    apiGetMock.mockRejectedValue(new ApiError(404, { error_code: "world-not-found" }));

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("この世界は見つかりません。");
    });
    expect(screen.getByRole("link", { name: "世界一覧へ" })).toBeInTheDocument();
    expect(screen.getByTitle("直近レスポンス")).toHaveTextContent("404");
  });

  // useParams の worldId は型上 undefined になりうる。ルート定義が変わっても
  // /api/worlds/undefined/... を叩かず世界一覧へ戻す。
  it("worldId が無いパスでは API を叩かず世界一覧へ戻す", () => {
    renderAt("/bloodHorses");

    expect(screen.getByText("世界一覧ページ")).toBeInTheDocument();
    expect(apiGetMock).not.toHaveBeenCalled();
  });

  it("失敗時はエラー banner とステータスを出す", async () => {
    apiGetMock.mockRejectedValue(new ApiError(401));

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(screen.getByTitle("直近レスポンス")).toHaveTextContent("401");
  });
});
