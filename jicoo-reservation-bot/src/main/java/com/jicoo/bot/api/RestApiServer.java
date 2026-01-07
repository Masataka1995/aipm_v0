package com.jicoo.bot.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jicoo.bot.Config;
import com.jicoo.bot.DateManager;
import com.jicoo.bot.EmailMonitoringService;
import com.jicoo.bot.JicooReservationBot;
import com.jicoo.bot.ReservationService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * REST APIサーバー
 * Webアプリケーション用のHTTPサーバー
 */
public class RestApiServer {
    private static final Logger logger = LoggerFactory.getLogger(RestApiServer.class);
    private static final int DEFAULT_PORT = 8080;
    
    private Server server;
    private final Gson gson;
    private final DateManager dateManager;
    private final JicooReservationBot bot;
    private final ExecutorService executorService;
    private EmailMonitoringService emailMonitoringService;
    private boolean isRunning = false;
    
    // システムトレイ関連
    private SystemTray systemTray;
    private TrayIcon trayIcon;
    private int serverPort;
    
    // 深夜0時自動予約スケジューラー
    private ScheduledExecutorService weeklyReservationScheduler;
    private static final ZoneId JAPAN_ZONE = ZoneId.of("Asia/Tokyo");
    private static final String SCHEDULER_THREAD_NAME = "WeeklyReservationScheduler";
    private static final String MIDNIGHT_SCHEDULER_LOG_PREFIX = "深夜0時自動予約スケジューラーを開始します。初回実行: {}（{}秒後）";
    private static final String MIDNIGHT_RESERVATION_LOG_HEADER = "========================================";
    private static final String MIDNIGHT_RESERVATION_LOG_MESSAGE = "深夜0時: 翌週の枠を自動予約対象に追加します";
    private static final String NEXT_WEEK_ALREADY_ADDED_MSG = "翌週の日付 {} は既に予約対象に追加されています";
    private static final String NEXT_WEEK_ADDED_MSG = "翌週の日付 {} を予約対象に追加しました";
    private static final String DEFAULT_TIME_SLOT_SET_MSG = "デフォルト時間帯 {} を設定しました";
    private static final String MONITORING_STARTED_MSG = "翌週の日付 {} で監視を開始します";
    private static final String TRAY_MESSAGE_TITLE = "Jicoo 自動予約 BOT";
    private static final String TRAY_MESSAGE_FORMAT = "翌週の枠を追加しました: %s %s";
    private static final String WEB_SOCKET_MESSAGE_TYPE = "nextWeekAdded";
    private static final String WEB_SOCKET_DATE_KEY = "date";
    private static final String WEB_SOCKET_TIME_SLOT_KEY = "timeSlot";
    private static final int HOURS_PER_DAY = 24;
    private static final int MINUTES_PER_HOUR = 60;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_DAY = HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE;
    private static final int MIDNIGHT_HOUR = 0;
    private static final int DAYS_TO_ADD_FOR_NEXT_WEEK = 7;
    
    public RestApiServer() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();
        this.dateManager = new DateManager();
        Config config = Config.getInstance();
        ReservationService reservationService = new ReservationService(config);
        this.bot = new JicooReservationBot(config, reservationService, dateManager);
        this.executorService = Executors.newCachedThreadPool();
        
        // BOTのコールバックを設定（システムトレイ設定時に再設定される）
        this.bot.setReservationCallback(new JicooReservationBot.ReservationCallback() {
            @Override
            public void onReservationResult(LocalDate date, boolean success) {
                dateManager.setReservationResult(date, success);
                // WebSocketでクライアントに通知
                WebSocketHandler.broadcastReservationResult(date, success);
            }
            
            @Override
            public void onReservationResult(LocalDate date, boolean success, List<String> timeSlots) {
                onReservationResult(date, success, timeSlots, null);
            }
            
            @Override
            public void onReservationResult(LocalDate date, boolean success, List<String> timeSlots, String teacherUrl) {
                logger.info("予約結果コールバック: 日付={}, 成功={}, 時間帯={}, 先生URL={}", date, success, timeSlots, teacherUrl);
                dateManager.setReservationResult(date, success, timeSlots, teacherUrl);
                // WebSocketでクライアントに通知（時間帯と先生URL情報付き）
                WebSocketHandler.broadcastReservationResult(date, success, timeSlots, teacherUrl);
            }
        });
        
