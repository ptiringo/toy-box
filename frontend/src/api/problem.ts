// RFC 9457 problem+json のうち本フロントが解釈する項目。拡張キー error_code / world_id は snake_case（バック規約）。
export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  error_code?: string;
  world_id?: string;
};

export function isProblemContentType(contentType: string | null): boolean {
  return contentType?.includes("application/problem+json") ?? false;
}

// error_code → 画面表示用の文言。出所は controller 配下の各 toProblemDetail()
// （WorldProblem.kt / MeProblem.kt / GlobalExceptionHandler.kt）。
const errorMessages: Record<string, string> = {
  "account-not-provisioned": "セットアップが完了していません。再試行してください。",
  "world-not-found": "この世界は見つかりません。",
  "world-name-blank": "世界の名前を入力してください。",
  "world-name-too-long": "世界の名前は 64 文字以内で入力してください。",
  "world-name-taken": "同じ名前の世界が既にあります。",
  "world-update-conflict": "同じ名前の世界があるか、別の更新と競合しました。やり直してください。",
};

/**
 * problem+json を画面表示用の文言へ写す。
 *
 * 未知の error_code は detail → title → 既定文言の順にフォールバックする（バックが新しいコードを
 * 足しても表示を壊さない）。
 */
export function problemMessage(problem: ProblemDetail | undefined, fallback: string): string {
  const known = problem?.error_code === undefined ? undefined : errorMessages[problem.error_code];
  return known ?? problem?.detail ?? problem?.title ?? fallback;
}
