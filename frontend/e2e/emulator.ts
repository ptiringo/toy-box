const EMULATOR_HOST = "127.0.0.1:9099";
const API_KEY = "fake-api-key";

export type TestUser = {
  email: string;
  password: string;
};

/**
 * Auth Emulator にテストユーザーを作る。
 *
 * 実行のたびにユニークな email を使う。別 email = 別 sub = 別アカウント = 別世界になるため、
 * DB も Emulator もリセットせずに済み、後始末の仕組みを持たなくてよくなる。
 */
export async function createTestUser(): Promise<TestUser> {
  const unique = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const user: TestUser = { email: `e2e-${unique}@example.com`, password: "e2e-password" };

  const response = await fetch(
    `http://${EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=${API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...user, returnSecureToken: true }),
    },
  );

  if (!response.ok) {
    throw new Error(
      `Auth Emulator へのユーザー作成に失敗した: ${response.status} ${await response.text()}`,
    );
  }
  return user;
}
