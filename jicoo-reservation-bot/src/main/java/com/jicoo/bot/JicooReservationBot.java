package com.jicoo.bot;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Jicoo自動予約BOT メインクラス
 * アプリケーションのエントリーポイント
 */
public class JicooReservationBot {
    private static final Logger logger = LoggerFactory.getLogger(JicooReservationBot.class);
    
    private final Config config;
    private final ReservationService reservationService;
    private WebDriver driver;
    private ReservationCallback guiCallback;
    private volatile boolean isMonitoring = false; // 監視中フラグ
    private volatile boolean shouldStopMonitoring = false; // 監視停止フラグ
    private long lastActivityTime = System.currentTimeMillis(); // 最後の活動時間（スリープ検知用）
    
    /**
     * 予約結果のコールバックインターフェース
     */
    public interface ReservationCallback {
        void onReservationResult(LocalDate date, boolean success);
        
        /**
         * 予約結果のコールバック（時間帯付き）
         * デフォルト実装では時間帯なしのメソッドを呼び出す
         */
        default void onReservationResult(LocalDate date, boolean success, List<String> timeSlots) {
            onReservationResult(date, success);
        }
    }
    
    /**
     * GUIコールバックを設定
     */
    public void setReservationCallback(ReservationCallback callback) {
        this.guiCallback = callback;
    }
    
    /**
     * 現在のコールバックを取得
     */
    public ReservationCallback getReservationCallback() {
        return this.guiCallback;
    }
    
    public JicooReservationBot() {
        this.config = Config.getInstance();
        this.reservationService = new ReservationService(config);
    }
    
    /**
     * メインメソッド
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("Jicoo自動予約BOT 起動");
        logger.info("========================================");
        
        JicooReservationBot bot = new JicooReservationBot();
        
        try {
            bot.startMonitoring();
        } catch (Exception e) {
            logger.error("アプリケーション実行中にエラーが発生しました", e);
        } finally {
            bot.stopMonitoring();
        }
        
        logger.info("========================================");
        logger.info("Jicoo自動予約BOT 終了");
        logger.info("========================================");
    }
    
    /**
     * 監視を開始（デフォルト：1週間後の日付）
     */
    public void startMonitoring() {
        startMonitoring((Map<LocalDate, List<String>>) null);
    }
    
    /**
     * 監視を開始（指定日付リスト）
     * @param targetDates 予約対象日付のリスト（nullの場合は1週間後を使用）
     */
    public void startMonitoring(List<LocalDate> targetDates) {
        // 日付と時間帯のマッピングに変換（デフォルト時間を使用）
        Map<LocalDate, List<String>> datesWithTimeSlots = new HashMap<>(8); // 初期サイズを指定
        if (targetDates != null) {
            for (LocalDate date : targetDates) {
                datesWithTimeSlots.put(date, List.of(config.getTargetTime()));
            }
        }
        startMonitoring(datesWithTimeSlots);
    }
    
