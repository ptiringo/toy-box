import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError, apiGet, errorMessage } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { breedLabels, coatColors, coatLabels, label, sexLabels } from "../labels";
import { useWorlds } from "../worlds/useWorlds";

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
  const { worldId } = useParams<{ worldId: string }>();
  const { getToken, signOutUser } = useAuth();
  // 世界名は表示専用。取得に失敗しても名前を出さないだけで、アクセス可否の判断はしない
  // （それは API の 404 world-not-found が担う）。
  const { worlds } = useWorlds();
  const worldName = worlds.find((w) => w.id === worldId)?.name ?? null;
  const [rows, setRows] = useState<BloodHorseSummary[]>([]);
  const [status, setStatus] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const data = await apiGet<BloodHorseSummary[]>(
          `/api/worlds/${worldId}/bloodHorses`,
          getToken,
        );
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
        }
        setError(errorMessage(e, "原簿を読み込めませんでした。"));
      }
    })();
    return () => {
      active = false;
    };
  }, [getToken, worldId]);

  return (
    <div className="ledger">
      <header className="ledger__head">
        <h1 className="ledger__title">軽種馬登録原簿</h1>
        {worldName !== null && <span className="ledger__world">{worldName}</span>}
        <span className="ledger__count">{rows.length} 頭</span>
        <div className="ledger__spacer" />
        <Link className="btn-ghost" to="/worlds">
          世界一覧へ
        </Link>
        {/* 認証を目で見るためのステータス可視化（直近レスポンス） */}
        <span className="status" data-ok={status === 200} title="直近レスポンス">
          {status ?? "—"}
        </span>
        <button className="btn-ghost" type="button" onClick={() => signOutUser()}>
          ログアウト
        </button>
      </header>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {rows.length === 0 && !error ? (
        <section className="empty">
          <p className="empty__mark">白紙</p>
          <p className="empty__lede">
            まだ登録された軽種馬がいません。血統登録が行われると、この原簿に記帳されます。
          </p>
        </section>
      ) : (
        <table className="registry">
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
                <td data-label="登録番号">
                  <span className="reg-no">{h.registration_number}</span>
                </td>
                <td data-label="馬名">
                  <span className="horse-name" data-unnamed={h.name === null}>
                    {h.name ?? "未命名"}
                  </span>
                </td>
                <td data-label="性">{label(sexLabels, h.sex)}</td>
                <td data-label="毛色">
                  <span className="coat">
                    <span
                      className="coat__dot"
                      style={{ background: coatColors[h.coat_color] ?? "var(--line)" }}
                      aria-hidden="true"
                    />
                    {label(coatLabels, h.coat_color)}
                  </span>
                </td>
                <td data-label="品種">{label(breedLabels, h.breed_type)}</td>
                <td data-label="生年月日">
                  <span className="dob">{h.date_of_birth}</span>
                </td>
                <td data-label="生産者">{h.breeder}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
