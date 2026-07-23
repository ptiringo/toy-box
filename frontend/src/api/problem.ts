// RFC 9457 problem+json のうち本フロントが解釈する項目。拡張キー error_code は snake_case（バック規約）。
export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  error_code?: string;
};

export function isProblemContentType(contentType: string | null): boolean {
  return contentType?.includes("application/problem+json") ?? false;
}
