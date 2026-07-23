import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { BloodHorseListPage } from "./pages/BloodHorseListPage";
import { LoginPage } from "./pages/LoginPage";
import { RequireAuth } from "./pages/RequireAuth";

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<RequireAuth />}>
            <Route path="/bloodHorses" element={<BloodHorseListPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/bloodHorses" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
