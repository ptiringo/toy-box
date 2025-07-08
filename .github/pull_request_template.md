---
name: 🚀 Feature / Enhancement
about: For new features or improvements
title: "[FEAT]: "
labels: enhancement, needs-review
assignees: ''

---

## 目的 (Purpose/Goal)
このプルリクエストが解決しようとしている課題、または達成したい目標を簡潔に記述してください。
関連する Issue があれば、リンクを張ってください。

例: `#123` (ユーザープロフィール編集機能の実装) を完了させる。

## 変更の概要 (Summary of Changes)
このプルリクエストで行われた主要な変更点を箇条書きでまとめてください。

- `src/components/UserProfile.js`: プロフィール画像アップロードコンポーネントを追加
- `server/routes/users.js`: 画像アップロード用のAPIエンドポイントを実装
- `database/migrations/2023xxxx_add_profile_image_to_users.sql`: `users` テーブルに `profile_image_url` カラムを追加

## 技術的な詳細 (Technical Details) - オプション
変更の実装方法や、採用した技術的選択について説明してください。

例:
- 画像アップロードには `multer` ライブラリを使用し、S3 バケットに直接アップロードするように設定しました。
- フロントエンドでは `FileReader` API を使用して、プレビュー画像を生成しています。

## テスト方法 (How to Test)
このプルリクエストの変更が正しく機能することを確認するための具体的な手順を記述してください。

1.  ローカル環境でブランチをチェックアウトし、`npm install` と `npm run dev` を実行する。
2.  ユーザーとしてログインし、プロフィール編集ページ (`/profile/edit`) にアクセスする。
3.  「画像を選択」ボタンをクリックし、適当な画像ファイル（例: `test.jpg`）を選択してアップロードする。
4.  アップロードされた画像がプロフィールに表示されることを確認する。
5.  開発者ツールでネットワークタブを確認し、画像がS3にアップロードされていることを確認する。

## レビューしてほしい点 (Points for Review)
特にレビュー担当者や Copilot に確認してほしい点、疑問点、懸念事項などがあれば記述してください。

- 画像アップロードのエラーハンドリングについて、より堅牢な実装が必要か。
- S3へのアップロード処理のセキュリティについて、改善点がないか。
- `UserProfile.js` のコンポーネント分割が適切か。

## スクリーンショット/動画 (Screenshots/Videos) - オプション
UI の変更を含む場合、変更前後のスクリーンショットや動画を添付してください。

## 依存関係/影響 (Dependencies/Impact) - オプション
このプルリクエストが他のプルリクエストや機能に依存している、または影響を与える可能性がある場合に記述してください。

例: この PR は `aws-sdk` パッケージのインストールが必要です。
