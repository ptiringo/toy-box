import { initializeApp } from "firebase/app";
import { connectAuthEmulator, getAuth } from "firebase/auth";

// 実 Identity Platform テナントの Web config。projectId はバックの GCP_PROJECT_ID と一致させる。
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
};

export const firebaseApp = initializeApp(firebaseConfig);
export const auth = getAuth(firebaseApp);

// ブラウザ E2E（#725）のときだけ Auth Emulator へ向ける。env が無い通常のビルドでは何もしないので、
// 本番の挙動は変わらない。Emulator が出す ID トークンは未署名で、バック側は bootTestRun 経由の
// テスト専用 JwtDecoder がこれを受理する。
const emulatorHost = import.meta.env.VITE_FIREBASE_AUTH_EMULATOR_HOST;
if (emulatorHost) {
  connectAuthEmulator(auth, `http://${emulatorHost}`, { disableWarnings: true });
}
