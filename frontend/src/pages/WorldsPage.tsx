import { type FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useWorlds } from "../worlds/useWorlds";

/**
 * 世界（セーブデータ）の一覧ハブ。
 *
 * 選択中の世界は URL（/worlds/:worldId/...）が唯一の出所なので、この画面は選択状態を持たない。
 */
export function WorldsPage() {
  const { signOutUser } = useAuth();
  const { worlds, loading, busy, error, create, rename, remove } = useWorlds();
  const navigate = useNavigate();
  const [newName, setNewName] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState("");

  // 失敗したときは入力・編集状態を保つ（打ち直しにさせない）。エラー文言は useWorlds が error に載せる。
  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    if (await create(newName)) {
      setNewName("");
    }
  };

  const onSaveRename = async (e: FormEvent, worldId: string) => {
    e.preventDefault();
    if (await rename(worldId, editingName)) {
      setEditingId(null);
    }
  };

  const onDelete = async (worldId: string, name: string) => {
    if (!window.confirm(`「${name}」を削除します。よろしいですか？`)) {
      return;
    }
    await remove(worldId);
  };

  return (
    <div className="ledger">
      <header className="ledger__head">
        <h1 className="ledger__title">世界一覧</h1>
        <span className="ledger__count">{worlds.length} 個</span>
        <div className="ledger__spacer" />
        <button className="btn-ghost" type="button" onClick={() => signOutUser()}>
          ログアウト
        </button>
      </header>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {loading ? (
        <div className="loading">読み込み中…</div>
      ) : worlds.length === 0 && !error ? (
        <section className="empty">
          <p className="empty__mark">世界がありません</p>
          <p className="empty__lede">最初の世界を作ると、その中で軽種馬の登録を始められます。</p>
        </section>
      ) : (
        <ul className="worlds">
          {worlds.map((world) => (
            <li className="worlds__item" key={world.id}>
              {editingId === world.id ? (
                // 作成側と同じく form + required にする（required の検証は submit のときだけ走るので、
                // ボタンの onClick で保存すると空名が素通りする）。
                <form className="worlds__edit" onSubmit={(e) => void onSaveRename(e, world.id)}>
                  {/* 作成フォームの「新しい世界の名前」と紛れないラベルにする。 */}
                  <label className="field worlds__field">
                    <span>変更後の名前</span>
                    <input
                      value={editingName}
                      onChange={(e) => setEditingName(e.target.value)}
                      required
                      // biome-ignore lint/a11y/noAutofocus: 改名ボタンから開いた入力に即入力できるようにする
                      autoFocus
                    />
                  </label>
                  <button className="btn" type="submit" disabled={busy}>
                    保存
                  </button>
                  <button className="btn-ghost" type="button" onClick={() => setEditingId(null)}>
                    やめる
                  </button>
                </form>
              ) : (
                <>
                  <button
                    className="worlds__name"
                    type="button"
                    onClick={() => navigate(`/worlds/${world.id}/bloodHorses`)}
                  >
                    {world.name}
                  </button>
                  <button
                    className="btn-ghost"
                    type="button"
                    onClick={() => {
                      setEditingId(world.id);
                      setEditingName(world.name);
                    }}
                  >
                    改名
                  </button>
                  <button
                    className="btn-ghost"
                    type="button"
                    onClick={() => void onDelete(world.id, world.name)}
                    disabled={busy}
                  >
                    削除
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}

      <form className="sheet worlds__new" onSubmit={onCreate}>
        <label className="field">
          <span>新しい世界の名前</span>
          <input value={newName} onChange={(e) => setNewName(e.target.value)} required />
        </label>
        {/* 変更中は文言を変えず disabled だけにする（busy はどの操作でも立つので「作成中…」は嘘になりうる）。 */}
        <button className="btn" type="submit" disabled={busy}>
          作る
        </button>
      </form>
    </div>
  );
}
