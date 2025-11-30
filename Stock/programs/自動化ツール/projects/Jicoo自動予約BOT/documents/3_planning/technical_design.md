# 技術設計書：Jicoo 自動予約 BOT

## 1. システムアーキテクチャ

### 1.1 全体構成

```
┌─────────────────────────────────────┐
│   Jicoo自動予約BOT (Main Class)    │
├─────────────────────────────────────┤
│  - URL監視ループ                    │
│  - スケジューラー管理               │
│  - エラーハンドリング               │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│   ReservationService               │
├─────────────────────────────────────┤
│  - URL処理                          │
│  - ログイン処理                     │
│  - カレンダー操作                   │
│  - タイムスロット監視               │
│  - 予約フォーム入力                 │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│   WebDriverManager                  │
│   Selenium WebDriver                │
└─────────────────────────────────────┘
```

### 1.2 主要クラス設計

#### 1.2.1 Main Class: `JicooReservationBot`

**責務**:

- アプリケーションのエントリーポイント
- スケジューラーの管理
- 全体のエラーハンドリング

**主要メソッド**:

- `main(String[] args)`: エントリーポイント
- `startMonitoring()`: 監視開始
- `stopMonitoring()`: 監視停止

#### 1.2.2 ReservationService

**責務**:

- 予約処理の主要ロジック
- URL ごとの処理
- 各ステップの実行

**主要メソッド**:

- `processUrl(String url)`: URL 処理
- `handleLoginPopup(WebDriver driver)`: ログインポップアップ処理
- `selectDate(WebDriver driver, LocalDate targetDate)`: 日付選択
- `monitorTimeSlot(WebDriver driver, String timeSlot)`: タイムスロット監視
- `fillReservationForm(WebDriver driver, String name, String email)`: フォーム入力
- `submitReservation(WebDriver driver)`: 予約確定

#### 1.2.3 WebDriverManager

**責務**:

- WebDriver の初期化
- ChromeDriver の自動取得
- WebDriver の設定

**主要メソッド**:

- `createWebDriver(boolean headless)`: WebDriver 作成
- `closeWebDriver(WebDriver driver)`: WebDriver 終了

#### 1.2.4 Config

**責務**:

- 設定値の管理
- 設定ファイルの読み込み

**主要プロパティ**:

- `URLS`: 監視対象 URL リスト
- `USERNAME`: ログインユーザー名
- `PASSWORD`: ログインパスワード
- `TARGET_TIME`: 対象時間（20:25）
- `MONITORING_INTERVAL`: 監視間隔（秒）
- `RESERVATION_NAME`: 予約者名
- `RESERVATION_EMAIL`: 予約者メールアドレス

## 2. 処理フロー

### 2.1 メインフロー

```
1. アプリケーション起動
   ↓
2. 設定読み込み
   ↓
3. WebDriver初期化
   ↓
4. 監視ループ開始
   ├─→ URL1処理
   ├─→ URL2処理
   ├─→ URL3処理
   └─→ URL4処理
   ↓
5. 予約成功 or 全URL処理完了
   ↓
6. 監視ループ終了
   ↓
7. WebDriver終了
   ↓
8. アプリケーション終了
```

### 2.2 URL 処理フロー

```
1. URLへアクセス
   ↓
2. ページ読み込み待機
   ↓
3. ログインポップアップ確認
   ├─→ 存在する → ログイン処理
   └─→ 存在しない → 次へ
   ↓
4. カレンダー画面確認
   ↓
5. 1週間後の日付を選択
   ↓
6. タイムスロット一覧表示待機
   ↓
7. 20:25のボタンを探す
   ├─→ 存在しない → 次のURLへ
   ├─→ disabled → 次のURLへ
   └─→ 押せる → クリック
   ↓
8. 予約フォーム画面へ遷移
   ↓
9. フォーム入力
   ↓
10. 予約確定ボタンクリック
   ↓
11. 成功確認
   ↓
12. 処理終了
```

### 2.3 ログインポップアップ処理フロー

```
1. ログインポップアップ要素の存在確認
   ├─→ input[type="text"] 存在確認
   ├─→ input[type="password"] 存在確認
   └─→ button[type="submit"] 存在確認
   ↓
2. 要素がすべて存在する場合
   ├─→ ユーザー名入力: students
   ├─→ パスワード入力: uluru2024
   └─→ サブミットボタンクリック
   ↓
3. ログイン成功待機
   ↓
4. カレンダー画面へ遷移確認
```

## 3. 技術実装詳細

### 3.1 WebDriver 設定

```java
ChromeOptions options = new ChromeOptions();
if (headless) {
    options.addArguments("--headless");
}
options.addArguments("--no-sandbox");
options.addArguments("--disable-dev-shm-usage");
options.addArguments("--disable-blink-features=AutomationControlled");
options.setExperimentalOption("excludeSwitches",
    Collections.singletonList("enable-automation"));

WebDriverManager.chromedriver().setup();
WebDriver driver = new ChromeDriver(options);
driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
driver.manage().window().maximize();
```

### 3.2 明示的待機（WebDriverWait）

```java
WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

// 要素の存在確認
wait.until(ExpectedConditions.presenceOfElementLocated(
    By.cssSelector("input[type='text']")));

// クリック可能になるまで待機
wait.until(ExpectedConditions.elementToBeClickable(
    By.xpath("//button[contains(text(), '20:25')]")));
```

### 3.3 ログインポップアップ検出

