import { isProblemContentType, type ProblemDetail, problemMessage } from "./problem";

/** ID トークンを取り出す関数。未ログインなら null を返す（AuthContext.getToken）。 */
export type GetToken = () => Promise<string | null>;

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
 * 保護 API を叩く。ID トークンを Authorization: Bearer に載せ、problem+json を ApiError へ写す。
 * トークンが取れない（未ログイン）ときは fetch せず 401 相当の ApiError を投げる。
 */
async function request(
  method: string,
  path: string,
  getToken: GetToken,
  body?: unknown,
): Promise<unknown> {
  const token = await getToken();
  if (token === null) {
    throw new ApiError(401);
  }

  const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!response.ok) {
    const problem = isProblemContentType(response.headers.get("Content-Type"))
      ? ((await response.json()) as ProblemDetail)
      : undefined;
    throw new ApiError(response.status, problem);
  }

  // 204 No Content（DELETE）は本文が無い。json() を呼ぶと落ちる。
  if (response.status === 204) {
    return undefined;
  }
  return await response.json();
}

export async function apiGet<T>(path: string, getToken: GetToken): Promise<T> {
  return (await request("GET", path, getToken)) as T;
}

export async function apiPost<T>(path: string, getToken: GetToken, body?: unknown): Promise<T> {
  return (await request("POST", path, getToken, body)) as T;
}

export async function apiPatch<T>(path: string, getToken: GetToken, body: unknown): Promise<T> {
  return (await request("PATCH", path, getToken, body)) as T;
}

export async function apiDelete(path: string, getToken: GetToken): Promise<void> {
  await request("DELETE", path, getToken);
}

/** 例外を画面表示用の文言へ写す。ApiError 以外（ネットワーク断等）は既定文言にする。 */
export function errorMessage(e: unknown, fallback: string): string {
  return e instanceof ApiError ? problemMessage(e.problem, fallback) : fallback;
}