    /**
     * 監視を開始（日付と時間帯のマッピング）
     * @param datesWithTimeSlots 日付と時間帯のマッピング（nullの場合は1週間後を使用）
     */
    public void startMonitoring(Map<LocalDate, List<String>> datesWithTimeSlots) {
        logger.info("監視を開始します");
        isMonitoring = true;
        shouldStopMonitoring = false;
        
        // 監視時間内かどうかをチェック（日本時間）
        if (!config.isWithinMonitoringHours()) {
            long secondsUntilStart = config.getSecondsUntilMonitoringStart();
            logger.warn("監視時間外です（日本時間 {}時〜{}時）。{}秒後に監視を開始します", 
                config.getMonitoringStartHour(), config.getMonitoringEndHour(), secondsUntilStart);
            // 監視時間外でも開始は可能（監視ループ内で時間チェックを行う）
        } else {
            logger.info("監視時間内です（日本時間 {}時〜{}時）", 
                config.getMonitoringStartHour(), config.getMonitoringEndHour());
        }
        
        // URLリストを取得
        List<String> urls = config.getUrls();
        if (urls.isEmpty()) {
            logger.error("監視対象URLが設定されていません");
            return;
        }
        
        logger.info("監視対象URL数: {}", urls.size());
        for (int i = 0; i < urls.size(); i++) {
            logger.info("  {}: {}", i + 1, urls.get(i));
        }
        
        // 対象日付リストと時間帯マッピングを決定
        Map<LocalDate, List<String>> datesWithTimeSlotsMap;
        if (datesWithTimeSlots != null && !datesWithTimeSlots.isEmpty()) {
            datesWithTimeSlotsMap = datesWithTimeSlots;
            logger.debug("指定された日付数: {}", datesWithTimeSlotsMap.size());
            for (Map.Entry<LocalDate, List<String>> entry : datesWithTimeSlotsMap.entrySet()) {
                logger.debug("  対象日付: {}, 時間帯: {}", entry.getKey(), entry.getValue());
            }
        } else {
            // デフォルト：1週間後、デフォルト時間
            LocalDate defaultDate = config.getTargetDate();
            datesWithTimeSlotsMap = new HashMap<>(datesWithTimeSlots.size()); // 初期サイズを指定
            datesWithTimeSlotsMap.put(defaultDate, List.of(config.getTargetTime()));
            logger.debug("デフォルト日付を使用: {}, 時間帯: {}", defaultDate, config.getTargetTime());
        }
        
        List<LocalDate> datesToProcess = new ArrayList<>(datesWithTimeSlotsMap.keySet());
        
        // 並行処理用のExecutorService（URL数×日付数のスレッド）
        // スレッドプールサイズを最適化（CPUコア数に基づく）
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int threadCount = urls.size() * datesToProcess.size();
        int optimalThreadPoolSize = Math.min(Math.max(threadCount, 2), Math.max(availableProcessors * 2, 8));
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(optimalThreadPoolSize);
        logger.debug("スレッドプールサイズ: {} (利用可能CPU: {}, タスク数: {})", optimalThreadPoolSize, availableProcessors, threadCount);
        
        final java.util.concurrent.atomic.AtomicBoolean overallSuccess = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        // 日付ごとの成功フラグ（その日の予約が成功したら、その日の他のタスクを停止）
        final Map<LocalDate, java.util.concurrent.atomic.AtomicBoolean> dateSuccessMap = 
            new HashMap<>(datesToProcess.size()); // 初期サイズを指定
        for (LocalDate date : datesToProcess) {
            dateSuccessMap.put(date, new java.util.concurrent.atomic.AtomicBoolean(false));
        }
        
        // 各URL×日付の組み合わせを並行処理
        for (Map.Entry<LocalDate, List<String>> dateEntry : datesWithTimeSlotsMap.entrySet()) {
            LocalDate targetDate = dateEntry.getKey();
            List<String> timeSlots = dateEntry.getValue();
            
            for (String url : urls) {
                final LocalDate date = targetDate; // ラムダ式で使用するためfinal
                final List<String> finalTimeSlots = new ArrayList<>(timeSlots); // ラムダ式で使用するためfinal
                executor.submit(() -> {
                    // 失敗しても監視を継続する無限ループ
                    WebDriver urlDriver = null; // スコープを広げるため、ループの外で宣言
                    while (isMonitoring && !shouldStopMonitoring) {
                        try {
                            // この日付の予約が既に成功している場合はスキップ
                            if (dateSuccessMap.get(date).get()) {
                                logger.info("日付 {} の予約が既に成功しているため、このタスクを終了します: URL={}", date, url);
                                latch.countDown();
                                return;
                            }
                            
                            logger.info("========================================");
                            logger.info("監視開始: 日付={}, URL={}, 時間帯={}", date, url, finalTimeSlots);
                            logger.info("========================================");
                            
                            // このURL×日付の組み合わせ用に新しいWebDriverを作成（既に存在する場合は再作成しない）
                            if (urlDriver == null) {
                                urlDriver = DriverManager.createWebDriver(
                                    config.isHeadless(),
                                    config.getTimeoutSeconds(),
                                    config.getImplicitWaitSeconds()
                                );
                                
                                if (urlDriver == null) {
                                    logger.error("WebDriverの作成に失敗しました: 日付={}, URL={}", date, url);
                                    // WebDriver作成失敗時は1分待機してから再試行
                                    logger.info("1分後に再試行します...");
                                    try {
                                        Thread.sleep(60000);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        logger.warn("再試行待機中に中断されました");
                                        latch.countDown();
                                        return;
                                    }
                                    continue; // ループを継続
                                }
                            }
                            
                            try {
                                // スリープモード検知：システム時間の大きな変化をチェック
                                long currentTime = System.currentTimeMillis();
                                long timeDiff = currentTime - lastActivityTime;
                                if (timeDiff > 300000) { // 5分以上の時間差がある場合（スリープから復帰した可能性）
                                    logger.warn("【スリープ検知】システム時間の大きな変化を検知しました（{}分）。スリープから復帰した可能性があります", timeDiff / 60000);
                                    logger.warn("【スリープ検知】WebDriverを再作成して監視を再開します");
                                    // WebDriverをクリーンアップして再作成
                                    try {
                                        DriverManager.closeWebDriver(urlDriver);
                                    } catch (Exception e) {
                                        logger.debug("スリープ復帰時のWebDriverクリーンアップでエラー（無視します）: {}", e.getMessage());
                                    }
                                    urlDriver = null; // 再作成のためnullに設定
                                    lastActivityTime = currentTime;
                                } else {
                                    lastActivityTime = currentTime;
                                }
                                
                                // WebDriverが無効な場合は再作成
                                if (urlDriver == null) {
                                    logger.info("WebDriverを再作成します: 日付={}, URL={}", date, url);
                                    urlDriver = DriverManager.createWebDriver(
                                        config.isHeadless(),
                                        config.getTimeoutSeconds(),
                                        config.getImplicitWaitSeconds()
                                    );
                                    if (urlDriver == null) {
                                        logger.error("WebDriverの再作成に失敗しました: 日付={}, URL={}", date, url);
                                        // 1分待機してから再試行
                                        try {
                                            Thread.sleep(60000);
                                        } catch (InterruptedException ie) {
                                            Thread.currentThread().interrupt();
                                            logger.warn("再試行待機中に中断されました");
                                            break;
                                        }
                                        continue; // ループを継続
                                    }
                                }
                                
                                // WebDriverの接続状態をチェック
                                try {
                                    urlDriver.getCurrentUrl(); // 接続状態を確認
                                } catch (Exception e) {
                                    logger.warn("【接続切断検知】WebDriverの接続が切断されました。再作成します: {}", e.getMessage());
                                    try {
                                        DriverManager.closeWebDriver(urlDriver);
                                    } catch (Exception closeEx) {
                                        logger.debug("WebDriverクリーンアップでエラー（無視します）: {}", closeEx.getMessage());
                                    }
                                    urlDriver = null; // 再作成のためnullに設定
                                    continue; // ループを継続して再作成
                                }
                                
                                // この日付の予約が既に成功している場合は処理を中断
                                if (dateSuccessMap.get(date).get()) {
                                    logger.info("日付 {} の予約が既に成功しているため、処理を中断します: URL={}", date, url);
                                    return;
                                }
                                
                                // リトライ付きでURL処理を実行（日付と時間帯リスト指定、日付成功フラグ付き）
                                // ラムダ式内で使用するため、urlDriverをfinal変数にコピー
                                final WebDriver finalUrlDriver = urlDriver;
                                boolean success = reservationService.processWithRetry(
                                    () -> {
                                        // 処理中に他のタスクが成功した場合は中断
                                        if (dateSuccessMap.get(date).get()) {
                                            logger.info("日付 {} の予約が成功したため、処理を中断します", date);
                                            return false;
                                        }
                                        return reservationService.processUrl(finalUrlDriver, url, date, finalTimeSlots, dateSuccessMap.get(date));
                                    },
                                    config.getMaxRetries()
                                );
                                
                                if (success) {
                                    logger.info("予約が成功しました！日付: {}, URL: {}", date, url);
                                    overallSuccess.set(true);
                                    
                                    // この日付の成功フラグを設定（この日の他のタスクを停止）
                                    dateSuccessMap.get(date).set(true);
                                    logger.info("日付 {} の予約が成功したため、この日の他のタスクを停止します", date);
                                    
                                    // GUIに結果を通知（時間帯付き）
                                    if (guiCallback != null) {
                                        guiCallback.onReservationResult(date, true, finalTimeSlots);
                                    }
                                    
                                    // 成功したらWebDriverをクリーンアップしてループを終了
                                    try {
                                        DriverManager.closeWebDriver(urlDriver);
                                    } catch (Exception e) {
                                        logger.warn("WebDriverのクリーンアップ中にエラー: {}", e.getMessage());
                                    }
                                    urlDriver = null;
                                    break;
                                } else {
                                    // この日付の予約が既に成功している場合は失敗として扱わない
                                    if (!dateSuccessMap.get(date).get()) {
                                        logger.warn("URL処理に失敗しました: 日付={}, URL={}", date, url);
                                        logger.info("1分後に再試行します...");
                                        if (guiCallback != null) {
                                            guiCallback.onReservationResult(date, false);
                                        }
                                    } else {
                                        // 他のタスクが成功した場合はループを終了
                                        break;
                                    }
                                }
                            } finally {
                                // WebDriverをクリーンアップ（成功時のみ、失敗時は再試行のため保持）
                                // 注意: 失敗時はurlDriverを保持して再試行するため、ここではクリーンアップしない
                                // 成功時やループ終了時にのみクリーンアップする
                            }
                            
                            // 失敗した場合は1分待機してから再試行（urlDriverは保持）
                            if (!dateSuccessMap.get(date).get() && isMonitoring && !shouldStopMonitoring) {
                                try {
                                    Thread.sleep(60000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    logger.warn("再試行待機中に中断されました");
                                    break;
                                }
                            }
                        } catch (UnreachableBrowserException e) {
                            // スリープモードに入った可能性がある
                            logger.warn("【スリープ検知】WebDriverが到達不能になりました。スリープモードに入った可能性があります: {}", e.getMessage());
                            logger.warn("【スリープ検知】スリープから復帰後に自動的に再開します");
                            
                            // WebDriverをクリーンアップ
                            try {
                                if (urlDriver != null) {
                                    DriverManager.closeWebDriver(urlDriver);
                                }
                            } catch (Exception closeEx) {
                                logger.debug("WebDriverクリーンアップでエラー（無視します）: {}", closeEx.getMessage());
                            }
                            urlDriver = null; // 再作成のためnullに設定
                            
                            if (guiCallback != null && !dateSuccessMap.get(date).get()) {
                                guiCallback.onReservationResult(date, false);
                            }
                            
                            // スリープから復帰を待つため、少し長めに待機（2分）
                            if (!dateSuccessMap.get(date).get() && isMonitoring && !shouldStopMonitoring) {
                                try {
                                    logger.info("スリープから復帰を待機中...（2分後に再試行）");
                                    Thread.sleep(120000);
                                    lastActivityTime = System.currentTimeMillis(); // 活動時間を更新
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    logger.warn("再試行待機中に中断されました");
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            logger.error("並行処理中にエラーが発生しました: 日付={}, URL={}", date, url, e);
                            
                            // WebDriverが切断された可能性がある場合は再作成
                            if (urlDriver != null) {
                                try {
                                    urlDriver.getCurrentUrl(); // 接続状態を確認
                                } catch (Exception checkEx) {
                                    logger.warn("WebDriverの接続が切断されています。再作成します");
                                    try {
                                        DriverManager.closeWebDriver(urlDriver);
                                    } catch (Exception closeEx) {
                                        logger.debug("WebDriverクリーンアップでエラー（無視します）: {}", closeEx.getMessage());
                                    }
                                    urlDriver = null; // 再作成のためnullに設定
                                }
                            }
                            
                            if (guiCallback != null && !dateSuccessMap.get(date).get()) {
                                guiCallback.onReservationResult(date, false);
                            }
                            
                            // エラー発生時も1分待機してから再試行
                            if (!dateSuccessMap.get(date).get() && isMonitoring && !shouldStopMonitoring) {
                                try {
                                    Thread.sleep(60000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    logger.warn("再試行待機中に中断されました");
                                    break;
                                }
                            }
                        }
                    }
                    
                    // ループ終了時にWebDriverをクリーンアップ
                    if (urlDriver != null) {
                        try {
                            DriverManager.closeWebDriver(urlDriver);
                        } catch (Exception e) {
                            logger.warn("WebDriverの最終クリーンアップ中にエラー: {}", e.getMessage());
                        }
                    }
                    
                    // ループ終了時にlatchをカウントダウン
                    latch.countDown();
                });
            }
        }
        
        // 監視が停止されるまで待機（無限ループで監視を継続するため、latchは使用しない）
        // 代わりに、監視停止フラグが設定されるまで待機
        try {
            while (isMonitoring && !shouldStopMonitoring) {
                Thread.sleep(1000); // 1秒ごとにチェック
            }
        } catch (InterruptedException e) {
            logger.error("待機中に中断されました", e);
            Thread.currentThread().interrupt();
            shouldStopMonitoring = true;
            isMonitoring = false;
        }
        
        // 監視停止を待機（すべてのタスクが停止するまで最大30秒待機）
        logger.info("すべてのタスクの停止を待機中...");
        try {
            boolean finished = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                logger.warn("一部のタスクがタイムアウトしました。強制終了します。");
            }
        } catch (InterruptedException e) {
            logger.error("待機中に中断されました", e);
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (overallSuccess.get()) {
            logger.info("少なくとも1つの予約が成功しました");
        } else {
            logger.info("監視を継続中です（失敗しても再試行を続けます）");
        }
    }
    
    /**
     * 監視を停止
     */
    public void stopMonitoring() {
        logger.info("監視を停止します");
        shouldStopMonitoring = true;
        isMonitoring = false;
        
        if (driver != null) {
            try {
                DriverManager.closeWebDriver(driver);
            } catch (Exception e) {
                logger.warn("WebDriverの終了中にエラーが発生しました（無視します）: {}", e.getMessage());
            } finally {
                driver = null;
            }
        }
        
        logger.info("監視を停止しました");
    }
    
    /**
     * 監視中かどうかを取得
     * @return 監視中の場合true
     */
    public boolean isMonitoring() {
        return isMonitoring;
    }
}



