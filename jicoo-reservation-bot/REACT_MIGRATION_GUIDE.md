# React移行手順書

## 概要
現在のVanilla JavaScript実装をReactに移行するための手順書です。

---

## 前提条件
- Node.js 18以上
- npm または yarn
- 既存のJavaバックエンドは変更不要

---

## 手順

### 1. プロジェクト構造の準備

#### 1.1 フロントエンド用ディレクトリの作成
```
jicoo-reservation-bot/
├── frontend/              # 新規作成（Reactプロジェクト）
│   ├── src/
│   ├── public/
│   ├── package.json
│   └── ...
└── src/main/webapp/       # 既存（バックアップ用）
```

#### 1.2 Reactプロジェクトの初期化
```bash
cd jicoo-reservation-bot
npx create-react-app frontend
# または
yarn create react-app frontend
```

---

### 2. 依存関係のインストール

#### 2.1 必要なパッケージの追加
```bash
cd frontend
npm install axios                    # HTTP通信
npm install react-router-dom         # ルーティング（必要に応じて）
npm install @tanstack/react-query    # データフェッチング（オプション）
npm install date-fns                 # 日付操作
```

#### 2.2 開発用依存関係
```bash
npm install --save-dev @types/react  # TypeScript使用時
```

---

### 3. プロジェクト構造の設計

#### 3.1 ディレクトリ構造
```
frontend/
├── src/
│   ├── components/          # 再利用可能なコンポーネント
│   │   ├── Calendar/
│   │   ├── DateList/
│   │   ├── TeacherList/
│   │   ├── LogArea/
│   │   └── ControlPanel/
│   ├── hooks/               # カスタムフック
│   │   ├── useWebSocket.js
│   │   ├── useApi.js
│   │   └── useReservation.js
│   ├── services/            # API通信
│   │   ├── api.js
│   │   └── websocket.js
│   ├── contexts/            # Context API
│   │   ├── AppContext.js
│   │   └── ReservationContext.js
│   ├── utils/               # ユーティリティ
│   │   └── dateUtils.js
│   ├── App.js
│   └── index.js
├── public/
└── package.json
```

---

### 4. コンポーネントの実装

#### 4.1 主要コンポーネントの分割
- **Header**: ヘッダーとステータスバー
- **TeacherSelection**: 先生選択セクション
- **DateManagement**: 日付管理セクション
  - **DatePicker**: 日付追加パネル
  - **Calendar**: カレンダー表示
  - **DateList**: 日付リスト表示
- **LogSection**: ログ出力セクション
- **ControlPanel**: 操作パネル
- **CompletedReservations**: 予約完了日表示

#### 4.2 カスタムフックの実装
- **useWebSocket**: WebSocket接続管理
- **useApi**: REST API呼び出し
- **useReservation**: 予約状態管理

---

### 5. 状態管理の実装

#### 5.1 Context APIの設定
```javascript
// contexts/AppContext.js
- 日付リスト
- 先生リスト
- ログメッセージ
- 監視状態
- 予約完了日
```

#### 5.2 ローカル状態管理
- 各コンポーネントでuseStateを使用
- 複雑な状態はuseReducerを検討

---

### 6. API通信の実装

#### 6.1 REST APIサービスの作成
```javascript
// services/api.js
- GET /api/status
- GET /api/dates
- GET /api/completed-reservations
- GET /api/teachers
- GET /api/time-slots
- POST /api/dates
- POST /api/start-monitoring
- POST /api/stop-monitoring
```

#### 6.2 WebSocketサービスの作成
```javascript
// services/websocket.js
- WebSocket接続管理
- 再接続ロジック
- メッセージハンドリング
```

---

### 7. スタイリングの移行

#### 7.1 CSS ModulesまたはStyled Components
- 既存の`styles.css`をコンポーネント単位に分割
- CSS Modules: `Component.module.css`
- またはStyled Componentsを使用

#### 7.2 レスポンシブデザインの維持
- 既存のスタイルをReactコンポーネントに適用

---

### 8. ビルド設定

#### 8.1 package.jsonのスクリプト設定
```json
{
  "scripts": {
    "build": "react-scripts build",
    "build:prod": "react-scripts build && npm run copy-to-webapp",
    "copy-to-webapp": "xcopy /E /I /Y build\\* ..\\src\\main\\webapp\\"
  }
}
```

