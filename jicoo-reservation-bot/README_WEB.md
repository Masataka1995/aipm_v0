# Jicoo 自動予約 BOT - Web アプリケーション版

## 概要

Jicoo 自動予約 BOT を Web アプリケーションとして再構築しました。

- **フロントエンド**: HTML/CSS/JavaScript（モダンな UI）
- **バックエンド**: Java REST API（Jetty HTTP サーバー）
- **リアルタイム通信**: WebSocket
- **動作環境**: ローカル環境のみ（外部サーバー不要）

## 起動方法

### 方法 1: バッチファイルから起動（推奨）

```bash
run-web.bat
```

起動後、自動的にブラウザで `http://localhost:8080/index.html` が開きます。

### 方法 2: JAR ファイルから直接起動

```bash
java -cp target/jicoo-reservation-bot-1.0.0.jar com.jicoo.bot.api.RestApiServer
```

起動後、ブラウザで以下にアクセス：

```
http://localhost:8080/index.html
```

### 方法 3: Maven から直接実行

```bash
mvn exec:java -Dexec.mainClass="com.jicoo.bot.api.RestApiServer"
```

## 注意事項

- **ローカル環境のみで動作**: 外部サーバーや別途 Web サーバー（Apache、Nginx など）は不要です
- ポート 8080 が使用中の場合は、`-Dserver.port=別のポート番号`で変更可能
- 既存の Swing GUI（`JicooReservationBotGUI`）も引き続き使用可能です
- 停止するには、コマンドプロンプトで `Ctrl+C` を押してください

## 機能

### REST API エンドポイント

- `GET /api/status` - システムステータス取得
- `GET /api/dates` - 予約対象日付一覧取得
- `POST /api/dates` - 日付追加
- `PUT /api/dates/{date}` - 日付更新（ON/OFF、時間帯設定）
- `DELETE /api/dates/{date}` - 日付削除
- `GET /api/completed-reservations` - 予約完了日一覧取得
- `GET /api/config` - 設定情報取得
- `PUT /api/config/monitoring-time-restriction` - 監視時間制限の ON/OFF
- `POST /api/monitoring/start` - 監視開始
- `POST /api/monitoring/stop` - 監視停止
- `POST /api/manual-reserve` - 手動予約

### WebSocket

- エンドポイント: `/ws`
- メッセージタイプ:
  - `log`: ログメッセージ
  - `reservationResult`: 予約結果
  - `status`: ステータス更新

## 構成

```
jicoo-reservation-bot/
├── src/main/
│   ├── java/com/jicoo/bot/
│   │   ├── api/              # REST API関連
│   │   │   ├── RestApiServer.java
│   │   │   ├── ApiServlet.java
│   │   │   ├── WebSocketHandler.java
│   │   │   ├── CorsFilter.java
│   │   │   └── StaticFileServlet.java
│   │   └── ...               # 既存のBOTロジック
│   └── webapp/               # Webアプリケーション
│       ├── index.html
│       ├── styles.css
│       └── app.js
├── run-web.bat               # Web版起動用バッチファイル
└── run.bat                   # Swing GUI版起動用バッチファイル
```

## Swing GUI 版との違い

- **Web 版**: ブラウザでアクセス、モダンな UI、レスポンシブ対応
- **Swing GUI 版**: デスクトップアプリケーション、システムトレイ対応

どちらも同じ BOT ロジックを使用しており、機能は同等です。
