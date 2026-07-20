import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({
    getToken: async () => "t",
    signOutUser: vi.fn(),
  }),
}));

vi.mock("../api/client", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api/client")>();
  return { ...actual, apiGet: vi.fn() };
});

import { apiGet } from "../api/client";
import { BloodHorseListPage } from "./BloodHorseListPage";

const apiGetMock = vi.mocked(apiGet);

function renderPage() {
  return render(
    <MemoryRouter>
      <BloodHorseListPage />
    </MemoryRouter>,
  );
}

describe("BloodHorseListPage", () => {
  it("フェッチ成功時はテーブルを表示し、エラー banner は出ずステータス200が可視化される", async () => {
    apiGetMock.mockResolvedValue([
      {
        id: "h1",
        registration_number: "0000000001",
        sex: "牡",
        coat_color: "鹿毛",
        breed_type: "サラブレッド",
        date_of_birth: "2020-01-01",
        breeder: "テスト牧場",
        name: null,
      },
    ]);

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("0000000001")).toBeInTheDocument();
    });
    expect(screen.getByText(/直近レスポンス: 200/)).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    // name が null の行は「（未命名）」で描画される。
    expect(screen.getByText("（未命名）")).toBeInTheDocument();
  });

  it("フェッチ失敗時はエラー banner が出て、ステータスが可視化される", async () => {
    apiGetMock.mockRejectedValue(new ApiError(401));

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(screen.getByText(/直近レスポンス: 401/)).toBeInTheDocument();
  });
});