        // メール監視サービスを初期化
        if (config.isEmailMonitoringEnabled()) {
            this.emailMonitoringService = new EmailMonitoringService(
                config,
                dateManager,
                (date, time, teacher) -> {
                    logger.info("メールから予約を検出: 日付={}, 時間={}, 講師={}", date, time, teacher);
                    dateManager.setReservationResult(date, true);
                    WebSocketHandler.broadcastReservationResult(date, true);
                    // システムトレイに通知
                    if (trayIcon != null) {
                        trayIcon.displayMessage(
                            "Jicoo 自動予約 BOT",
                            String.format("メールから予約を検出: %s %s", date, time),
                            TrayIcon.MessageType.INFO
                        );
                    }
                }
            );
        }
    }
    
    /**
     * サーバーを起動
     */
    public void start() throws Exception {
        if (server != null && server.isRunning()) {
            logger.warn("サーバーは既に起動しています");
            return;
        }
        
        serverPort = getServerPort();
        server = new Server(serverPort);
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        // カスタムサーブレットで処理するため、リソースベースは設定しない
        
        // CORS設定
        context.addFilter(CorsFilter.class, "/*", null);
        
        // REST APIエンドポイント（より具体的なパターンを先に登録）
        ServletHolder apiServletHolder = new ServletHolder(new ApiServlet(gson, dateManager, bot, this));
        context.addServlet(apiServletHolder, "/api/*");
        
        // 静的ファイル（HTML/CSS/JS）の配信
        // カスタムサーブレットを使用するため、リソースベースは設定しない（デフォルトのまま）
        ServletHolder staticServletHolder = new ServletHolder(new StaticFileServlet());
        // 静的ファイルサーブレットを"/"にマッピング（APIパスより後に登録されるが、"/api/*"は既に処理されている）
        context.addServlet(staticServletHolder, "/");
        
        // WebSocketエンドポイント（静的ファイルサーブレットの後に設定）
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            try {
                // アイドルタイムアウトを無効化（0 = 無制限）
                wsContainer.setIdleTimeout(java.time.Duration.ZERO);
                
                // Jetty 11では、WebSocketCreatorを使用してWebSocketHandlerを登録
                wsContainer.addMapping("/ws", (upgradeRequest, upgradeResponse) -> {
                    logger.info("WebSocket接続要求を受信: {}", upgradeRequest.getRequestURI());
                    WebSocketHandler handler = new WebSocketHandler();
                    return handler;
                });
                logger.info("WebSocketエンドポイント /ws を登録しました（アイドルタイムアウト: 無制限）");
            } catch (Exception e) {
                logger.error("WebSocketエンドポイントの追加に失敗しました", e);
            }
        });
        
        server.setHandler(context);
        server.start();
        
        isRunning = true;
        logger.info("========================================");
        logger.info("REST APIサーバーを起動しました");
        logger.info("========================================");
        logger.info("Webアプリケーション: http://localhost:{}/index.html", serverPort);
        logger.info("APIエンドポイント: http://localhost:{}/api/", serverPort);
        logger.info("========================================");
        logger.info("システムトレイに常駐します");
        logger.info("停止するにはシステムトレイアイコンを右クリック→終了");
        logger.info("========================================");
        
        // システムトレイを設定
        setupSystemTray();
        
        // スリープモードを防ぐ（設定で有効な場合のみ）
        Config config = Config.getInstance();
        if (config.isSleepPreventEnabled()) {
            SleepPreventer.preventSleep();
            logger.info("スリープモード防止を有効にしました（監視中はスリープしません）");
        } else {
            logger.info("スリープモード防止は無効です（スリープから復帰時に自動再開します）");
        }
        
        // メール監視を開始
        if (emailMonitoringService != null) {
            emailMonitoringService.start();
        }
        
        // 深夜0時に翌週の枠を自動予約するスケジューラーを開始
        startWeeklyReservationScheduler();
        
        // ブラウザの自動起動は無効化（システムトレイアイコンをクリックして開く）
        // 自動起動を有効にしたい場合は、以下のコメントを外してください
        /*
        try {
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                Desktop.getDesktop().browse(
                    new URI("http://localhost:" + serverPort + "/index.html")
                );
            }
        } catch (Exception e) {
            logger.debug("ブラウザの自動起動に失敗しました（無視して続行）", e);
        }
        */
    }
    
    /**
     * サーバーを停止
     */
    public void stop() throws Exception {
        // メール監視を停止
        if (emailMonitoringService != null) {
            try {
                emailMonitoringService.stop();
            } catch (Exception e) {
                logger.warn("メール監視サービスの停止中にエラーが発生しました（無視して続行）: {}", e.getMessage());
            }
        }
        
        // スリープモード防止を解除
        SleepPreventer.allowSleep();
        
        // システムトレイを削除
        if (trayIcon != null && systemTray != null) {
            try {
                systemTray.remove(trayIcon);
            } catch (Exception e) {
                logger.debug("システムトレイアイコンの削除中にエラーが発生しました（無視）: {}", e.getMessage());
            }
        }
        
        // Jettyサーバーを停止
        if (server != null && server.isRunning()) {
            try {
                server.stop();
                isRunning = false;
                logger.info("REST APIサーバーを停止しました");
            } catch (InterruptedException e) {
                // シャットダウン時の中断は正常な動作
                logger.warn("サーバー停止処理が中断されました（無視して続行）");
                Thread.currentThread().interrupt();
                isRunning = false;
            } catch (Exception e) {
                logger.warn("サーバー停止中にエラーが発生しました（無視して続行）: {}", e.getMessage());
                isRunning = false;
            }
        }
        
        // ExecutorServiceをシャットダウン
        if (executorService != null) {
            try {
                executorService.shutdown();
                // 最大5秒待機
                if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    logger.debug("ExecutorServiceを強制終了しました");
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
                logger.debug("ExecutorServiceのシャットダウンが中断されました");
            } catch (Exception e) {
                logger.warn("ExecutorServiceのシャットダウン中にエラーが発生しました（無視）: {}", e.getMessage());
            }
        }
        
        // 深夜0時自動予約スケジューラーを停止
        if (weeklyReservationScheduler != null) {
            try {
                weeklyReservationScheduler.shutdown();
                if (!weeklyReservationScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    weeklyReservationScheduler.shutdownNow();
                    logger.debug("週次予約スケジューラーを強制終了しました");
                }
            } catch (InterruptedException e) {
                weeklyReservationScheduler.shutdownNow();
                Thread.currentThread().interrupt();
                logger.debug("週次予約スケジューラーのシャットダウンが中断されました");
            } catch (Exception e) {
                logger.warn("週次予約スケジューラーのシャットダウン中にエラーが発生しました（無視）: {}", e.getMessage());
            }
        }
    }
    
    /**
     * システムトレイを設定
     */
    private void setupSystemTray() {
        if (!SystemTray.isSupported()) {
            logger.warn("システムトレイがサポートされていません");
            return;
        }
        
        try {
            systemTray = SystemTray.getSystemTray();
            
            // アイコン画像を作成
            BufferedImage image = createTrayIconImage();
            
            // ポップアップメニューを作成
            PopupMenu popup = new PopupMenu();
            
            MenuItem openBrowserItem = new MenuItem("ブラウザで開く");
            openBrowserItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        Desktop.getDesktop().browse(
                            new URI("http://localhost:" + serverPort + "/index.html")
                        );
                    } catch (Exception ex) {
                        logger.error("ブラウザを開く際にエラーが発生しました", ex);
                    }
                }
            });
            popup.add(openBrowserItem);
            
            popup.addSeparator();
            
            MenuItem stopMonitoringItem = new MenuItem("監視を停止");
            stopMonitoringItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    bot.stopMonitoring();
                    if (trayIcon != null) {
                        trayIcon.displayMessage(
                            "Jicoo 自動予約 BOT",
                            "監視を停止しました",
                            TrayIcon.MessageType.INFO
                        );
                    }
                }
            });
            popup.add(stopMonitoringItem);
            
            popup.addSeparator();
            
            MenuItem exitItem = new MenuItem("終了");
            exitItem.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        stop();
                        System.exit(0);
                    } catch (Exception ex) {
                        logger.error("終了処理中にエラーが発生しました", ex);
                        System.exit(1);
                    }
                }
            });
            popup.add(exitItem);
            
            // トレイアイコンを作成
            String tooltip = String.format("Jicoo 自動予約 BOT (実行中)\nポート: %d\nhttp://localhost:%d/index.html", 
                serverPort, serverPort);
            trayIcon = new TrayIcon(image, tooltip, popup);
            trayIcon.setImageAutoSize(true);
            
            // アイコンをクリックしたときの処理
            trayIcon.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        Desktop.getDesktop().browse(
                            new URI("http://localhost:" + serverPort + "/index.html")
                        );
                    } catch (Exception ex) {
                        logger.error("ブラウザを開く際にエラーが発生しました", ex);
                    }
                }
            });
            
            // システムトレイに追加
            systemTray.add(trayIcon);
            
            // 起動通知を表示
            trayIcon.displayMessage(
                "Jicoo 自動予約 BOT",
                String.format("サーバーが起動しました\nポート: %d\nシステムトレイで実行中", serverPort),
                TrayIcon.MessageType.INFO
            );
            
            // 予約成功時の通知を追加（既存のコールバックを保持しつつ、システムトレイ通知を追加）
            // 既存のコールバックを保存
            final JicooReservationBot.ReservationCallback originalCallback = bot.getReservationCallback();
            
            // 新しいコールバックを設定（既存のコールバック + システムトレイ通知）
            bot.setReservationCallback(new JicooReservationBot.ReservationCallback() {
                @Override
                public void onReservationResult(LocalDate date, boolean success) {
                    // 既存のコールバックを実行
                    if (originalCallback != null) {
                        originalCallback.onReservationResult(date, success);
                    }
                    // システムトレイ通知
                    if (trayIcon != null) {
                        String message = success 
                            ? String.format("予約成功: %s", date)
                            : String.format("予約失敗: %s", date);
                        TrayIcon.MessageType messageType = success 
                            ? TrayIcon.MessageType.INFO 
                            : TrayIcon.MessageType.WARNING;
                        trayIcon.displayMessage(
                            "Jicoo 自動予約 BOT",
                            message,
                            messageType
                        );
                    }
                }
                
                @Override
                public void onReservationResult(LocalDate date, boolean success, List<String> timeSlots) {
                    // 既存のコールバックを実行
                    if (originalCallback != null) {
                        originalCallback.onReservationResult(date, success, timeSlots);
                    }
                    // システムトレイ通知
                    if (trayIcon != null) {
                        String message = success 
                            ? String.format("予約成功: %s", date)
                            : String.format("予約失敗: %s", date);
                        TrayIcon.MessageType messageType = success 
                            ? TrayIcon.MessageType.INFO 
                            : TrayIcon.MessageType.WARNING;
                        trayIcon.displayMessage(
                            "Jicoo 自動予約 BOT",
                            message,
                            messageType
                        );
                    }
                }
            });
            
            logger.info("システムトレイを設定しました");
        } catch (Exception e) {
            logger.error("システムトレイの設定に失敗しました", e);
        }
    }
    
    /**
     * トレイアイコン用の画像を作成
     */
    private BufferedImage createTrayIconImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 背景（紫のグラデーション）
        g.setColor(new Color(102, 126, 234));
        g.fillOval(0, 0, 16, 16);
        
        // 文字「J」を描画
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int x = (16 - fm.stringWidth("J")) / 2;
        int y = (16 - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString("J", x, y);
        
        g.dispose();
        return image;
    }
    
    /**
     * サーバーポートを取得
     */
    private int getServerPort() {
        String portStr = System.getProperty("server.port", String.valueOf(DEFAULT_PORT));
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public DateManager getDateManager() {
        return dateManager;
    }
    
    public JicooReservationBot getBot() {
        return bot;
    }
    
    /**
     * 深夜0時に翌週の枠を自動予約するスケジューラーを開始
     */
    private void startWeeklyReservationScheduler() {
        weeklyReservationScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, SCHEDULER_THREAD_NAME);
            t.setDaemon(true);
            return t;
        });
        
        // 次の深夜0時（日本時間）までの待機時間を計算
        ZonedDateTime now = ZonedDateTime.now(JAPAN_ZONE);
        ZonedDateTime nextMidnight = now.withHour(MIDNIGHT_HOUR).withMinute(0).withSecond(0).withNano(0);
        
        // 既に深夜0時を過ぎている場合は翌日の深夜0時
        if (now.isAfter(nextMidnight) || now.isEqual(nextMidnight)) {
            nextMidnight = nextMidnight.plusDays(1);
        }
        
        long initialDelay = java.time.Duration.between(now, nextMidnight).getSeconds();
        logger.info(MIDNIGHT_SCHEDULER_LOG_PREFIX, nextMidnight, initialDelay);
        
        // 初回実行をスケジュール
        weeklyReservationScheduler.schedule(() -> {
            try {
                scheduleNextWeekReservation();
            } catch (Exception e) {
                logger.error("深夜0時自動予約処理中にエラーが発生しました", e);
            }
        }, initialDelay, TimeUnit.SECONDS);
        
        // 以降、毎日深夜0時に実行（24時間ごと）
        weeklyReservationScheduler.scheduleAtFixedRate(() -> {
            try {
                scheduleNextWeekReservation();
            } catch (Exception e) {
                logger.error("深夜0時自動予約処理中にエラーが発生しました", e);
            }
        }, initialDelay, SECONDS_PER_DAY, TimeUnit.SECONDS);
    }
    
    /**
     * 翌週の枠を予約対象に追加して監視を開始
     */
    private void scheduleNextWeekReservation() {
        logger.info(MIDNIGHT_RESERVATION_LOG_HEADER);
        logger.info(MIDNIGHT_RESERVATION_LOG_MESSAGE);
        logger.info(MIDNIGHT_RESERVATION_LOG_HEADER);
        
        Config config = Config.getInstance();
        LocalDate nextWeek = LocalDate.now().plusDays(DAYS_TO_ADD_FOR_NEXT_WEEK);
        
        // 既に追加されているかチェック
        DateManager.DateInfo existingInfo = dateManager.getDateInfo(nextWeek);
        if (existingInfo != null && existingInfo.isEnabled()) {
            logger.info(NEXT_WEEK_ALREADY_ADDED_MSG, nextWeek);
            return;
        }
        
        // 日付を追加
        dateManager.addDate(nextWeek);
        logger.info(NEXT_WEEK_ADDED_MSG, nextWeek);
        
        // デフォルトの時間帯を設定
        String targetTime = config.getTargetTime();
        if (targetTime != null && !targetTime.isEmpty()) {
            DateManager.DateInfo dateInfo = dateManager.getDateInfo(nextWeek);
            if (dateInfo != null) {
                List<String> timeSlots = new ArrayList<>();
                timeSlots.add(targetTime);
                dateInfo.setSelectedTimeSlots(timeSlots);
                logger.info(DEFAULT_TIME_SLOT_SET_MSG, targetTime);
            }
        }
        
        // 監視を開始
        Map<LocalDate, List<String>> nextWeekMap = new HashMap<>();
        DateManager.DateInfo dateInfo = dateManager.getDateInfo(nextWeek);
        if (dateInfo != null) {
            nextWeekMap.put(nextWeek, dateInfo.getSelectedTimeSlots());
        }
        
        logger.info(MONITORING_STARTED_MSG, nextWeek);
        bot.startMonitoring(nextWeekMap);
        
        // システムトレイに通知
        if (trayIcon != null) {
            trayIcon.displayMessage(
                TRAY_MESSAGE_TITLE,
                String.format(TRAY_MESSAGE_FORMAT, nextWeek, targetTime),
                TrayIcon.MessageType.INFO
            );
        }
        
        // WebSocketでクライアントに通知
        Map<String, Object> messageData = new HashMap<>();
        messageData.put(WEB_SOCKET_DATE_KEY, nextWeek.toString());
        messageData.put(WEB_SOCKET_TIME_SLOT_KEY, targetTime != null ? targetTime : "");
        WebSocketHandler.broadcastMessage(WEB_SOCKET_MESSAGE_TYPE, messageData);
        
        logger.info(MIDNIGHT_RESERVATION_LOG_HEADER);
    }
    
    /**
     * メインメソッド
     */
    public static void main(String[] args) {
        // AWTヘッドレスモードを無効化（システムトレイを使用するため）
        System.setProperty("java.awt.headless", "false");
        
        try {
            RestApiServer apiServer = new RestApiServer();
            apiServer.start();
            
            // シャットダウンフック
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    apiServer.stop();
                } catch (Exception e) {
                    logger.error("サーバー停止中にエラーが発生しました", e);
                }
            }));
            
            // サーバーが停止するまで待機
            apiServer.server.join();
        } catch (Exception e) {
            logger.error("サーバー起動中にエラーが発生しました", e);
            System.exit(1);
        }
    }
}


