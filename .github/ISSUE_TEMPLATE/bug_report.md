---
name: 🐛 Bug Report
about: Report a bug to help us improve
title: "[BUG]: "
labels: bug, needs-triage
assignees: ''

---

## バグの概要 (Bug Summary)
発生している問題を簡潔に、かつ具体的に記述してください。

例: ログインボタンをクリックしても反応がない。

## 再現手順 (Steps to Reproduce)
バグを再現するための具体的な手順を、ステップバイステップで記述してください。

1.  ブラウザで `https://example.com/login` にアクセスする。
2.  ユーザー名「testuser」、パスワード「password」を入力する。
3.  「ログイン」ボタンをクリックする。

## 期待される挙動 (Expected Behavior)
上記の再現手順を実行した際に、本来あるべき正しい挙動を記述してください。

例: ログインボタンをクリックすると、ダッシュボードページにリダイレクトされるべき。

## 実際の挙動 (Actual Behavior)
上記の再現手順を実行した際に、実際に発生した異常な挙動を記述してください。エラーメッセージや、画面のフリーズなど、詳細に記述してください。

例: ログインボタンをクリックしても何も起こらず、コンソールに `Uncaught TypeError: Cannot read property 'login' of undefined` が表示される。

## 環境 (Environment)
バグが発生した環境に関する情報を提供してください。

-   **OS**: [例: macOS Ventura 13.5 / Windows 11 / Ubuntu 22.04]
-   **ブラウザ**: [例: Google Chrome 114.0.5735.198 / Firefox 115.0 / Safari 16.5]
-   **デバイス**: [例: MacBook Pro (M1, 2020) / Dell XPS 15 / iPhone 14 Pro]
-   **アプリケーションバージョン**: [例: v1.2.0 / `git rev-parse HEAD` のコミットハッシュ]

## スクリーンショット/動画 (Screenshots/Videos) - オプション
バグの状況を視覚的に示すスクリーンショットや短い動画を添付してください。

## 関連するログ/エラーメッセージ (Relevant Logs/Error Messages) - オプション
コンソールに出力されたエラーメッセージ、サーバーログ、スタックトレースなど、関連する技術的な情報を貼り付けてください。
