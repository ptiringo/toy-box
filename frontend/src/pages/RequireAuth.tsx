import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

// 認証状態が確定するまで待ち、未ログインは /login へ送る。
export function RequireAuth() {
  const { user, loading } = useAuth();
  if (loading) {
    return <p>認証状態を確認中…</p>;
  }
  return user ? <Outlet /> : <Navigate to="/login" replace />;
}
