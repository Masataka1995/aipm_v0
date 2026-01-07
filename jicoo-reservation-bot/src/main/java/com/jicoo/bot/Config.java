package com.jicoo.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 設定管理クラス
 * application.propertiesから設定を読み込む
 */
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static Config instance;
    
    private final List<String> urls;
    private final String username;
    private final String password;
    private final String targetTime;
    private final int monitoringIntervalSeconds;
    private final int maxRetries;
    private final String reservationName;
    private final String reservationEmail;
    private final boolean headless;
    private final int timeoutSeconds;
    private final int implicitWaitSeconds;
    private final int monitoringStartHour;
    private final int monitoringEndHour;
    private static volatile boolean monitoringTimeRestrictionEnabled = true; // 監視時間制限のON/OFF
    
    // メール監視設定
    private final boolean emailMonitoringEnabled;
    private final String emailImapHost;
    private final int emailImapPort;
    private final String emailImapUsername;
    private final String emailImapPassword;
    private final int emailMonitoringInterval;
    private final String emailSenderFilter;
    private final String emailSubjectFilter;
    private final boolean sleepPreventEnabled;
    private static final ZoneId JAPAN_ZONE = ZoneId.of("Asia/Tokyo");
    
    private Config() {
        Properties props = loadProperties();
        
        // URLリストの読み込み
        String urlsStr = props.getProperty("jicoo.urls", "");
        logger.info("読み込んだURL文字列: [{}]", urlsStr);
        logger.info("読み込んだプロパティ数: {}", props.size());
        
        // プロパティが読み込まれていない場合のデフォルトURL設定
        if (props.isEmpty() || urlsStr.isEmpty()) {
            logger.warn("application.propertiesからURLが読み込めませんでした。デフォルトURLを使用します。");
            // デフォルトURLを設定
            urlsStr = "https://www.jicoo.com/t/_XDgWVCOgMPP/e/Teacher_Vanessa,https://www.jicoo.com/en/t/_XDgWVCOgMPP/e/Jozelly,https://www.jicoo.com/en/t/_XDgWVCOgMPP/e/Teacher_Lorna,https://www.jicoo.com/en/t/_XDgWVCOgMPP/e/namie";
            logger.info("デフォルトURL文字列: [{}]", urlsStr);
        }
        
        this.urls = parseUrls(urlsStr);
        logger.info("パース後のURL数: {}", this.urls.size());
        for (int i = 0; i < this.urls.size(); i++) {
            logger.info("  URL[{}]: {}", i, this.urls.get(i));
        }
        
        // URLが空の場合の警告
        if (this.urls.isEmpty()) {
            logger.error("監視対象URLが設定されていません。application.propertiesのjicoo.urlsを確認してください。");
            logger.error("読み込んだプロパティ数: {}", props.size());
            logger.error("jicoo.urlsプロパティの値: [{}]", urlsStr);
        }
        
        // ログイン情報
        this.username = props.getProperty("jicoo.login.username", "students");
        this.password = props.getProperty("jicoo.login.password", "uluru2024");
        
        // 予約情報
        this.reservationName = props.getProperty("jicoo.reservation.name", "Masataka");
        this.reservationEmail = props.getProperty("jicoo.reservation.email", "masataaaka3@icloud.com");
        
        // 監視設定
        this.targetTime = props.getProperty("jicoo.target.time", "");
        this.monitoringIntervalSeconds = Integer.parseInt(
            props.getProperty("jicoo.monitoring.interval.seconds", "5"));
        this.maxRetries = Integer.parseInt(
            props.getProperty("jicoo.max.retries", "3"));
        
        // WebDriver設定
        this.headless = Boolean.parseBoolean(
            props.getProperty("webdriver.headless", "false"));
        this.timeoutSeconds = Integer.parseInt(
            props.getProperty("webdriver.timeout.seconds", "30"));
        this.implicitWaitSeconds = Integer.parseInt(
            props.getProperty("webdriver.implicit.wait.seconds", "10"));
        
        // 監視時間設定（日本時間）
        this.monitoringStartHour = Integer.parseInt(
            props.getProperty("jicoo.monitoring.start.hour", "9"));
        this.monitoringEndHour = Integer.parseInt(
            props.getProperty("jicoo.monitoring.end.hour", "20"));
        
        // メール監視設定
        this.emailMonitoringEnabled = Boolean.parseBoolean(
            props.getProperty("email.monitoring.enabled", "false"));
        this.emailImapHost = props.getProperty("email.imap.host", "outlook.office365.com");
        this.emailImapPort = Integer.parseInt(
            props.getProperty("email.imap.port", "993"));
        this.emailImapUsername = props.getProperty("email.imap.username", "");
        this.emailImapPassword = props.getProperty("email.imap.password", "");
        this.emailMonitoringInterval = Integer.parseInt(
            props.getProperty("email.monitoring.interval.seconds", "300"));
        this.emailSenderFilter = props.getProperty("email.sender.filter", "jicoo.com");
        this.emailSubjectFilter = props.getProperty("email.subject.filter", "予約");
        
        // スリープモード設定
        this.sleepPreventEnabled = Boolean.parseBoolean(
            props.getProperty("sleep.prevent.enabled", "true"));
        
        logger.info("設定を読み込みました: URL数={}, 対象時間={}, 監視間隔={}秒, 監視時間={}時〜{}時（日本時間）, スリープ防止={}", 
            urls.size(), targetTime, monitoringIntervalSeconds, monitoringStartHour, monitoringEndHour, sleepPreventEnabled);
        if (emailMonitoringEnabled) {
            logger.info("メール監視: 有効, ホスト={}, 間隔={}秒", emailImapHost, emailMonitoringInterval);
        }
    }
    
    /**
     * シングルトンインスタンスを取得
     */
    public static Config getInstance() {
        if (instance == null) {
            synchronized (Config.class) {
                if (instance == null) {
                    instance = new Config();
                }
            }
        }
        return instance;
    }
    
    /**
     * プロパティファイルを読み込む
     */
    private Properties loadProperties() {
        Properties props = new Properties();
        
        // 複数の方法でリソースを読み込む
        InputStream is = null;
        
        // 方法1: ClassLoader経由
        try {
            is = getClass().getClassLoader().getResourceAsStream("application.properties");
            if (is == null) {
                // 方法2: クラス経由
                is = getClass().getResourceAsStream("/application.properties");
            }
            if (is == null) {
                // 方法3: システムリソース
                is = ClassLoader.getSystemResourceAsStream("application.properties");
            }
            
            if (is == null) {
                logger.warn("application.propertiesが見つかりません。デフォルト値を使用します。");
                logger.warn("リソース検索パス: {}", getClass().getClassLoader().getResource("application.properties"));
                return props;
            }
            
            props.load(is);
            logger.info("application.propertiesを読み込みました");
            logger.info("読み込んだプロパティ数: {}", props.size());
            // デバッグ用：すべてのプロパティをログ出力
            for (String key : props.stringPropertyNames()) {
                if (key.equals("jicoo.urls")) {
                    String value = props.getProperty(key);
                    logger.info("プロパティ[{}] = [{}]", key, value);
                    logger.info("プロパティ[{}]の長さ: {}", key, value != null ? value.length() : 0);
                } else {
                    logger.debug("プロパティ[{}] = [{}]", key, props.getProperty(key));
                }
            }
            
            // jicoo.urlsが存在しない場合の警告
            if (!props.containsKey("jicoo.urls")) {
                logger.warn("application.propertiesにjicoo.urlsプロパティが見つかりません");
            }
        } catch (Exception e) {
            logger.error("設定ファイルの読み込みに失敗しました", e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    // 無視
                }
            }
        }
        return props;
    }
    
    /**
     * URL文字列をパースしてリストに変換
     */
    private List<String> parseUrls(String urlsStr) {
        List<String> urlList = new ArrayList<>();
        if (urlsStr == null || urlsStr.trim().isEmpty()) {
            return urlList;
        }
        
        // バックスラッシュ改行を削除してから処理
        String cleaned = urlsStr.replace("\\\n", "").replace("\\\r\n", "").replace("\\\r", "");
        
        // カンマ、改行、空白行で分割
        String[] urlArray = cleaned.split("[,\\n\\r]+");
        for (String url : urlArray) {
            String trimmed = url.trim();
            // 空行やバックスラッシュのみの行をスキップ
            if (!trimmed.isEmpty() && !trimmed.equals("\\")) {
                urlList.add(trimmed);
            }
        }
        return urlList;
    }
    
    /**
     * 今日から1週間後の日付を取得
     */
    public LocalDate getTargetDate() {
        return LocalDate.now().plusDays(7);
    }
    
    // Getters
    public List<String> getUrls() {
        return new ArrayList<>(urls);
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public String getTargetTime() {
        return targetTime;
    }
    
    public int getMonitoringIntervalSeconds() {
        return monitoringIntervalSeconds;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public String getReservationName() {
        return reservationName;
    }
    
    public String getReservationEmail() {
        return reservationEmail;
    }
    
    public boolean isHeadless() {
        return headless;
    }
    
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
    
    public int getImplicitWaitSeconds() {
        return implicitWaitSeconds;
    }
    
    /**
     * 監視開始時刻（時）を取得
     */
    public int getMonitoringStartHour() {
        return monitoringStartHour;
    }
    
    /**
     * 監視終了時刻（時）を取得
     */
    public int getMonitoringEndHour() {
        return monitoringEndHour;
    }
    
    /**
     * 監視時間制限が有効かどうかを取得
     * @return 有効の場合true
     */
    public boolean isMonitoringTimeRestrictionEnabled() {
        return monitoringTimeRestrictionEnabled;
    }
    
    /**
     * 監視時間制限のON/OFFを設定
     * @param enabled 有効にする場合true
     */
    public void setMonitoringTimeRestrictionEnabled(boolean enabled) {
        Config.monitoringTimeRestrictionEnabled = enabled;
        logger.info("監視時間制限を{}に設定しました", enabled ? "有効" : "無効");
    }
    
    /**
     * 現在が監視時間内かどうかを判定（日本時間）
     * @return 監視時間内の場合true（制限が無効の場合は常にtrue）
     */
    public boolean isWithinMonitoringHours() {
        // 監視時間制限が無効の場合は常にtrueを返す
        if (!monitoringTimeRestrictionEnabled) {
            return true;
        }
        ZonedDateTime now = ZonedDateTime.now(JAPAN_ZONE);
        int currentHour = now.getHour();
        
        // 監視時間が24時を跨ぐ場合（endHour < startHour、例：20時から翌日の2時まで）
        if (monitoringEndHour < monitoringStartHour) {
            // 20時以降（20-23時）または2時未満（0-1時）が監視時間内
            return currentHour >= monitoringStartHour || currentHour < monitoringEndHour;
        } else {
            // 通常の場合（例：9時から20時まで）
            return currentHour >= monitoringStartHour && currentHour < monitoringEndHour;
        }
    }
    
    /**
     * 次の監視開始時刻までの待機時間（秒）を取得
     * @return 待機時間（秒）、既に監視時間内の場合は0
     */
    public long getSecondsUntilMonitoringStart() {
        ZonedDateTime now = ZonedDateTime.now(JAPAN_ZONE);
        ZonedDateTime nextStart;
        
        int currentHour = now.getHour();
        
        // 監視時間が24時を跨ぐ場合（endHour < startHour、例：20時から翌日の2時まで）
        if (monitoringEndHour < monitoringStartHour) {
            if (currentHour >= monitoringStartHour) {
                // 20時以降は監視時間内（次の開始は翌日の20時）
                return 0;
            } else if (currentHour < monitoringEndHour) {
                // 2時未満も監視時間内（次の開始は今日の20時）
                nextStart = now.withHour(monitoringStartHour).withMinute(0).withSecond(0).withNano(0);
            } else {
                // 2時から20時未満は監視時間外（次の開始は今日の20時）
                nextStart = now.withHour(monitoringStartHour).withMinute(0).withSecond(0).withNano(0);
            }
        } else {
            // 通常の場合（例：9時から20時まで）
            if (currentHour < monitoringStartHour) {
                // 今日の監視開始時刻
                nextStart = now.withHour(monitoringStartHour).withMinute(0).withSecond(0).withNano(0);
            } else if (currentHour >= monitoringEndHour) {
                // 明日の監視開始時刻
                nextStart = now.plusDays(1).withHour(monitoringStartHour).withMinute(0).withSecond(0).withNano(0);
            } else {
                // 既に監視時間内
                return 0;
            }
        }
        
        return java.time.Duration.between(now, nextStart).getSeconds();
    }
    
    // メール監視設定のgetter
    public boolean isEmailMonitoringEnabled() {
        return emailMonitoringEnabled;
    }
    
    public String getEmailImapHost() {
        return emailImapHost;
    }
    
    public int getEmailImapPort() {
        return emailImapPort;
    }
    
    public String getEmailImapUsername() {
        return emailImapUsername;
    }
    
    public String getEmailImapPassword() {
        return emailImapPassword;
    }
    
    public int getEmailMonitoringInterval() {
        return emailMonitoringInterval;
    }
    
    public String getEmailSenderFilter() {
        return emailSenderFilter;
    }
    
    public String getEmailSubjectFilter() {
        return emailSubjectFilter;
    }
    
    /**
     * スリープモード防止が有効かどうかを取得
     * @return 有効の場合true
     */
    public boolean isSleepPreventEnabled() {
        return sleepPreventEnabled;
    }
}

