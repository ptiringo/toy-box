import { useCallback, useEffect, useRef, useState } from "react";
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

  // getToken は ref 越しに参照する。AuthContext の実装は認証状態が変わらない限り安定した参照を返すが、
  // それを useCallback/useEffect の依存に直接入れると「毎回新しい関数を返す」呼び出し元（テストダブル等）
  // まで暗黙に安定を仮定してしまい、マウント時 useEffect が再フェッチを連鎖させかねない。ref にすれば
  // reload/mutate 系の参照が安定し、常に最新の getToken を使いつつ再フェッチはマウント時の 1 回に保てる。
  const getTokenRef = useRef(getToken);
  useEffect(() => {
    getTokenRef.current = getToken;
  }, [getToken]);

  const reload = useCallback(async () => {
    try {
      setWorlds(await listWorlds(getTokenRef.current));
      setError(null);
    } catch (e) {
      setError(errorMessage(e, "世界の一覧を取得できませんでした。"));
    } finally {
      setLoading(false);
    }
  }, []);

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
    (name: string) =>
      mutate(() => createWorld(getTokenRef.current, name), "世界を作成できませんでした。"),
    [mutate],
  );

  const rename = useCallback(
    (worldId: string, name: string) =>
      mutate(
        () => renameWorld(getTokenRef.current, worldId, name),
        "世界の名前を変更できませんでした。",
      ),
    [mutate],
  );

  const remove = useCallback(
    (worldId: string) =>
      mutate(() => deleteWorld(getTokenRef.current, worldId), "世界を削除できませんでした。"),
    [mutate],
  );

  return { worlds, loading, error, create, rename, remove };
}
