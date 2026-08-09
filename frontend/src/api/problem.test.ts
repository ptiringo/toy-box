import { describe, expect, it } from "vitest";
import { problemMessage } from "./problem";

describe("problemMessage", () => {
  it("既知の error_code は画面用の日本語文言に写す", () => {
    expect(problemMessage({ error_code: "world-name-taken" }, "既定")).toBe(
      "同じ名前の世界が既にあります。",
    );
    expect(problemMessage({ error_code: "world-update-conflict" }, "既定")).toBe(
      "同じ名前の世界があるか、別の更新と競合しました。やり直してください。",
    );
  });

  it("未知の error_code は detail にフォールバックする", () => {
    expect(problemMessage({ error_code: "unknown-code", detail: "サーバの説明" }, "既定")).toBe(
      "サーバの説明",
    );
  });

  it("detail が無ければ title、それも無ければ既定文言にフォールバックする", () => {
    expect(problemMessage({ title: "Conflict" }, "既定")).toBe("Conflict");
    expect(problemMessage(undefined, "既定")).toBe("既定");
  });
});
