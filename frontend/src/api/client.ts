import { type ProblemDetail, isProblemContentType } from "./problem";

/** API 呼び出しの失敗。status===401 は未認証（ログインへ誘導する）。 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem?: ProblemDetail,
  ) {
    super(problem?.detail ?? problem?.title ?? `API error: ${status}`);
    this.name = "ApiError";
  }
}

/**
 * 保護 API に GET する。ID トークンを Authorization: Bearer に載せ、problem+json を ApiError へ写す。
 * トークンが取れない（未ログイン）ときは fetch せず 401 相当の ApiError を投げる。
 */
export async function apiGet<T>(path: string, getToken: () => Promise<string | null>): Promise<T> {
  const token = await getToken();
  if (token === null) {
    throw new ApiError(401);
  }

  const response = await fetch(path, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    const problem = isProblemContentType(response.headers.get("Content-Type"))
      ? ((await response.json()) as ProblemDetail)
      : undefined;
    throw new ApiError(response.status, problem);
  }

  return (await response.json()) as T;
}
