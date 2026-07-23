import { type User, onAuthStateChanged, signInWithEmailAndPassword, signOut } from "firebase/auth";
import { type ReactNode, createContext, useContext, useEffect, useMemo, useState } from "react";
import { auth } from "./firebase";

type AuthValue = {
  user: User | null;
  loading: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signOutUser: () => Promise<void>;
  getToken: () => Promise<string | null>;
};

const AuthContext = createContext<AuthValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // onAuthStateChanged はマウント時と状態変化時に発火する。初回発火で loading を解く。
    return onAuthStateChanged(auth, (u) => {
      setUser(u);
      setLoading(false);
    });
  }, []);

  const value = useMemo<AuthValue>(
    () => ({
      user,
      loading,
      signIn: async (email, password) => {
        await signInWithEmailAndPassword(auth, email, password);
      },
      signOutUser: async () => {
        await signOut(auth);
      },
      // 未ログインは null。ログイン済みは自動更新に任せて現在の ID トークンを返す。
      getToken: async () => (user ? user.getIdToken() : null),
    }),
    [user, loading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (ctx === undefined) {
    throw new Error("useAuth は AuthProvider の内側で使うこと");
  }
  return ctx;
}
