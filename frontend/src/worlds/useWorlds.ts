import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../api/client";
import { type World, createWorld, deleteWorld, listWorlds, renameWorld } from "../api/worlds";
import { useAuth } from "../auth/AuthContext";

type UseWorlds = {
  worlds: World[];
  loading: boolean;
  error: string | null;
  create: (name: string) => Promise<boolean>;
  rename: (worldId: string, name: string) => Promise<boolean>;
  remove: (worldId: string) => Promise<boolean>;
};

/**
 * 世界一覧の取得と変更操作。
 *
 * 変更が成功したら一覧を引き直して画面を API に合わせる（楽観更新はしない。世界の数は少なく、
 * 取り直しのコストより整合の確かさを取る）。
 *
 * 変更操作は例外を投げず、失敗を error の文言と戻り値 false で表す。呼び出し側は成否だけを見て
 * 入力欄や編集モードを閉じるか決める（失敗したのに入力を捨てるとユーザーが打ち直しになる）。
 */
export function useWorlds(): UseWorlds {
  const { getToken } = useAuth();
  const [worlds, setWorlds] = useState<World[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      setWorlds(await listWorlds(getToken));
      setError(null);
    } catch (e) {
      setError(errorMessage(e, "世界の一覧を取得できませんでした。"));
    } finally {
      setLoading(false);
    }
  }, [getToken]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const mutate = useCallback(
    async (operation: () => Promise<unknown>, fallback: string): Promise<boolean> => {
      try {
        await operation();
      } catch (e) {
        setError(errorMessage(e, fallback));
        return false;
      }
      setError(null);
      await reload();
      return true;
    },
    [reload],
  );

  const create = useCallback(
    (name: string) => mutate(() => createWorld(getToken, name), "世界を作成できませんでした。"),
    [mutate, getToken],
  );

  const rename = useCallback(
    (worldId: string, name: string) =>
      mutate(() => renameWorld(getToken, worldId, name), "世界の名前を変更できませんでした。"),
    [mutate, getToken],
  );

  const remove = useCallback(
    (worldId: string) =>
      mutate(() => deleteWorld(getToken, worldId), "世界を削除できませんでした。"),
    [mutate, getToken],
  );

  return { worlds, loading, error, create, rename, remove };
}
