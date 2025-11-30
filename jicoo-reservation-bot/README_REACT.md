# React フロントエンド

フロントエンドは React で実装されています。

## セットアップ

### 1. Node.js のインストール

Node.js 16 以上が必要です。未インストールの場合は以下からダウンロードしてください：
https://nodejs.org/

### 2. 依存パッケージのインストール

```bash
npm install
```

### 3. ビルド

```bash
npm run build
```

または、Windows の場合：

```bash
build-frontend.bat
```

ビルド後、`src/main/webapp/bundle.js`が生成されます。

## 開発

開発モードでビルド（ファイル変更を監視）：

```bash
npm run dev
```

## プロジェクト構造

```
src/main/webapp-react/
├── index.js              # エントリーポイント
├── App.js                # メインアプリケーションコンポーネント
├── styles.css            # スタイルシート
├── components/           # Reactコンポーネント
│   ├── Header.js
│   ├── DateManagement.js
│   ├── Calendar.js
│   ├── DateList.js
│   ├── LogSection.js
│   └── ControlPanel.js
└── hooks/                # カスタムフック
    ├── useWebSocket.js
    └── useApi.js
```

## ビルドプロセス

1. `build-frontend.bat`を実行
2. Webpack が`src/main/webapp-react`のソースをビルド
3. `src/main/webapp/bundle.js`が生成される
4. `build.bat`を実行すると、自動的にフロントエンドもビルドされる

## 注意事項

- フロントエンドを変更した場合は、必ず`npm run build`を実行してください
- `src/main/webapp/bundle.js`は自動生成されるため、手動で編集しないでください
- 既存の`app.js`と`styles.css`は React 版では使用されません（互換性のため残しています）