#### 8.2 ビルド出力先の設定
- `package.json`の`homepage`を設定（必要に応じて）
- ビルド後のファイルを`src/main/webapp/`にコピー

---

### 9. バックエンドとの統合

#### 9.1 静的ファイル配信の確認
- `StaticFileServlet.java`がビルド後のファイルを配信できるか確認
- 必要に応じてパス設定を調整

#### 9.2 APIエンドポイントの確認
- 既存のAPIエンドポイントは変更不要
- CORS設定を確認（既に`CorsFilter.java`がある）

---

### 10. 開発環境の設定

#### 10.1 プロキシ設定（開発時）
```json
// package.json
{
  "proxy": "http://localhost:8080"
}
```

#### 10.2 環境変数
```env
// .env.development
REACT_APP_API_BASE=http://localhost:8080/api
REACT_APP_WS_URL=ws://localhost:8080/ws
```

---

### 11. 段階的移行（推奨）

#### 11.1 フェーズ1: 小さなコンポーネントから
- LogAreaコンポーネント
- ControlPanelコンポーネント

#### 11.2 フェーズ2: 中規模コンポーネント
- TeacherSelection
- DateList

#### 11.3 フェーズ3: 複雑なコンポーネント
- Calendar
- DateManagement

#### 11.4 フェーズ4: 統合とテスト
- 全体の統合
- 既存機能の動作確認

---

### 12. テスト

#### 12.1 機能テスト
- 日付追加・削除
- 先生選択
- 監視開始・停止
- WebSocket接続
- ログ表示

#### 12.2 統合テスト
- バックエンドAPIとの連携
- WebSocket通信
- エラーハンドリング

---

### 13. デプロイ準備

#### 13.1 ビルドスクリプトの作成
```bash
# build-frontend.bat (Windows)
cd frontend
npm run build
xcopy /E /I /Y build\* ..\src\main\webapp\
```

#### 13.2 Mavenビルドとの統合
- `pom.xml`にフロントエンドビルドを追加（オプション）
- または手動でビルド後にMavenビルド

---

### 14. 既存ファイルのバックアップ

#### 14.1 バックアップ
```bash
# 既存のwebappディレクトリをバックアップ
cp -r src/main/webapp src/main/webapp.backup
```

#### 14.2 段階的置き換え
- 新しいReactビルドで段階的に置き換え
- 問題があればバックアップから復元

---

## 注意事項

### 既存機能の維持
- すべての既存機能をReactで再実装
- バックエンドAPIは変更しない
- WebSocketプロトコルは維持

### パフォーマンス
- 不要な再レンダリングを防ぐ（React.memo, useMemo, useCallback）
- 大量のログ表示時のパフォーマンスに注意

### ブラウザ互換性
- 既存のブラウザサポートを維持
- WebSocketのフォールバック処理

---

## 参考リソース

### React公式ドキュメント
- https://react.dev/

### 状態管理
- Context API: https://react.dev/reference/react/useContext
- React Query: https://tanstack.com/query/latest

### WebSocket
- React Hooks for WebSocket: カスタムフック実装

---

## 完了チェックリスト

- [ ] Reactプロジェクトの初期化
- [ ] 依存関係のインストール
- [ ] プロジェクト構造の作成
- [ ] コンポーネントの実装
- [ ] カスタムフックの実装
- [ ] API通信の実装
- [ ] WebSocket接続の実装
- [ ] スタイリングの移行
- [ ] ビルド設定
- [ ] バックエンドとの統合確認
- [ ] 機能テスト
- [ ] 統合テスト
- [ ] デプロイ準備
- [ ] 既存ファイルのバックアップ

---

## トラブルシューティング

### ビルドエラー
- Node.jsのバージョンを確認
- 依存関係の再インストール

### API接続エラー
- CORS設定を確認
- プロキシ設定を確認

### WebSocket接続エラー
- WebSocket URLの確認
- 再接続ロジックの確認

---

## 次のステップ

1. 小さなコンポーネントから実装開始
2. 段階的に機能を移行
3. 各フェーズでテストを実施
4. 最終的に既存のVanilla JSを置き換え

