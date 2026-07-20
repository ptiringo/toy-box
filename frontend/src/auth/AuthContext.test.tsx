import { act, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

// firebase/auth をモック（onAuthStateChanged で user を差し込む）
const listeners: Array<(u: unknown) => void> = [];
const signInMock = vi.fn();
vi.mock("firebase/auth", () => ({
  onAuthStateChanged: (_auth: unknown, cb: (u: unknown) => void) => {
    listeners.push(cb);
    return () => {};
  },
  signInWithEmailAndPassword: (_auth: unknown, email: string, password: string) =>
    signInMock(email, password),
  signOut: vi.fn(),
}));
vi.mock("./firebase", () => ({ auth: {} }));

import { AuthProvider, useAuth } from "./AuthContext";

function Probe() {
  const { user, loading } = useAuth();
  return <div>{loading ? "loading" : user ? "signed-in" : "signed-out"}</div>;
}

beforeEach(() => {
  listeners.length = 0;
  signInMock.mockReset();
});

describe("AuthContext", () => {
  it("初期はloading、認証状態が届くとsigned-out/inになる", async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );
    expect(screen.getByText("loading")).toBeInTheDocument();

    // 未ログイン（null）が届く
    act(() => {
      for (const cb of listeners) cb(null);
    });
    await waitFor(() => expect(screen.getByText("signed-out")).toBeInTheDocument());

    // ログイン済み user が届く
    act(() => {
      for (const cb of listeners) cb({ uid: "u1", getIdToken: async () => "t" });
    });
    await waitFor(() => expect(screen.getByText("signed-in")).toBeInTheDocument());
  });
});
