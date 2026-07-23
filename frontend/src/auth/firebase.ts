import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";

// 実 Identity Platform テナントの Web config。projectId はバックの GCP_PROJECT_ID と一致させる。
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
};

export const firebaseApp = initializeApp(firebaseConfig);
export const auth = getAuth(firebaseApp);
