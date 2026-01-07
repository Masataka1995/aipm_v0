# Jicoo 自動予約 BOT

Jicoo（オンライン予約システム）において、指定されたレッスン時間（20:25）の予約を自動的に行う BOT システムです。

## 機能

- 4 つの URL を自動監視
- ログインポップアップの自動処理
- 1 週間後の日付の自動選択
- 20:25 タイムスロットの監視と自動予約
- 予約フォームの自動入力
- エラーハンドリングとリトライ機能

## 要件

- Java 17 以上
- Maven 3.6 以上
- Chrome ブラウザ

## セットアップ

### 1. 依存ライブラリのダウンロード

```bash
mvn clean install
```

### 2. 設定ファイルの確認

`src/main/resources/application.properties` を確認し、必要に応じて設定を変更してください。

## 実行方法

### GUI アプリケーションで実行（推奨）

```bash
mvn exec:java
```

または

```bash
# JARファイルの作成
mvn clean package

# GUIアプリケーションを起動
java -jar target/jicoo-reservation-bot-1.0.0.jar
```

GUI アプリケーションが起動します。以下の操作が可能です：

- **開始ボタン**: 予約監視を開始
- **停止ボタン**: 予約監視を停止
- **ログ表示**: リアルタイムでログを確認
- **ステータス表示**: 現在の状態を確認
- **日付管理**: 予約したい日付を追加・削除・ON/OFF 切り替えが可能
- **結果表示**: 予約成功（緑）・失敗（赤）を色分け表示

### バックグラウンド実行機能

アプリケーションはシステムトレイでバックグラウンド実行に対応しています：

- **ウィンドウを閉じる**: ウィンドウを閉じると、システムトレイに最小化されます（アプリケーションは継続実行）
- **システムトレイアイコン**: タスクバーの通知領域に「J」アイコンが表示されます
- **トレイアイコン操作**:
  - **ダブルクリック**: ウィンドウを表示
  - **右クリック → 表示**: ウィンドウを表示
  - **右クリック → 終了**: アプリケーションを終了
- **通知機能**:
  - 予約成功/失敗時にトレイアイコンから通知が表示されます
  - すべての処理完了時にも通知が表示されます

### コマンドライン実行（旧方式）

コマンドラインで実行する場合は、`JicooReservationBot`クラスを直接実行してください。

## 設定

設定は `src/main/resources/application.properties` で行います。

### 主要な設定項目

- `jicoo.urls`: 監視対象 URL（カンマ区切り）
- `jicoo.login.username`: ログインユーザー名
- `jicoo.login.password`: ログインパスワード
- `jicoo.reservation.name`: 予約者名
- `jicoo.reservation.email`: 予約者メールアドレス
- `jicoo.target.time`: 対象時間（例: 20:25）
- `jicoo.monitoring.interval.seconds`: 監視間隔（秒）
- `webdriver.headless`: ヘッドレスモード（true/false）
- `sleep.prevent.enabled`: スリープモード防止（true/false）
  - `true`（推奨）: 監視中はスリープモードに入らないようにする
  - `false`: スリープモードを許可（スリープから復帰時に自動的に監視を再開）

## ログ

ログは以下の場所に出力されます：

- コンソール: 標準出力
- ファイル: `logs/jicoo-bot.log`

## プロジェクト構造

```
jicoo-reservation-bot/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── jicoo/
│   │   │           └── bot/
│   │   │               ├── JicooReservationBot.java
│   │   │               ├── ReservationService.java
│   │   │               ├── WebDriverManager.java
│   │   │               └── Config.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── logback.xml
│   └── test/
│       └── java/
├── pom.xml
└── README.md
```

## ライセンス

個人利用のみ

---

**作成日**: 2025-11-18  
**バージョン**: 1.0.0
