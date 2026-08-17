import { render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

// firebase の初期化を避けるため、認証まわりはすべてモックする。
vi.mock("./auth/AuthContext", () => ({
  AuthProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
  useAuth: () => ({
    user: { uid: "u1" },
    loading: false,
    getToken: async () => "t",
    signOutUser: vi.fn(),
    signIn: vi.fn(),
  }),
}));

vi.mock("./api/me", () => ({ provisionMe: vi.fn().mockResolvedValue({ account_id: "a1" }) }));

vi.mock("./worlds/useWorlds", () => ({
  useWorlds: () => ({
    worlds: [{ id: "w1", name: "はじまりの世界" }],
    loading: false,
    busy: false,
    error: null,
    create: vi.fn(),
    rename: vi.fn(),
    remove: vi.fn(),
  }),
}));

import { App } from "./App";

describe("App のルーティング", () => {
  it("未知のパスは /worlds へ送り、世界一覧を描く", async () => {
    window.history.pushState({}, "", "/unknown");

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText("世界一覧")).toBeInTheDocument();
    });
  });
});
