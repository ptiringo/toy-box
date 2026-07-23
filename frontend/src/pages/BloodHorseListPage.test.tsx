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
  it("成功時はテーブルを表示し、wire enum を日本語ラベルで描き、エラーは出ずステータス200が可視化される", async () => {
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
    // wire 値（FEMALE / BAY / THOROUGHBRED）ではなく日本語ラベルで描かれる。
    expect(screen.getByText("牝")).toBeInTheDocument();
    expect(screen.getByText("鹿毛")).toBeInTheDocument();
    expect(screen.getByText("サラブレッド")).toBeInTheDocument();
    // ステータス可視化（バッジに直近レスポンスが出る）
    expect(screen.getByTitle("直近レスポンス")).toHaveTextContent("200");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    // name が null の行は「未命名」で描画される。
    expect(screen.getByText("未命名")).toBeInTheDocument();
  });

  it("失敗時はエラー banner が出て、ステータスが可視化される", async () => {
    apiGetMock.mockRejectedValue(new ApiError(401));

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(screen.getByTitle("直近レスポンス")).toHaveTextContent("401");
  });
});
