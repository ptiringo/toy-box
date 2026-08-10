import { type FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await signIn(email, password);
      navigate("/worlds", { replace: true });
    } catch {
      setError("サインインできませんでした。メールアドレスとパスワードを確認してください。");
      setSubmitting(false);
    }
  };

  return (
    <div className="cover">
      <aside className="cover__plate">
        <p className="cover__eyebrow">JAIRS · Studbook</p>
        <div>
          <h1 className="cover__title">
            軽種馬
            <br />
            登録原簿
          </h1>
          <p className="cover__lede">
            血統登録された軽種馬の記録を閲覧する台帳です。登録担当のアカウントでサインインしてください。
          </p>
        </div>
        <p className="cover__reg">REGISTRY ACCESS · ptiringo-toy-box</p>
      </aside>

      <main className="cover__form">
        <form className="sheet" onSubmit={onSubmit}>
          <h2 className="sheet__heading">記帳にサインイン</h2>
          <p className="sheet__note">Identity Platform のアカウントで認証します。</p>

          <label className="field">
            <span>メールアドレス</span>
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>
          <label className="field">
            <span>パスワード</span>
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>

          <button className="btn" type="submit" disabled={submitting}>
            {submitting ? "確認中…" : "サインイン"}
          </button>

          {error && (
            <p className="alert" role="alert">
              {error}
            </p>
          )}
        </form>
      </main>
    </div>
  );
}
