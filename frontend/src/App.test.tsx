import { render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import type { UseWorlds } from "./worlds/useWorlds";

// firebase の初期化を避けるため、認証まわりはすべてモックする。
// useAuth の戻り値はモジュールスコープの安定した参照にする。実物の AuthContext（useMemo）と同じく、
// レンダーのたびに新しい関数を返さない — そうしないと getToken を依存配列に持つ useEffect が
// 毎レンダー走り直し、本番では起きない無限再フェッチをテストだけが踏む（#747）。
const auth = vi.hoisted(() => ({
  user: { uid: "u1" },
  loading: false,
  getToken: async () => "t",
  signOutUser: async () => {},
  signIn: async () => {},
}));
vi.mock("./auth/AuthContext", () => ({
  AuthProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
  useAuth: () => auth,
}));

vi.mock("./api/me", () => ({ provisionMe: vi.fn().mockResolvedValue({ account_id: "a1" }) }));

// 戻り値を UseWorlds で型付けし、フックの契約が増えたらここが型で落ちるようにする（#747）。
vi.mock("./worlds/useWorlds", () => ({
  useWorlds: (): UseWorlds => ({
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
