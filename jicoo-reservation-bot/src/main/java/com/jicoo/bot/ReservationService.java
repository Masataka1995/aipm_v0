package com.jicoo.bot;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 予約処理サービス
 * URLごとの予約処理を実行する
 */
public class ReservationService {
    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);
    
    // 定数定義
    private static final int DEFAULT_CLICK_WAIT_MS = 200;
    private static final int MIN_MONITORING_INTERVAL_SECONDS = 1;
    
    // XPathセレクタの定数化
    private static final String MUI_BUTTON_XPATH = "//button[contains(@class, 'MuiButton')]";
    private static final String TIME_SLOT_BUTTON_XPATH = MUI_BUTTON_XPATH + " | //button[contains(text(), ':')]";
    
    // WebDriverWaitのタイムアウト定数
    private static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(5);
    
    // 文字列リテラルの定数化
    private static final String ATTR_DISABLED = "disabled";
    private static final String ATTR_CLASS = "class";
    private static final String ATTR_ARIA_DISABLED = "aria-disabled";
    private static final String CLASS_MUI_DISABLED = "Mui-disabled";
    private static final String DATE_PARAM_PREFIX = "date=";
    private static final String BUTTON_TEXT_PREFIX = "//button[contains(text(), '";
    private static final String STEP_SELECTOR_NOT_FOUND = "【STEP】handleLoginPopup - セレクタ {} で要素が見つかりませんでした";
    private static final String ERROR_CAUSE_FORMAT = "原因: %s";
    private static final String ERROR_MESSAGE_FORMAT = "エラーメッセージ: %s, エラークラス: %s%s";
    
    private final Config config;
    
    public ReservationService(Config config) {
        this.config = config;
    }
    
    /**
     * URLを処理して予約を試みる（デフォルト日付使用）
     * @param driver WebDriver
     * @param url 処理するURL
     * @return 予約が成功した場合true
     */
    public boolean processUrl(WebDriver driver, String url) {
        return processUrl(driver, url, config.getTargetDate());
    }
    
    /**
     * URLを処理して予約を試みる（日付指定）
     * @param driver WebDriver
     * @param url 処理するURL
     * @param targetDate 予約対象日付
     * @return 予約が成功した場合true
     */
    public boolean processUrl(WebDriver driver, String url, LocalDate targetDate) {
        return processUrl(driver, url, targetDate, null);
    }
    
    /**
     * URLを処理して予約を試みる（日付と時間帯リスト指定）
     * @param driver WebDriver
     * @param url 処理するURL
     * @param targetDate 予約対象日付
     * @param timeSlots 監視する時間帯のリスト（nullの場合はconfig.getTargetTime()を使用）
     * @return 予約が成功した場合true
     */
    public boolean processUrl(WebDriver driver, String url, LocalDate targetDate, List<String> timeSlots) {
        return processUrl(driver, url, targetDate, timeSlots, null);
    }
    
    /**
     * URLを処理して予約を試みる（日付と時間帯リスト指定、日付成功フラグ付き）
     * @param driver WebDriver
     * @param url 処理するURL
     * @param targetDate 予約対象日付
     * @param timeSlots 監視する時間帯のリスト（nullの場合はconfig.getTargetTime()を使用）
     * @param dateSuccessFlag 日付の成功フラグ（その日の予約が成功したらtrueになる。nullの場合はチェックしない）
     * @return 予約が成功した場合true
     */
    public boolean processUrl(WebDriver driver, String url, LocalDate targetDate, List<String> timeSlots, AtomicBoolean dateSuccessFlag) {
        logger.debug("URL処理開始: {}, 対象日付: {}, 時間帯: {}", url, targetDate, timeSlots);
        
        // 監視時間内かどうかをチェック（日本時間）
        if (!config.isWithinMonitoringHours()) {
            logger.warn("監視時間外です（日本時間 {}時〜{}時）。処理をスキップします", 
                config.getMonitoringStartHour(), config.getMonitoringEndHour());
            return false;
        }
        
        // 時間帯リストが空またはnullの場合はデフォルト時間を使用
        if (timeSlots == null || timeSlots.isEmpty()) {
            timeSlots = List.of(config.getTargetTime());
        }
        
        try {
            // URLに日付パラメータを追加
            String urlWithDate = addDateParameterToUrl(url, targetDate);
            logger.debug("日付パラメータ付きURL: {}", urlWithDate);
            
            // URLへアクセス
            try {
                logger.debug("【STEP】processUrl - URLへアクセス開始: {}", urlWithDate);
                driver.get(urlWithDate);
                logger.debug("【STEP】processUrl - ページにアクセスしました: {}", urlWithDate);
            } catch (UnreachableBrowserException e) {
                // ブラウザが予期せず終了した場合
                logger.warn("【WARN】processUrl - ブラウザとの通信が切断されました（リトライ可能）: {}", e.getMessage());
                throw e; // リトライのために再スロー
            } catch (Exception e) {
                // InterruptedExceptionが原因の場合は警告レベル
                if (e.getCause() instanceof InterruptedException || e instanceof InterruptedException) {
                    logger.warn("【WARN】processUrl - URLアクセスが中断されました（リトライ可能）: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                } else {
                    logger.error("【ERROR】processUrl - URLアクセス中にエラーが発生しました", e);
                    logger.error("【ERROR】processUrl - URL: {}", urlWithDate);
                    logger.error("【ERROR】processUrl - エラーメッセージ: {}", e.getMessage());
                }
                throw e;
            }
            
            // ページ読み込み待機（共通メソッドを使用）
            waitForPageLoad(driver);
            
            // ログインポップアップ処理
            logger.debug("【STEP】processUrl - ログインポップアップ処理を開始します");
            if (!handleLoginPopup(driver)) {
                logger.error("【ERROR】processUrl - ログイン処理に失敗しました");
                return false;
            }
            logger.debug("【STEP】processUrl - ログインポップアップ処理が完了しました");
            
            // URLに日付パラメータが含まれている場合、日付選択はスキップ可能
            // タイムスロットの表示を待機（共通メソッドを使用）
            logger.debug("URLに日付パラメータが含まれています。タイムスロットの表示を待機中...");
            waitForTimeSlotButtons(driver);
            
            // 選択された時間帯を順番に監視（日付は既に選択済み）
            for (String timeSlot : timeSlots) {
                // この日付の予約が既に成功している場合はスキップ
                if (dateSuccessFlag != null && dateSuccessFlag.get()) {
                    logger.info("【SKIP】processUrl - 日付 {} の予約が既に成功しているため、時間帯 {} の監視をスキップします", targetDate, timeSlot);
                    break;
                }
                
                logger.debug("【STEP】processUrl - 時間帯 {} を監視します（日付は既に選択済み）", timeSlot);
                if (monitorTimeSlot(driver, timeSlot, targetDate, dateSuccessFlag)) {
                    logger.info("【STEP】processUrl - 時間帯 {} のクリックに成功しました", timeSlot);
                    
                    // この日付の予約が既に成功している場合はスキップ
                    if (dateSuccessFlag != null && dateSuccessFlag.get()) {
                        logger.info("【SKIP】processUrl - 日付 {} の予約が既に成功しているため、フォーム入力をスキップします", targetDate);
                        break;
                    }
                    
                    // 予約フォーム入力
                    logger.info("【STEP】processUrl - 予約フォーム入力を開始します");
                    if (!fillReservationForm(driver, config.getReservationName(), config.getReservationEmail())) {
                        logger.error("【ERROR】processUrl - 予約フォーム入力に失敗しました。次の時間帯を試行します");
                        continue; // 次の時間帯を試行
                    }
                    logger.info("【STEP】processUrl - 予約フォーム入力が完了しました");
                    
                    // この日付の予約が既に成功している場合はスキップ
                    if (dateSuccessFlag != null && dateSuccessFlag.get()) {
                        logger.info("【SKIP】processUrl - 日付 {} の予約が既に成功しているため、予約確定をスキップします", targetDate);
                        break;
                    }
                    
                    // 予約確定
                    logger.info("【STEP】processUrl - 予約確定を開始します");
                    if (!submitReservation(driver)) {
                        logger.error("【ERROR】processUrl - 予約確定に失敗しました。次の時間帯を試行します");
                        continue; // 次の時間帯を試行
                    }
                    logger.info("【STEP】processUrl - 予約確定が完了しました");
                    
                    logger.info("【SUCCESS】processUrl - 予約が成功しました！時間帯: {}", timeSlot);
                    return true;
                } else {
                    // この日付の予約が既に成功している場合はスキップ
                    if (dateSuccessFlag != null && dateSuccessFlag.get()) {
                        logger.info("【SKIP】processUrl - 日付 {} の予約が既に成功しているため、次の時間帯の監視をスキップします", targetDate);
                        break;
                    }
                    logger.warn("【WARN】processUrl - 時間帯 {} の監視に失敗しました。次の時間帯を試行します", timeSlot);
                }
            }
            
            logger.warn("すべての時間帯の監視に失敗しました");
            return false;
            
        } catch (UnreachableBrowserException e) {
            // ブラウザが予期せず終了した場合（リトライ可能）
            logger.warn("【WARN】processUrl - ブラウザとの通信が切断されました（リトライ可能）");
            logger.warn("【WARN】processUrl - URL: {}, 対象日付: {}, 時間帯: {}", url, targetDate, timeSlots);
            if (e.getCause() instanceof InterruptedException) {
                logger.warn("【WARN】processUrl - 原因: 処理が中断されました（リトライ可能）");
                Thread.currentThread().interrupt();
            }
            return false;
        } catch (Exception e) {
            // InterruptedExceptionが原因の場合は警告レベル
            if (e.getCause() instanceof InterruptedException || e instanceof InterruptedException) {
                logger.warn("【WARN】processUrl - URL処理が中断されました（リトライ可能）");
                logger.warn("【WARN】processUrl - URL: {}, 対象日付: {}, 時間帯: {}", url, targetDate, timeSlots);
                Thread.currentThread().interrupt();
            } else {
                logger.error("【ERROR】processUrl - URL処理中にエラーが発生しました", e);
                logger.error("【ERROR】processUrl - URL: {}, 対象日付: {}, 時間帯: {}", url, targetDate, timeSlots);
                logger.error("【ERROR】processUrl - エラーメッセージ: {}", e.getMessage());
                logger.error("【ERROR】processUrl - エラークラス: {}", e.getClass().getName());
                if (e.getCause() != null) {
                    logger.error("【ERROR】processUrl - 原因: {}", e.getCause().getMessage());
                }
            }
            return false;
        }
    }
    
    /**
     * URLに日付パラメータを追加
     * @param url 元のURL
     * @param targetDate 対象日付
     * @return 日付パラメータが追加されたURL
     */
    private String addDateParameterToUrl(String url, LocalDate targetDate) {
        if (url == null || targetDate == null) {
            return url;
        }
        
        try {
            // 日付をYYYY-MM-DD形式にフォーマット
            String dateStr = targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // StringBuilderを使用して文字列操作を最適化
            StringBuilder urlBuilder = new StringBuilder(url.length() + 20); // 予めサイズを確保
            urlBuilder.append(url);
            
            int dateParamIndex = url.indexOf(DATE_PARAM_PREFIX);
            if (dateParamIndex != -1) {
                // 既存のdateパラメータを置き換え
                int startIndex = dateParamIndex;
                int endIndex = url.indexOf("&", startIndex);
                if (endIndex == -1) {
                    endIndex = url.length();
                }
                urlBuilder.replace(startIndex, endIndex, DATE_PARAM_PREFIX + dateStr);
                logger.debug("既存のdateパラメータを置き換えました");
            } else {
                // dateパラメータがない場合は追加
                char separator = url.contains("?") ? '&' : '?';
                urlBuilder.append(separator).append(DATE_PARAM_PREFIX).append(dateStr);
                logger.debug("dateパラメータを追加しました");
            }
            url = urlBuilder.toString();
        } catch (Exception e) {
            logger.warn("URLに日付パラメータを追加する際にエラーが発生しました: {}", e.getMessage());
        }
        
        return url;
    }
    
    /**
     * 時間帯を正規化（"19:0" -> "19:00"）
     * @param timeSlot 時間帯文字列
     * @return 正規化された時間帯
     */
    private String normalizeTimeSlot(String timeSlot) {
        if (timeSlot == null || timeSlot.isEmpty()) {
            return timeSlot;
        }
        // "19:0" -> "19:00" のように分の部分を2桁に正規化（最適化版）
        int colonIndex = timeSlot.indexOf(':');
        if (colonIndex == -1) {
            return timeSlot;
        }
        try {
            int hour = Integer.parseInt(timeSlot.substring(0, colonIndex));
            String minuteStr = colonIndex + 1 < timeSlot.length() ? timeSlot.substring(colonIndex + 1) : "0";
            int minute = Integer.parseInt(minuteStr);
            // StringBuilderを使用して文字列生成を最適化
            return new StringBuilder(5).append(hour).append(':')
                .append(minute < 10 ? "0" : "").append(minute).toString();
        } catch (NumberFormatException e) {
            return timeSlot; // パースに失敗した場合は元の文字列を返す
        }
    }
    
    /**
     * ログインポップアップを処理
     * @param driver WebDriver
     * @return 処理が成功した場合true（ポップアップが存在しない場合もtrue）
     */
    public boolean handleLoginPopup(WebDriver driver) {
        logger.info("【STEP】handleLoginPopup - ログインポップアップの存在を確認しています...");
        logger.info("【STEP】handleLoginPopup - 現在のURL: {}", driver.getCurrentUrl());
        
        try {
            WebDriverWait wait = createWebDriverWait(driver);
            
            // 複数のセレクタパターンを試行
            String[] usernameSelectors = {
                "input[type='text']",
                "input[name*='user' i]",
                "input[name*='login' i]",
                "input[id*='user' i]",
                "input[id*='login' i]",
                "input[placeholder*='ユーザー' i]",
                "input[placeholder*='username' i]",
                "input[type='email']"
            };
            
            String[] passwordSelectors = {
                "input[type='password']",
                "input[name*='pass' i]",
                "input[id*='pass' i]",
                "input[placeholder*='パスワード' i]",
                "input[placeholder*='password' i]"
            };
            
            String[] submitSelectors = {
                "button[type='submit']",
                "input[type='submit']",
                "button:contains('ログイン')",
                "button:contains('Login')",
                "button:contains('送信')",
                "button:contains('Submit')"
            };
            
            // ユーザー名フィールドを探す
            WebElement usernameField = null;
            for (String selector : usernameSelectors) {
                try {
                    logger.debug("【STEP】handleLoginPopup - ユーザー名フィールドを検索中: {}", selector);
                    usernameField = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
                    if (usernameField != null && usernameField.isDisplayed()) {
                        logger.info("【STEP】handleLoginPopup - ユーザー名フィールドを見つけました: {}", selector);
                        break;
                    }
                } catch (TimeoutException | NoSuchElementException e) {
                    logger.debug(STEP_SELECTOR_NOT_FOUND, selector);
                    continue;
                }
            }
            
            // パスワードフィールドを探す
            WebElement passwordField = null;
            if (usernameField != null) {
                for (String selector : passwordSelectors) {
                    try {
                        logger.debug("【STEP】handleLoginPopup - パスワードフィールドを検索中: {}", selector);
                        passwordField = wait.until(
                            ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
                        if (passwordField != null && passwordField.isDisplayed()) {
                            logger.info("【STEP】handleLoginPopup - パスワードフィールドを見つけました: {}", selector);
                            break;
                        }
                    } catch (TimeoutException | NoSuchElementException e) {
                        logger.debug(STEP_SELECTOR_NOT_FOUND, selector);
                        continue;
                    }
                }
            }
            
            // 送信ボタンを探す
            WebElement submitButton = null;
            if (usernameField != null && passwordField != null) {
                for (String selector : submitSelectors) {
                    try {
                        logger.debug("【STEP】handleLoginPopup - 送信ボタンを検索中: {}", selector);
                        // CSSセレクタで:containsは使えないので、XPathを試行
                        if (selector.contains(":contains")) {
                            String text = selector.substring(selector.indexOf("'") + 1, selector.lastIndexOf("'"));
                            submitButton = wait.until(
                                ExpectedConditions.elementToBeClickable(
                                    By.xpath(BUTTON_TEXT_PREFIX + text + "')]")));
                        } else {
                            submitButton = wait.until(
                                ExpectedConditions.elementToBeClickable(By.cssSelector(selector)));
                        }
                        if (submitButton != null && submitButton.isDisplayed()) {
                            logger.info("【STEP】handleLoginPopup - 送信ボタンを見つけました: {}", selector);
                            break;
                        }
                    } catch (TimeoutException | NoSuchElementException e) {
                        logger.debug(STEP_SELECTOR_NOT_FOUND, selector);
                        continue;
                    }
                }
            }
            
            // すべての要素が見つかった場合のみログイン処理を実行
            if (usernameField != null && passwordField != null && submitButton != null) {
                logger.info("【STEP】handleLoginPopup - ログインポップアップを検出しました。ログイン処理を実行します");
                
                try {
                    usernameField.clear();
                    usernameField.sendKeys(config.getUsername());
                    logger.info("【STEP】handleLoginPopup - ユーザー名を入力しました");
                    
                    passwordField.clear();
                    passwordField.sendKeys(config.getPassword());
                    logger.info("【STEP】handleLoginPopup - パスワードを入力しました");
                    
                    submitButton.click();
                    logger.debug("【STEP】handleLoginPopup - ログインボタンをクリックしました");
                    
                    // ログイン成功を待機（カレンダー画面が表示されるまで）
                    try {
                        WebDriverWait loginWait = createWebDriverWait(driver);
                        // ログインポップアップが消えるまで待機
                        loginWait.until(ExpectedConditions.invisibilityOfElementLocated(
                            By.xpath("//input[@type='text' or @type='email'] | //input[@type='password']")
                        ));
                    } catch (TimeoutException e) {
                        logger.debug("ログイン後の待機タイムアウト（続行します）");
                    }
                    
                    logger.debug("【SUCCESS】handleLoginPopup - ログイン処理が完了しました");
                    return true;
                } catch (Exception e) {
                    logger.error("【ERROR】handleLoginPopup - ログイン入力中にエラーが発生しました", e);
                    logErrorWithUrl("handleLoginPopup", driver, "エラーメッセージ: " + e.getMessage());
                    return false;
                }
            } else {
                // ポップアップが存在しない場合は正常
                logger.info("【INFO】handleLoginPopup - ログインポップアップは存在しません。次の処理に進みます");
                logger.debug("【DEBUG】handleLoginPopup - 検索結果: usernameField={}, passwordField={}, submitButton={}", 
                    usernameField != null, passwordField != null, submitButton != null);
                return true;
            }
            
        } catch (TimeoutException e) {
            // ポップアップが存在しない場合は正常
            logger.info("【INFO】handleLoginPopup - ログインポップアップは存在しません（タイムアウト）。次の処理に進みます");
            logger.debug("【DEBUG】handleLoginPopup - タイムアウト: {}", e.getMessage());
            return true;
        } catch (NoSuchElementException e) {
            // 要素が見つからない場合は正常（ポップアップが存在しない）
            logger.info("【INFO】handleLoginPopup - ログインポップアップは存在しません（要素未検出）。次の処理に進みます");
            logger.debug("【DEBUG】handleLoginPopup - NoSuchElementException: {}", e.getMessage());
            return true;
        } catch (Exception e) {
            logger.error("【ERROR】handleLoginPopup - ログインポップアップ処理中に予期しないエラーが発生しました", e);
            logger.error("【ERROR】handleLoginPopup - エラーメッセージ: {}", e.getMessage());
            logErrorWithUrlAndTitle("handleLoginPopup", driver, "エラークラス: " + e.getClass().getName());
            if (e.getCause() != null) {
                logger.error("【ERROR】handleLoginPopup - 原因: {}", e.getCause().getMessage());
            }
            // 予期しないエラーでも、処理を続行できるようにtrueを返す
            return true;
        }
    }
    
    /**
     * カレンダーから日付を選択
     * @param driver WebDriver
     * @param targetDate 選択する日付
     * @return 選択が成功した場合true
     */
    public boolean selectDate(WebDriver driver, LocalDate targetDate) {
        logger.info("日付を選択します: {}", targetDate);
        
        try {
            // 日付のフォーマット（複数のパターンを試行）
            String[] dateFormats = {
                targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                targetDate.format(DateTimeFormatter.ofPattern("M/d")),
                targetDate.format(DateTimeFormatter.ofPattern("MM/dd")),
                String.valueOf(targetDate.getDayOfMonth())
            };
            
            WebDriverWait wait = createWebDriverWait(driver, config.getTimeoutSeconds());
            
            // カレンダー要素を探す（複数のセレクタを試行）
            WebElement dateElement = null;
            for (String dateFormat : dateFormats) {
                try {
                    // 日付ボタンを探す（複数のセレクタを試行）
                    String[] selectors = {
                        "//button[contains(text(), '" + dateFormat + "')]",
                        "//a[contains(text(), '" + dateFormat + "')]",
                        "//div[contains(text(), '" + dateFormat + "')]",
                        "//td[contains(text(), '" + dateFormat + "')]",
                        "//span[contains(text(), '" + dateFormat + "')]"
                    };
                    
                    for (String selector : selectors) {
                        try {
                            dateElement = wait.until(
                                ExpectedConditions.elementToBeClickable(By.xpath(selector)));
                            if (dateElement != null) {
                                break;
                            }
                        } catch (TimeoutException e) {
                            // 次のセレクタを試行
                            continue;
                        }
                    }
                    
                    if (dateElement != null) {
                        break;
                    }
                } catch (Exception e) {
                    // 次のフォーマットを試行
                    continue;
                }
            }
            
            if (dateElement == null) {
                logErrorWithUrlAndTitle("selectDate", driver, 
                    String.format("日付要素が見つかりませんでした: %s, 試行した日付フォーマット数: %d", targetDate, dateFormats.length));
                return false;
            }
            
            // 日付をクリック
            logger.debug("日付要素をクリックします: {}", targetDate);
            try {
                // JavaScriptでクリックを試行（より確実）
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView(true);", dateElement);
                // 短い待機（要素が表示されるまで）
                try {
                    WebDriverWait dateWait = createWebDriverWait(driver, Duration.ofMillis(500));
                    dateWait.until(ExpectedConditions.elementToBeClickable(dateElement));
                } catch (TimeoutException e) {
                    logger.debug("日付要素のクリック可能状態の待機タイムアウト（続行します）");
                }
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "arguments[0].click();", dateElement);
                logger.debug("日付をクリックしました（JavaScript）: {}", targetDate);
            } catch (Exception jsEx) {
                // JavaScriptクリックが失敗した場合は通常のクリックを試行
                logger.debug("JavaScriptクリックに失敗、通常のクリックを試行: {}", jsEx.getMessage());
                dateElement.click();
                logger.info("日付をクリックしました（通常）: {}", targetDate);
            }
            
            // タイムスロット一覧が表示されるまで待機
            logger.debug("タイムスロット一覧の表示を待機中...");
            try {
                WebDriverWait timeSlotWait = createWebDriverWait(driver);
                timeSlotWait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath(TIME_SLOT_BUTTON_XPATH)
                ));
            } catch (TimeoutException e) {
                logger.debug("タイムスロット一覧の表示待機タイムアウト（続行します）");
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("【ERROR】selectDate - 日付選択中にエラーが発生しました: {}", targetDate, e);
            String causeMsg = e.getCause() != null ? String.format(ERROR_CAUSE_FORMAT, e.getCause().getMessage()) : null;
            logErrorWithUrlAndTitle("selectDate", driver, 
                String.format(ERROR_MESSAGE_FORMAT, e.getMessage(), e.getClass().getName(), 
                    causeMsg != null ? ", " + causeMsg : ""));
            return false;
        }
    }
    
    /**
     * 利用可能なタイムスロットを取得
     * @param driver WebDriver
     * @return 利用可能なタイムスロットのリスト
     */
    public List<String> getAvailableTimeSlots(WebDriver driver) {
        List<String> availableSlots = new ArrayList<>();
        logger.info("利用可能なタイムスロットを取得します");
        
        try {
            // タイムスロット要素を探す（複数のセレクタを試行）
            String[] selectors = {
                // Material-UI (MUI) ボタン対応
                "//button[contains(@class, 'MuiButton')]//div[contains(text(), ':')]",
                "//button[contains(@class, 'MuiButtonBase')]//div[contains(text(), ':')]",
                "//button[.//div[contains(text(), ':')]]",
                // 通常のボタン
                "//button[contains(@class, 'time') or contains(@class, 'slot')]",
                "//a[contains(@class, 'time') or contains(@class, 'slot')]",
                "//div[contains(@class, 'time') or contains(@class, 'slot')]",
                "//span[contains(@class, 'time') or contains(@class, 'slot')]",
                "//button[contains(text(), ':')]",
                "//a[contains(text(), ':')]",
                "//div[contains(text(), ':')]",
                "//span[contains(text(), ':')]",
                "//*[contains(@class, 'time-slot')]",
                "//*[contains(@class, 'timeslot')]",
                "//*[contains(@class, 'timeSlot')]"
            };
            
            Set<String> foundSlots = new HashSet<>();
            
                    for (String selector : selectors) {
                try {
                    List<WebElement> elements = driver.findElements(By.xpath(selector));
                    for (WebElement element : elements) {
                        try {
                            // MUIボタンの場合、親要素（button）を取得
                            WebElement buttonElement = element;
                            if (selector.contains("//div[contains(text(), ':')]")) {
                                // div要素の場合は親のbutton要素を取得
                                try {
                                    buttonElement = element.findElement(By.xpath("./ancestor::button[1]"));
                                } catch (Exception e) {
                                    // 親要素が見つからない場合はdiv要素自体を使用
                                    buttonElement = element;
                                }
                            }
                            
                            String text = element.getText().trim();
                            // 時間形式（HH:MM）を抽出
                            if (text.matches(".*\\d{1,2}:\\d{2}.*")) {
                                // 時間部分を抽出
                                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{1,2}:\\d{2})");
                                java.util.regex.Matcher matcher = pattern.matcher(text);
                                if (matcher.find()) {
                                    String timeSlot = matcher.group(1);
                                    
                                    // ボタンが有効か確認（非表示でも有効な場合がある）
                                    boolean isEnabled = buttonElement.isEnabled();
                                    String disabled = buttonElement.getAttribute(ATTR_DISABLED);
                                    String style = buttonElement.getAttribute("style");
                                    String className = buttonElement.getAttribute(ATTR_CLASS);
                                    
                                    // MUIボタンの場合、disabledクラスやaria-disabled属性も確認
                                    boolean isMuiDisabled = className != null && 
                                                          (className.contains(CLASS_MUI_DISABLED) || 
                                                           className.contains(ATTR_DISABLED));
                                    String ariaDisabled = buttonElement.getAttribute(ATTR_ARIA_DISABLED);
                                    
                                    // 非表示でも予約可能な場合があるため、disabled属性とクラス名で判断
                                    boolean isAvailable = isEnabled && 
                                                         !"true".equals(disabled) &&
                                                         !ATTR_DISABLED.equals(disabled) &&
                                                         !isMuiDisabled &&
                                                         !"true".equals(ariaDisabled) &&
                                                         (style == null || !style.contains("display: none")) &&
                                                         buttonElement.isDisplayed();
                                    
                                    if (isAvailable || foundSlots.isEmpty()) {
                                        // 最初の検索時はすべて追加、その後は有効なもののみ
                                        foundSlots.add(timeSlot);
                                        if (isAvailable) {
                                            availableSlots.add(timeSlot);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // 個別の要素の処理でエラーが発生しても続行
                            continue;
                        }
                    }
                } catch (Exception e) {
                    // 次のセレクタを試行
                    continue;
                }
            }
            
            // 見つかったすべてのスロットをソート
            availableSlots.sort(String::compareTo);
            
            logger.info("利用可能なタイムスロット数: {}", availableSlots.size());
            for (String slot : availableSlots) {
                logger.info("  - {}", slot);
            }
            
            // 非表示だが存在するスロットもログに記録
            if (!foundSlots.isEmpty() && foundSlots.size() > availableSlots.size()) {
                logger.info("非表示だが存在するタイムスロット:");
                for (String slot : foundSlots) {
                    if (!availableSlots.contains(slot)) {
                        logger.info("  - {} (非表示)", slot);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("【ERROR】getAvailableTimeSlots - タイムスロット取得中にエラーが発生しました", e);
            String causeMsg = e.getCause() != null ? String.format(ERROR_CAUSE_FORMAT, e.getCause().getMessage()) : null;
            logErrorWithUrlAndTitle("getAvailableTimeSlots", driver, 
                String.format(ERROR_MESSAGE_FORMAT, e.getMessage(), e.getClass().getName(), 
                    causeMsg != null ? ", " + causeMsg : ""));
        }
        
        return availableSlots;
    }
    
    /**
     * タイムスロットを監視してクリック
     * @param driver WebDriver
     * @param timeSlot 対象のタイムスロット（例: "20:25"）
     * @return クリックが成功した場合true
     */
    public boolean monitorTimeSlot(WebDriver driver, String timeSlot) {
        return monitorTimeSlot(driver, timeSlot, null);
    }
    
    /**
     * タイムスロットを監視してクリック（日付指定版）
     * @param driver WebDriver
     * @param timeSlot 対象のタイムスロット（例: "20:25"）
     * @param targetDate 対象日付（リフレッシュ後に再選択するため）
     * @return クリックが成功した場合true
     */
    public boolean monitorTimeSlot(WebDriver driver, String timeSlot, LocalDate targetDate) {
        return monitorTimeSlot(driver, timeSlot, targetDate, null);
    }
    
    /**
     * タイムスロットを監視してクリック（日付指定版、日付成功フラグ付き）
     * @param driver WebDriver
     * @param timeSlot 対象のタイムスロット（例: "20:25"）
     * @param targetDate 対象日付（リフレッシュ後に再選択するため）
     * @param dateSuccessFlag 日付の成功フラグ（その日の予約が成功したらtrueになる。nullの場合はチェックしない）
     * @return クリックが成功した場合true
     */
    public boolean monitorTimeSlot(WebDriver driver, String timeSlot, LocalDate targetDate, AtomicBoolean dateSuccessFlag) {
            logger.debug("タイムスロットを監視します: {} (日付: {})", timeSlot, targetDate);
            logger.debug("予約が解放されたら即座に予約を実行します");
        
        final boolean[] success = {false};
        final String[] previousPageHash = {null}; // 前回のページハッシュを保持
        final boolean[] wasDisabled = {true}; // 前回のボタン状態（無効だったか）
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        try {
            // 監視間隔を設定（設定値を使用、ただし最小1秒）
            int monitoringInterval = Math.max(MIN_MONITORING_INTERVAL_SECONDS, config.getMonitoringIntervalSeconds());
            logger.debug("監視間隔: {}秒", monitoringInterval);
            
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    // この日付の予約が既に成功している場合は監視を停止
                    if (dateSuccessFlag != null && dateSuccessFlag.get()) {
                        logger.info("【STOP】monitorTimeSlot - 日付 {} の予約が既に成功しているため、監視を停止します: 時間帯={}", targetDate, timeSlot);
                        scheduler.shutdown();
                        return;
                    }
                    
                    // 監視時間内かどうかをチェック（日本時間）
                    if (!config.isWithinMonitoringHours()) {
                        long secondsUntilStart = config.getSecondsUntilMonitoringStart();
                        if (secondsUntilStart > 0) {
                            logger.info("監視時間外です（日本時間 {}時〜{}時）。{}秒後に監視を再開します", 
                                config.getMonitoringStartHour(), config.getMonitoringEndHour(), secondsUntilStart);
                        } else {
                            logger.debug("監視時間外です（日本時間 {}時〜{}時）", 
                                config.getMonitoringStartHour(), config.getMonitoringEndHour());
                        }
                        return; // 監視時間外の場合は処理をスキップ
                    }
                    
                    // ページソースのハッシュを取得して更新を検知
                    String currentPageSource = driver.getPageSource();
                    String currentHash = String.valueOf(currentPageSource.hashCode());
                    
                    // 前回のハッシュと比較
                    boolean pageUpdated = previousPageHash[0] == null || 
                                        !previousPageHash[0].equals(currentHash);
                    
                        if (pageUpdated && previousPageHash[0] != null) {
                        logger.debug("ページが更新されました（ハッシュ変更を検知）");
                    }
                    previousPageHash[0] = currentHash;
                    
                    // ページを更新（定期的なリフレッシュ）
                    // リフレッシュ後、日付パラメータ付きURLで再アクセス
                    if (targetDate != null) {
                        // 現在のURLを取得（ベースURLを取得）
                        String currentUrl = driver.getCurrentUrl();
                        // ベースURLを取得（クエリパラメータを除く）
                        String baseUrl = currentUrl.split("\\?")[0];
                        // 日付パラメータ付きURLを作成
                        String urlWithDate = addDateParameterToUrl(baseUrl, targetDate);
                        
                        // URLが変更されている場合は再アクセス
                        if (!currentUrl.equals(urlWithDate)) {
                            logger.debug("ページリフレッシュ後、日付パラメータ付きURLで再アクセス: {}", urlWithDate);
                            driver.get(urlWithDate);
                            // ページ読み込み待機（WebDriverWaitで最適化）
                            try {
                                WebDriverWait wait = createWebDriverWait(driver);
                                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
                            } catch (TimeoutException e) {
                                logger.debug("ページ読み込みタイムアウト（続行します）");
                            }
                        } else {
                            // 既に正しいURLの場合はリフレッシュのみ
                            driver.navigate().refresh();
                            logger.debug("ページを更新しました（日付パラメータは既に含まれています）");
                            try {
                                WebDriverWait wait = createWebDriverWait(driver, 3);
                                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
                            } catch (TimeoutException e) {
                                logger.debug("ページリフレッシュ後の読み込みタイムアウト（続行します）");
                            }
                        }
                        // タイムスロット表示待機（WebDriverWaitで最適化）
                        try {
                            WebDriverWait wait = createWebDriverWait(driver);
                            wait.until(ExpectedConditions.presenceOfElementLocated(
                                By.xpath(TIME_SLOT_BUTTON_XPATH)
                            ));
                        } catch (TimeoutException e) {
                            logger.debug("タイムスロットボタンの表示待機タイムアウト（続行します）");
                        }
                        logger.debug("日付パラメータ確認完了。時間ボタンを探します");
                    } else {
                        logger.warn("targetDateがnullのため、日付パラメータを追加できません");
                        driver.navigate().refresh();
                        try {
                            WebDriverWait wait = createWebDriverWait(driver, 3);
                            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
                        } catch (TimeoutException e) {
                            logger.debug("ページリフレッシュ後の読み込みタイムアウト（続行します）");
                        }
                    }
                    
                    // タイムスロットボタンを探す（複数のセレクタと時間形式を試行）
                    // 時間形式の正規化（"19:00" と "19:0" の両方に対応）
                    String normalizedTimeSlot = normalizeTimeSlot(timeSlot);
                    String[] timeVariations = {
                        timeSlot,           // "19:00"
                        normalizedTimeSlot, // "19:0" -> "19:00"
                        timeSlot.replace(":", "："), // 全角コロン
                        timeSlot.replace(":0", ":")  // "19:00" -> "19:0"
                    };
                    
                    WebElement timeSlotButton = null;
                    
                    // 各時間形式バリエーションを試行
                    for (String timeVar : timeVariations) {
                        String[] selectors = {
                            // Material-UI (MUI) ボタン対応
                            "//button[contains(@class, 'MuiButton')]//div[normalize-space(text())='" + timeVar + "']",
                            "//button[contains(@class, 'MuiButtonBase')]//div[normalize-space(text())='" + timeVar + "']",
                            "//button[.//div[normalize-space(text())='" + timeVar + "']]",
                            "//button[contains(@class, 'MuiButton')]//div[contains(text(), '" + timeVar + "')]",
                            "//button[contains(@class, 'MuiButtonBase')]//div[contains(text(), '" + timeVar + "')]",
                            // 通常のボタン
                            "//button[contains(text(), '" + timeVar + "')]",
                            "//a[contains(text(), '" + timeVar + "')]",
                            "//div[contains(text(), '" + timeVar + "')]",
                            "//span[contains(text(), '" + timeVar + "')]",
                            "//button[normalize-space(text())='" + timeVar + "']",
                            "//a[normalize-space(text())='" + timeVar + "']",
                            "//div[normalize-space(text())='" + timeVar + "']",
                            "//span[normalize-space(text())='" + timeVar + "']",
                            "//*[contains(@class, 'time') and contains(text(), '" + timeVar + "')]",
                            "//*[contains(@class, 'slot') and contains(text(), '" + timeVar + "')]"
                        };
                        
                        for (String selector : selectors) {
                            try {
                                List<WebElement> elements = driver.findElements(By.xpath(selector));
                                for (WebElement element : elements) {
                                    try {
                                        // MUIボタンの場合、親要素（button）を取得
                                        WebElement targetElement = element;
                                        if (selector.contains("//div[") && !selector.contains("//button[")) {
                                            // div要素の場合は親のbutton要素を取得
                                            try {
                                                targetElement = element.findElement(By.xpath("./ancestor::button[1]"));
                                            } catch (Exception e) {
                                                // 親要素が見つからない場合はdiv要素自体を使用
                                                targetElement = element;
                                            }
                                        }
                                        
                                        // 要素が表示されているか確認
                                        if (!targetElement.isDisplayed()) {
                                            continue;
                                        }
                                        
                                        // ボタンが有効か確認（より柔軟な判定）
                                        boolean isClickable = targetElement.isEnabled() && 
                                                             !"true".equals(targetElement.getAttribute(ATTR_DISABLED)) &&
                                                             !ATTR_DISABLED.equals(targetElement.getAttribute(ATTR_DISABLED)) &&
                                                             targetElement.isDisplayed();
                                        
                                        // MUIボタンの場合、disabledクラスやaria-disabled属性も確認
                                        String className = targetElement.getAttribute(ATTR_CLASS);
                                        boolean isMuiDisabled = className != null && 
                                                              (className.contains(CLASS_MUI_DISABLED) || 
                                                               className.contains(ATTR_DISABLED));
                                        String ariaDisabled = targetElement.getAttribute(ATTR_ARIA_DISABLED);
                                        
                                        if (isMuiDisabled || "true".equals(ariaDisabled)) {
                                            isClickable = false;
                                        }
                                        
                                        if (isClickable) {
                                            timeSlotButton = targetElement; // button要素を使用
                                            logger.debug("タイムスロットボタンを見つけました: {} (セレクタ: {})", timeVar, selector);
                                            break;
                                        }
                                    } catch (Exception e) {
                                        // 個別の要素の処理でエラーが発生しても続行
                                        continue;
                                    }
                                }
                                if (timeSlotButton != null) {
                                    break;
                                }
                            } catch (Exception e) {
                                // 次のセレクタを試行
                                continue;
                            }
                        }
                        if (timeSlotButton != null) {
                            break;
                        }
                    }
                    
                    // ボタンの状態変化を検知（無効→有効）
                    if (timeSlotButton != null) {
                        // MUIボタンの場合、disabledクラスやaria-disabled属性も確認
                        String className = timeSlotButton.getAttribute(ATTR_CLASS);
                        boolean isMuiDisabled = className != null && 
                                              (className.contains(CLASS_MUI_DISABLED) || 
                                               className.contains(ATTR_DISABLED));
                        String ariaDisabled = timeSlotButton.getAttribute(ATTR_ARIA_DISABLED);
                        
                        boolean isCurrentlyEnabled = timeSlotButton.isEnabled() && 
                                                    !"true".equals(timeSlotButton.getAttribute(ATTR_DISABLED)) &&
                                                    !ATTR_DISABLED.equals(timeSlotButton.getAttribute(ATTR_DISABLED)) &&
                                                    !isMuiDisabled &&
                                                    !"true".equals(ariaDisabled) &&
                                                    timeSlotButton.isDisplayed();
                        
                        // 無効から有効に変わった場合（予約が解放された）
                        if (wasDisabled[0] && isCurrentlyEnabled) {
                            logger.info("【予約解放検知】タイムスロット {} が有効になりました！即座に予約を実行します", timeSlot);
                        }
                        wasDisabled[0] = !isCurrentlyEnabled;
                    }
                    
                    if (timeSlotButton == null) {
                        logger.debug("タイムスロットボタンが見つかりません: {} (試行した形式: {})", timeSlot, String.join(", ", timeVariations));
                        wasDisabled[0] = true; // 見つからない場合は無効として扱う
                        return;
                    }
                    
                    // ボタンが押せる状態か再確認
                    try {
                        // MUIボタンの場合、disabledクラスやaria-disabled属性も確認
                        String className = timeSlotButton.getAttribute(ATTR_CLASS);
                        boolean isMuiDisabled = className != null && 
                                              (className.contains(CLASS_MUI_DISABLED) || 
                                               className.contains(ATTR_DISABLED));
                        String ariaDisabled = timeSlotButton.getAttribute(ATTR_ARIA_DISABLED);
                        
                        boolean isEnabled = timeSlotButton.isEnabled() && 
                                          !"true".equals(timeSlotButton.getAttribute(ATTR_DISABLED)) &&
                                          !ATTR_DISABLED.equals(timeSlotButton.getAttribute(ATTR_DISABLED)) &&
                                          !isMuiDisabled &&
                                          !"true".equals(ariaDisabled) &&
                                          timeSlotButton.isDisplayed();
                        
                        if (!isEnabled) {
                            logger.debug("タイムスロットボタンは無効または非表示です: {}", timeSlot);
                            wasDisabled[0] = true;
                            return;
                        }
                        
                        // 【重要】予約が解放されたら即座にクリック
                        logger.info("【予約実行】タイムスロット {} が有効です。即座にクリックします", timeSlot);
                        
                        // JavaScriptでクリック可能か確認（より確実なクリック）
                        try {
                            // スクロールして要素を表示
                            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                                "arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});", timeSlotButton);
                            // 短い待機（要素が表示されるまで）
                            try {
                                WebDriverWait wait = createWebDriverWait(driver, Duration.ofMillis(DEFAULT_CLICK_WAIT_MS * 10L));
                                wait.until(ExpectedConditions.elementToBeClickable(timeSlotButton));
                            } catch (TimeoutException e) {
                                logger.debug("要素のクリック可能状態の待機タイムアウト（続行します）");
                            }
                            
                            // JavaScriptでクリックを試行（より確実）
                            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                                "arguments[0].click();", timeSlotButton);
                            logger.info("【成功】タイムスロットボタンをクリックしました（JavaScript）: {}", timeSlot);
                        } catch (Exception jsEx) {
                            // JavaScriptクリックが失敗した場合は通常のクリックを試行
                            logger.debug("JavaScriptクリックに失敗、通常のクリックを試行: {}", jsEx.getMessage());
                            try {
                                timeSlotButton.click();
                                logger.info("【成功】タイムスロットボタンをクリックしました（通常）: {}", timeSlot);
                            } catch (Exception clickEx) {
                                logger.warn("クリックに失敗しました: {}", clickEx.getMessage());
                                return; // クリック失敗時は次回再試行
                            }
                        }
                        
                        success[0] = true;
                        scheduler.shutdown();
                        logger.info("【予約成功】タイムスロット {} の予約処理を開始しました", timeSlot);
                    } catch (Exception e) {
                        logger.warn("タイムスロットボタンのクリックに失敗しました: {}", e.getMessage());
                    }
                    
                } catch (NoSuchElementException e) {
                    // ボタンが見つからない場合は次回の監視で再試行
                    logger.debug("タイムスロットボタンが見つかりません（次回再試行）: {}", timeSlot);
                } catch (Exception e) {
                    logger.error("【ERROR】monitorTimeSlot - タイムスロット監視中にエラーが発生しました", e);
                    logger.error("【ERROR】monitorTimeSlot - タイムスロット: {}, 対象日付: {}", timeSlot, targetDate);
                    String causeMsg = e.getCause() != null ? String.format(ERROR_CAUSE_FORMAT, e.getCause().getMessage()) : null;
                    logErrorWithUrlAndTitle("monitorTimeSlot", driver, 
                        String.format(ERROR_MESSAGE_FORMAT, e.getMessage(), e.getClass().getName(), 
                            causeMsg != null ? ", " + causeMsg : ""));
                }
               }, 0, monitoringInterval, TimeUnit.SECONDS);
            
            // 最大30分待機（タイムアウト）
            scheduler.awaitTermination(30, TimeUnit.MINUTES);
            
            if (success[0]) {
                logger.info("タイムスロットのクリックが成功しました: {}", timeSlot);
                // ページ遷移を待機（WebDriverWaitで最適化）
                try {
                    WebDriverWait wait = createWebDriverWait(driver);
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
                } catch (TimeoutException e) {
                    logger.debug("ページ遷移後の読み込みタイムアウト（続行します）");
                }
                return true;
            } else {
                logger.warn("タイムスロットの監視がタイムアウトしました: {}", timeSlot);
                return false;
            }
            
        } catch (InterruptedException e) {
            logger.error("【ERROR】monitorTimeSlot - タイムスロット監視が中断されました", e);
            logger.error("【ERROR】monitorTimeSlot - タイムスロット: {}, 対象日付: {}", timeSlot, targetDate);
            logger.error("【ERROR】monitorTimeSlot - エラーメッセージ: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    /**
     * 予約フォームに入力
     * @param driver WebDriver
     * @param name 名前
     * @param email メールアドレス
     * @return 入力が成功した場合true
     */
    public boolean fillReservationForm(WebDriver driver, String name, String email) {
        logger.info("予約フォームに入力します: name={}, email={}", name, email);
        
        try {
            WebDriverWait wait = createWebDriverWait(driver, config.getTimeoutSeconds());
            
            // 名前フィールドを探す（複数のセレクタを試行）
            String[] nameSelectors = {
                "//input[@name='name']",
                "//input[@placeholder*='名前' or @placeholder*='Name']",
                "//input[@type='text'][1]",
                "//input[@id*='name' or @id*='Name']"
            };
            
            WebElement nameField = null;
            for (String selector : nameSelectors) {
                try {
                    nameField = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(selector)));
                    if (nameField != null) {
                        break;
                    }
                } catch (TimeoutException e) {
                    continue;
                }
            }
            
            if (nameField == null) {
                logErrorWithUrlAndTitle("fillReservationForm", driver, 
                    String.format("名前フィールドが見つかりませんでした, 試行したセレクタ数: %d", nameSelectors.length));
                return false;
            }
            
            nameField.clear();
            nameField.sendKeys(name);
            logger.info("名前を入力しました: {}", name);
            
            // メールフィールドを探す（複数のセレクタを試行）
            String[] emailSelectors = {
                "//input[@type='email']",
                "//input[@name='email' or @name='Email']",
                "//input[@placeholder*='メール' or @placeholder*='Email']",
                "//input[@id*='email' or @id*='Email']"
            };
            
            WebElement emailField = null;
            for (String selector : emailSelectors) {
                try {
                    emailField = driver.findElement(By.xpath(selector));
                    if (emailField != null) {
                        break;
                    }
                } catch (NoSuchElementException e) {
                    continue;
                }
            }
            
            if (emailField == null) {
                logger.error("【ERROR】fillReservationForm - メールフィールドが見つかりませんでした");
                logger.error("【ERROR】fillReservationForm - 試行したセレクタ数: {}", emailSelectors.length);
                logErrorWithUrlAndTitle("fillReservationForm", driver, null);
                return false;
            }
            
            emailField.clear();
            emailField.sendKeys(email);
            logger.debug("メールアドレスを入力しました: {}", email);
            
            // フォーム入力完了を待機（短い待機）
            try {
                WebDriverWait formWait = createWebDriverWait(driver, 2);
                formWait.until(ExpectedConditions.attributeToBeNotEmpty(emailField, "value"));
            } catch (TimeoutException e) {
                logger.debug("メールアドレス入力の確認タイムアウト（続行します）");
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("【ERROR】fillReservationForm - 予約フォーム入力中にエラーが発生しました", e);
            logger.error("【ERROR】fillReservationForm - 名前: {}, メール: {}", name, email);
            logger.error("【ERROR】fillReservationForm - エラーメッセージ: {}", e.getMessage());
            logger.error("【ERROR】fillReservationForm - エラークラス: {}", e.getClass().getName());
            String causeMsg = e.getCause() != null ? String.format(ERROR_CAUSE_FORMAT, e.getCause().getMessage()) : null;
            logErrorWithUrl("fillReservationForm", driver, 
                String.format(ERROR_MESSAGE_FORMAT, e.getMessage(), e.getClass().getName(), 
                    causeMsg != null ? ", " + causeMsg : ""));
            return false;
        }
    }
    
    /**
     * 予約を確定
     * @param driver WebDriver
     * @return 確定が成功した場合true
     */
    public boolean submitReservation(WebDriver driver) {
        logger.info("予約を確定します");
        
        try {
            WebDriverWait wait = createWebDriverWait(driver, config.getTimeoutSeconds());
            
            // 確定ボタンを探す（複数のセレクタを試行）
            String[] submitSelectors = {
                "//button[@type='submit']",
                "//button[contains(text(), '確定') or contains(text(), '予約') or contains(text(), 'Submit')]",
                "//input[@type='submit']",
                "//button[@class*='submit' or @class*='confirm']"
            };
            
            WebElement submitButton = null;
            for (String selector : submitSelectors) {
                try {
                    submitButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(selector)));
                    if (submitButton != null) {
                        break;
                    }
                } catch (TimeoutException e) {
                    continue;
                }
            }
            
            if (submitButton == null) {
                logger.error("【ERROR】submitReservation - 確定ボタンが見つかりませんでした");
                logger.error("【ERROR】submitReservation - 試行したセレクタ数: {}", submitSelectors.length);
                logErrorWithUrlAndTitle("submitReservation", driver, null);
                return false;
            }
            
            submitButton.click();
            logger.info("確定ボタンをクリックしました");
            
            // 成功画面への遷移を待機（WebDriverWaitで最適化）
            try {
                WebDriverWait submitWait = createWebDriverWait(driver);
                // ページが更新されるまで待機（URLまたはタイトルの変更を検知）
                submitWait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("success"),
                    ExpectedConditions.urlContains("confirm"),
                    ExpectedConditions.titleContains("予約")
                ));
            } catch (TimeoutException e) {
                logger.debug("予約確定後の画面遷移待機タイムアウト（続行します）");
            }
            
            // 成功画面の確認（URLやタイトルの変更を確認）
            String currentUrl = driver.getCurrentUrl();
            String pageTitle = driver.getTitle();
            logger.debug("予約確定後のURL: {}, タイトル: {}", currentUrl, pageTitle);
            
            logger.info("予約確定が完了しました");
            return true;
            
        } catch (Exception e) {
            logger.error("【ERROR】submitReservation - 予約確定中にエラーが発生しました", e);
            logger.error("【ERROR】submitReservation - エラーメッセージ: {}", e.getMessage());
            logger.error("【ERROR】submitReservation - エラークラス: {}", e.getClass().getName());
            String causeMsg = e.getCause() != null ? String.format(ERROR_CAUSE_FORMAT, e.getCause().getMessage()) : null;
            logErrorWithUrlAndTitle("submitReservation", driver, 
                String.format(ERROR_MESSAGE_FORMAT, e.getMessage(), e.getClass().getName(), 
                    causeMsg != null ? ", " + causeMsg : ""));
            return false;
        }
    }
    
    /**
     * リトライ付きで処理を実行
     * @param process 実行する処理
     * @param maxRetries 最大リトライ回数
     * @return 処理が成功した場合true
     */
    public boolean processWithRetry(Supplier<Boolean> process, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                if (process.get()) {
                    return true;
                }
            } catch (Exception e) {
                logger.error("【ERROR】processWithRetry - 処理失敗 (試行 {}/{})", i + 1, maxRetries);
                logger.error("【ERROR】processWithRetry - エラーメッセージ: {}", e.getMessage());
                logger.error("【ERROR】processWithRetry - エラークラス: {}", e.getClass().getName());
                if (e.getCause() != null) {
                    logger.error("【ERROR】processWithRetry - 原因: {}", e.getCause().getMessage());
                }
                if (i < maxRetries - 1) {
                    logger.info("【RETRY】processWithRetry - 5秒後に再試行します...");
                    try {
                        // リトライ待機（Thread.sleepの代わりにTimeUnitを使用）
                        java.util.concurrent.TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ie) {
                        logger.error("【ERROR】processWithRetry - 再試行待機中に中断されました");
                        Thread.currentThread().interrupt();
                        return false;
                    }
                } else {
                    logger.error("【ERROR】processWithRetry - 最大リトライ回数に達しました。処理を終了します");
                }
            }
        }
        return false;
    }
    
    /**
     * ページ読み込みを待機（共通メソッド）
     */
    private void waitForPageLoad(WebDriver driver) {
        waitForPageLoad(driver, DEFAULT_WAIT_TIMEOUT);
    }
    
    /**
     * WebDriverWaitインスタンスを作成（共通メソッド）
     */
    private WebDriverWait createWebDriverWait(WebDriver driver) {
        return createWebDriverWait(driver, DEFAULT_WAIT_TIMEOUT);
    }
    
    /**
     * WebDriverWaitインスタンスを作成（タイムアウト指定版）
     */
    private WebDriverWait createWebDriverWait(WebDriver driver, Duration timeout) {
        return new WebDriverWait(driver, timeout);
    }
    
    /**
     * WebDriverWaitインスタンスを作成（秒数指定版）
     */
    private WebDriverWait createWebDriverWait(WebDriver driver, int timeoutSeconds) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
    }
    
    /**
     * ページ読み込みを待機（タイムアウト指定版）
     */
    private void waitForPageLoad(WebDriver driver, Duration timeout) {
        try {
            WebDriverWait wait = createWebDriverWait(driver, timeout);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        } catch (TimeoutException e) {
            logger.debug("ページ読み込みタイムアウト（続行します）");
        }
    }
    
    /**
     * タイムスロットボタンの表示を待機（共通メソッド）
     */
    private void waitForTimeSlotButtons(WebDriver driver) {
        try {
            WebDriverWait wait = createWebDriverWait(driver);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(TIME_SLOT_BUTTON_XPATH)));
        } catch (TimeoutException e) {
            logger.debug("タイムスロットボタンの表示待機タイムアウト（続行します）");
        }
    }
    
    /**
     * エラーログを出力（現在のURLとページタイトルを含む）
     * @param methodName メソッド名
     * @param driver WebDriver
     * @param message エラーメッセージ（オプション）
     */
    private void logErrorWithUrlAndTitle(String methodName, WebDriver driver, String message) {
        if (message != null && !message.isEmpty()) {
            logger.error("【ERROR】{} - {}", methodName, message);
        }
        try {
            logger.error("【ERROR】{} - 現在のURL: {}", methodName, driver.getCurrentUrl());
            logger.error("【ERROR】{} - ページタイトル: {}", methodName, driver.getTitle());
        } catch (Exception e) {
            logger.error("【ERROR】{} - URL/タイトル取得中にエラー: {}", methodName, e.getMessage());
        }
    }
    
    /**
     * エラーログを出力（現在のURLのみ）
     * @param methodName メソッド名
     * @param driver WebDriver
     * @param message エラーメッセージ（オプション）
     */
    private void logErrorWithUrl(String methodName, WebDriver driver, String message) {
        if (message != null && !message.isEmpty()) {
            logger.error("【ERROR】{} - {}", methodName, message);
        }
        try {
            logger.error("【ERROR】{} - 現在のURL: {}", methodName, driver.getCurrentUrl());
        } catch (Exception e) {
            logger.error("【ERROR】{} - URL取得中にエラー: {}", methodName, e.getMessage());
        }
    }
}