```java
public boolean isLoginPopupPresent(WebDriver driver) {
    try {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        wait.until(ExpectedConditions.presenceOfElementLocated(
            By.cssSelector("input[type='text']")));
        wait.until(ExpectedConditions.presenceOfElementLocated(
            By.cssSelector("input[type='password']")));
        wait.until(ExpectedConditions.presenceOfElementLocated(
            By.cssSelector("button[type='submit']")));
        return true;
    } catch (TimeoutException e) {
        return false;
    }
}
```

### 3.4 日付計算

```java
LocalDate today = LocalDate.now();
LocalDate targetDate = today.plusDays(7);
```

### 3.5 タイムスロット監視

```java
public void monitorTimeSlot(WebDriver driver, String timeSlot) {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    scheduler.scheduleAtFixedRate(() -> {
        try {
            // ページ更新
            driver.navigate().refresh();

            // タイムスロットボタンを探す
            WebElement timeSlotButton = driver.findElement(
                By.xpath("//button[contains(text(), '" + timeSlot + "')]"));

            // ボタンが有効か確認
            if (timeSlotButton.isEnabled() &&
                !timeSlotButton.getAttribute("disabled").equals("true")) {
                // 即座にクリック
                timeSlotButton.click();
                scheduler.shutdown();
            }
        } catch (NoSuchElementException e) {
            // ボタンが見つからない場合は次回の監視で再試行
        }
    }, 0, 5, TimeUnit.SECONDS);
}
```

### 3.6 エラーハンドリングとリトライ

```java
public boolean processWithRetry(Supplier<Boolean> process, int maxRetries) {
    for (int i = 0; i < maxRetries; i++) {
        try {
            if (process.get()) {
                return true;
            }
        } catch (Exception e) {
            logger.warn("処理失敗 (試行 {}/{}): {}", i + 1, maxRetries, e.getMessage());
            if (i < maxRetries - 1) {
                try {
                    Thread.sleep(5000); // 5秒待機
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    return false;
}
```

## 4. 設定ファイル設計

### 4.1 application.properties

```properties
# 監視対象URL（カンマ区切り）
jicoo.urls=https://www.jicoo.com/t/_XDgWVCOgMPP/e/Teacher_Vanessa,\
           https://www.jicoo.com/t/_XDgWVCOgMPP/e/Jozelly,\
           https://www.jicoo.com/t/_XDgWVCOgMPP/e/Teacher_Lorna,\
           https://www.jicoo.com/t/_XDgWVCOgMPP/e/namie

# ログイン情報
jicoo.login.username=students
jicoo.login.password=uluru2024

# 予約情報
jicoo.reservation.name=道川正隆
jicoo.reservation.email=masataaaka3@icloud.com

# 監視設定
jicoo.target.time=20:25
jicoo.monitoring.interval.seconds=5
jicoo.max.retries=3

# WebDriver設定
webdriver.headless=false
webdriver.timeout.seconds=30
webdriver.implicit.wait.seconds=10
```

## 5. ログ設計

### 5.1 ログレベル

- **INFO**: 通常の処理フロー
- **WARN**: 警告（リトライ等）
- **ERROR**: エラー（処理失敗等）

### 5.2 ログ出力例

```
2025-11-18 20:00:00 [INFO] アプリケーション起動
2025-11-18 20:00:01 [INFO] WebDriver初期化完了
2025-11-18 20:00:02 [INFO] URL処理開始: https://www.jicoo.com/t/_XDgWVCOgMPP/e/Teacher_Vanessa
2025-11-18 20:00:05 [INFO] ログインポップアップ検出
2025-11-18 20:00:06 [INFO] ログイン成功
2025-11-18 20:00:10 [INFO] 日付選択: 2025-11-25
2025-11-18 20:00:15 [INFO] タイムスロット監視開始: 20:25
2025-11-18 20:05:20 [INFO] タイムスロットボタンが有効になりました
2025-11-18 20:05:21 [INFO] タイムスロットクリック
2025-11-18 20:05:25 [INFO] 予約フォーム入力完了
2025-11-18 20:05:26 [INFO] 予約確定
2025-11-18 20:05:30 [INFO] 予約成功確認
2025-11-18 20:05:31 [INFO] 処理完了
```

## 6. 依存関係（Maven/Gradle）

### 6.1 Maven 依存関係

```xml
<dependencies>
    <!-- Selenium -->
    <dependency>
        <groupId>org.seleniumhq.selenium</groupId>
        <artifactId>selenium-java</artifactId>
        <version>4.15.0</version>
    </dependency>

    <!-- WebDriverManager -->
    <dependency>
        <groupId>io.github.bonigarcia</groupId>
        <artifactId>webdrivermanager</artifactId>
        <version>5.6.2</version>
    </dependency>

    <!-- SLF4J API -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>2.0.9</version>
    </dependency>

    <!-- Logback -->
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.4.11</version>
    </dependency>
</dependencies>
```

## 7. ディレクトリ構造

```
jicoo-reservation-bot/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── jicoo/
│       │           └── bot/
│       │               ├── JicooReservationBot.java
│       │               ├── ReservationService.java
│       │               ├── WebDriverManager.java
│       │               └── Config.java
│       └── resources/
│           ├── application.properties
│           └── logback.xml
├── pom.xml (or build.gradle)
└── README.md
```

## 8. テスト戦略

### 8.1 単体テスト

- 日付計算ロジック
- 設定読み込み
- エラーハンドリング

### 8.2 統合テスト

- WebDriver 操作のモック化
- 各処理ステップの動作確認

### 8.3 動作確認

- 実際の Web サイトでの動作確認
- ログインポップアップの検出確認
- タイムスロット監視の動作確認

---

**作成日**: 2025-11-18  
**確定日**: 2025-11-18  
**最終更新日**: 2025-11-18  
**バージョン**: 1.0
