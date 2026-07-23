import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

// 認証状態が確定するまで待ち、未ログインは /login へ送る。
export function RequireAuth() {
  const { user, loading } = useAuth();
  if (loading) {
    return <div className="loading">認証状態を確認中…</div>;
  }
  return user ? <Outlet /> : <Navigate to="/login" replace />;
}
