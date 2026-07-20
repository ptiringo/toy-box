import { type FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await signIn(email, password);
      navigate("/bloodHorses", { replace: true });
    } catch {
      setError("ログインに失敗しました。メールアドレスとパスワードを確認してください。");
    }
  };

  return (
    <main>
      <h1>ログイン</h1>
      <form onSubmit={onSubmit}>
        <label>
          メールアドレス
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          パスワード
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <button type="submit">ログイン</button>
      </form>
      {error && <p role="alert">{error}</p>}
    </main>
  );
}
