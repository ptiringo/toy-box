import { useEffect, useState } from "react";
import { ApiError, apiGet } from "../api/client";
import { useAuth } from "../auth/AuthContext";

// バックの BloodHorseSummaryResponse（snake_case）に対応する行の型。
type BloodHorseSummary = {
  id: string;
  registration_number: string;
  sex: string;
  coat_color: string;
  breed_type: string;
  date_of_birth: string;
  breeder: string;
  name: string | null;
};

export function BloodHorseListPage() {
  const { getToken, signOutUser } = useAuth();
  const [rows, setRows] = useState<BloodHorseSummary[]>([]);
  const [status, setStatus] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const data = await apiGet<BloodHorseSummary[]>("/api/bloodHorses", getToken);
        if (active) {
          setRows(data);
          setStatus(200);
          // 再フェッチが成功したら、前回失敗時の error を引きずらないようにクリアする。
          setError(null);
        }
      } catch (e) {
        if (!active) return;
        if (e instanceof ApiError) {
          setStatus(e.status);
          setError(e.message);
        } else {
          setError("不明なエラーが発生しました。");
        }
      }
    })();
    return () => {
      active = false;
    };
  }, [getToken]);

  return (
    <main>
      <header>
        <h1>軽種馬一覧</h1>
        {/* 認証を目で見るための小さなステータス可視化 */}
        <small>直近レスポンス: {status ?? "-"}</small>
        <button type="button" onClick={() => signOutUser()}>
          ログアウト
        </button>
      </header>
      {error && <p role="alert">{error}</p>}
      <table>
        <thead>
          <tr>
            <th>登録番号</th>
            <th>馬名</th>
            <th>性</th>
            <th>毛色</th>
            <th>品種</th>
            <th>生年月日</th>
            <th>生産者</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((h) => (
            <tr key={h.id}>
              <td>{h.registration_number}</td>
              <td>{h.name ?? "（未命名）"}</td>
              <td>{h.sex}</td>
              <td>{h.coat_color}</td>
              <td>{h.breed_type}</td>
              <td>{h.date_of_birth}</td>
              <td>{h.breeder}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </main>
  );
}
