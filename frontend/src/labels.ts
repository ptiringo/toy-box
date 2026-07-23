// バックの wire enum 値（英語定数名）を、表示用の日本語ラベルと毛色スウォッチ色へ写す。
// 出所は controller/horse/BloodHorseWireEnums.kt（SexDto / CoatColorDto / BreedTypeDto）。

export const sexLabels: Record<string, string> = {
  MALE: "牡",
  FEMALE: "牝",
};

export const breedLabels: Record<string, string> = {
  THOROUGHBRED: "サラブレッド",
  ARAB: "アラブ",
  ANGLO_ARAB: "アングロアラブ",
  THOROUGHBRED_TYPE: "サラブレッド系種",
  ARAB_TYPE: "アラブ系種",
};

export const coatLabels: Record<string, string> = {
  CHESTNUT: "栗毛",
  DARK_CHESTNUT: "栃栗毛",
  BAY: "鹿毛",
  DARK_BAY: "黒鹿毛",
  BROWN: "青鹿毛",
  BLACK: "青毛",
  GRAY: "芦毛",
  WHITE: "白毛",
};

// styles.css の --coat-* と対応する実色近似（毛色スウォッチ用）。
export const coatColors: Record<string, string> = {
  CHESTNUT: "#9c5a2c",
  DARK_CHESTNUT: "#6e3d1c",
  BAY: "#6b4423",
  DARK_BAY: "#4a2f1e",
  BROWN: "#38291f",
  BLACK: "#2b2724",
  GRAY: "#ada9a0",
  WHITE: "#ece8de",
};

// 未知の wire 値はそのまま表示する（契約に列挙子が増えても表示を壊さない）。
export function label(map: Record<string, string>, value: string): string {
  return map[value] ?? value;
}
