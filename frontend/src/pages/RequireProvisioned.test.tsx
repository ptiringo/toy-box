import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/client";

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ user: { uid: "u1" }, getToken: async () => "t" }),
}));

vi.mock("../api/me", () => ({ provisionMe: vi.fn() }));

import { provisionMe } from "../api/me";
import { RequireProvisioned } from "./RequireProvisioned";

const provisionMock = vi.mocked(provisionMe);

function renderGuard() {
  return render(
    <MemoryRouter initialEntries={["/worlds"]}>
      <Routes>
        <Route element={<RequireProvisioned />}>
          <Route path="/worlds" element={<div>protected</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("RequireProvisioned", () => {
  it("セットアップ完了までは子ルートを描かない", () => {
    // 解決しない Promise を返して pending 状態に留める。
    provisionMock.mockReturnValue(new Promise(() => {}));

    renderGuard();

    expect(screen.getByText("準備中…")).toBeInTheDocument();
    expect(screen.queryByText("protected")).not.toBeInTheDocument();
  });

  it("成功したら子ルートを描く", async () => {
    provisionMock.mockResolvedValue({ account_id: "a1" });

    renderGuard();

    await waitFor(() => {
      expect(screen.getByText("protected")).toBeInTheDocument();
    });
  });

  it("失敗したらエラーを出し、再試行でもう一度呼ぶ", async () => {
    provisionMock.mockRejectedValueOnce(
      new ApiError(403, { error_code: "account-not-provisioned" }),
    );

    renderGuard();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "セットアップが完了していません。再試行してください。",
      );
    });

    provisionMock.mockResolvedValue({ account_id: "a1" });
    await userEvent.click(screen.getByRole("button", { name: "再試行" }));

    await waitFor(() => {
      expect(screen.getByText("protected")).toBeInTheDocument();
    });
  });
});
