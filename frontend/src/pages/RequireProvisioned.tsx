import { useCallback, useEffect, useState } from "react";
import { Outlet } from "react-router-dom";
import { errorMessage } from "../api/client";
import { provisionMe } from "../api/me";
import { useAuth } from "../auth/AuthContext";

const FALLBACK = "セットアップに失敗しました。時間をおいて再試行してください。";

/**
 * 初回セットアップ（:provision）が通るまで子ルートを描かないガード。
 *
 * 認証ガード（RequireAuth）と分けているのは、RequireAuth を「認証状態を見るだけ」に保つため。
 */
export function RequireProvisioned() {
  const { user, getToken } = useAuth();
  const uid = user?.uid ?? null;
  const [state, setState] = useState<"pending" | "ready" | "error">("pending");
  const [message, setMessage] = useState("");

  const run = useCallback(async () => {
    if (uid === null) {
      return;
    }
    setState("pending");
    try {
      await provisionMe(uid, getToken);
      setState("ready");
    } catch (e) {
      setMessage(errorMessage(e, FALLBACK));
      setState("error");
    }
  }, [uid, getToken]);

  useEffect(() => {
    void run();
  }, [run]);

  if (state === "error") {
    return (
      <div className="loading">
        <p className="alert" role="alert">
          {message}
        </p>
        <button className="btn" type="button" onClick={() => void run()}>
          再試行
        </button>
      </div>
    );
  }

  if (state === "pending") {
    return <div className="loading">準備中…</div>;
  }

  return <Outlet />;
}
