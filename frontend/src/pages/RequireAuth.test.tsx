import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

const useAuthMock = vi.fn();
vi.mock("../auth/AuthContext", () => ({ useAuth: () => useAuthMock() }));

import { RequireAuth } from "./RequireAuth";

function renderAt() {
  return render(
    <MemoryRouter initialEntries={["/bloodHorses"]}>
      <Routes>
        <Route element={<RequireAuth />}>
          <Route path="/bloodHorses" element={<div>protected</div>} />
        </Route>
        <Route path="/login" element={<div>login-page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("RequireAuth", () => {
  it("未ログインなら /login へリダイレクトする", () => {
    useAuthMock.mockReturnValue({ user: null, loading: false });
    renderAt();
    expect(screen.getByText("login-page")).toBeInTheDocument();
  });

  it("ログイン済みなら保護画面を表示する", () => {
    useAuthMock.mockReturnValue({ user: { uid: "u1" }, loading: false });
    renderAt();
    expect(screen.getByText("protected")).toBeInTheDocument();
  });
});
