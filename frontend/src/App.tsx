import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { BloodHorseListPage } from "./pages/BloodHorseListPage";
import { LoginPage } from "./pages/LoginPage";
import { RequireAuth } from "./pages/RequireAuth";
import { RequireProvisioned } from "./pages/RequireProvisioned";
import { WorldsPage } from "./pages/WorldsPage";

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          {/* 認証 → 初回セットアップの順に通す。:provision を通さないと他の API は 403 になる。 */}
          <Route element={<RequireAuth />}>
            <Route element={<RequireProvisioned />}>
              <Route path="/worlds" element={<WorldsPage />} />
              <Route path="/worlds/:worldId/bloodHorses" element={<BloodHorseListPage />} />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/worlds" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
