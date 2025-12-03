package com.jicoo.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * メール監視サービス
 * Outlookメールを監視して予約確認メールを検出し、予約情報を抽出
 */
public class EmailMonitoringService {
    private static final Logger logger = LoggerFactory.getLogger(EmailMonitoringService.class);
    
    private final Config config;
    private final DateManager dateManager;
    private final ReservationCallback callback;
    private ScheduledExecutorService scheduler;
    private Store store;
    private Folder inbox;
    private boolean isRunning = false;
    private Set<String> processedMessageIds = new HashSet<>(100); // 初期サイズを指定（過去7日分のメールを想定）
    
    // 日付パターン（様々な形式に対応）
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{4})[年/\\-](\\d{1,2})[月/\\-](\\d{1,2})[日]?"
    );
    
    // 時間パターン
    private static final Pattern TIME_PATTERN = Pattern.compile(
        "(\\d{1,2}):(\\d{2})"
    );
    
    /**
     * 予約情報のコールバック
     */
    public interface ReservationCallback {
        void onReservationDetected(LocalDate date, LocalTime time, String teacher);
    }
    
    public EmailMonitoringService(Config config, DateManager dateManager, ReservationCallback callback) {
        this.config = config;
        this.dateManager = dateManager;
        this.callback = callback;
    }
    
    /**
     * メール監視を開始
     */
    public void start() {
        if (isRunning) {
            logger.warn("メール監視は既に実行中です");
            return;
        }
        
        // メール監視が有効かチェック
        if (!isEmailMonitoringEnabled()) {
            logger.info("メール監視は無効です（email.monitoring.enabled=false）");
            return;
        }
        
        try {
            connectToMailServer();
            isRunning = true;
            scheduler = Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "EmailMonitoringService");
                t.setDaemon(true); // デーモンスレッドとして設定
                return t;
            });
            
            int intervalSeconds = config.getEmailMonitoringInterval();
            logger.info("メール監視を開始します（間隔: {}秒）", intervalSeconds);
            
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    checkForNewReservations();
                } catch (Exception e) {
                    logger.error("メール監視中にエラーが発生しました", e);
                }
            }, 0, intervalSeconds, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            logger.error("メール監視の開始に失敗しました", e);
            isRunning = false;
        }
    }
    
    /**
     * メール監視を停止
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        closeMailConnection();
        logger.info("メール監視を停止しました");
    }
    
    /**
     * メールサーバーに接続
     */
    private void connectToMailServer() throws MessagingException {
        String host = config.getEmailImapHost();
        int port = config.getEmailImapPort();
        String username = config.getEmailImapUsername();
        String password = config.getEmailImapPassword();
        
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("メールパスワードが設定されていません（email.imap.password）");
        }
        
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", String.valueOf(port));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.auth", "true");
        
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        
        store = session.getStore("imaps");
        store.connect(host, username, password);
        inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);
        
        logger.info("メールサーバーに接続しました: {}:{}", host, port);
    }
    
    /**
     * メール接続を閉じる
     */
    private void closeMailConnection() {
        try {
            if (inbox != null && inbox.isOpen()) {
                inbox.close(false);
            }
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (MessagingException e) {
            logger.warn("メール接続のクローズ中にエラーが発生しました", e);
        }
    }
    
    /**
     * 新しい予約メールをチェック
     */
    private void checkForNewReservations() throws MessagingException {
        if (inbox == null || !inbox.isOpen()) {
            logger.warn("メールボックスが開いていません。再接続を試みます...");
            try {
                connectToMailServer();
            } catch (Exception e) {
                logger.error("メールサーバーへの再接続に失敗しました", e);
                return;
            }
        }
        
        // 最近のメールを取得（過去7日分）
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        Date sinceDate = calendar.getTime();
        
        Message[] messages = inbox.search(
            new ReceivedDateTerm(ComparisonTerm.GE, sinceDate)
        );
        
        logger.debug("チェック対象のメール数: {}", messages.length);
        
        String senderFilter = config.getEmailSenderFilter();
        String subjectFilter = config.getEmailSubjectFilter();
        
        for (Message message : messages) {
            try {
                String messageId = getMessageId(message);
                
                // 既に処理済みのメールはスキップ
                if (processedMessageIds.contains(messageId)) {
                    continue;
                }
                
                // 送信者フィルタ
                if (senderFilter != null && !senderFilter.isEmpty()) {
                    Address[] fromAddresses = message.getFrom();
                    if (fromAddresses == null || fromAddresses.length == 0) {
                        continue;
                    }
                    String from = ((InternetAddress) fromAddresses[0]).getAddress();
                    if (!from.contains(senderFilter)) {
                        continue;
                    }
                }
                
                // 件名フィルタ
                if (subjectFilter != null && !subjectFilter.isEmpty()) {
                    String subject = message.getSubject();
                    if (subject == null || !subject.contains(subjectFilter)) {
                        continue;
                    }
                }
                
                // メール内容を解析
                ReservationInfo reservation = parseReservationEmail(message);
                if (reservation != null) {
                    logger.info("予約確認メールを検出しました: 日付={}, 時間={}, 講師={}", 
                        reservation.date, reservation.time, reservation.teacher);
                    
                    // 予約情報をDateManagerに追加
                    if (reservation.date != null) {
                        DateManager.DateInfo dateInfo = dateManager.getDateInfo(reservation.date);
                        if (dateInfo == null) {
                            dateManager.addDate(reservation.date);
                            dateInfo = dateManager.getDateInfo(reservation.date);
                            if (dateInfo != null) {
                                dateInfo.setEnabled(true);
                            }
                        }
                        
                        if (dateInfo != null && reservation.time != null) {
                            String timeStr = reservation.time.format(DateTimeFormatter.ofPattern("HH:mm"));
                            dateInfo.addTimeSlot(timeStr);
                            dateManager.setReservationResult(reservation.date, true);
                            
                            // コールバックを呼び出し
                            if (callback != null) {
                                callback.onReservationDetected(reservation.date, reservation.time, reservation.teacher);
                            }
                        }
                    }
                    
                    processedMessageIds.add(messageId);
                }
                
            } catch (Exception e) {
                logger.warn("メール解析中にエラーが発生しました", e);
            }
        }
    }
    
    /**
     * メールIDを取得
     */
    private String getMessageId(Message message) throws MessagingException {
        String[] messageIdHeader = message.getHeader("Message-ID");
        if (messageIdHeader != null && messageIdHeader.length > 0) {
            return messageIdHeader[0];
        }
        // Message-IDがない場合は、日付と件名で一意のIDを生成
        return message.getReceivedDate().getTime() + "_" + message.getSubject();
    }
    
    /**
     * 予約確認メールを解析
     */
    private ReservationInfo parseReservationEmail(Message message) throws Exception {
        String subject = message.getSubject();
        String content = getMessageContent(message);
        
        logger.debug("メール解析: 件名={}", subject);
        logger.debug("メール内容（最初の200文字）: {}", 
            content.length() > 200 ? content.substring(0, 200) + "..." : content);
        
        ReservationInfo info = new ReservationInfo();
        
        // 日付を抽出
        LocalDate date = extractDate(content);
        if (date == null) {
            date = extractDate(subject);
        }
        info.date = date;
        
        // 時間を抽出
        LocalTime time = extractTime(content);
        if (time == null) {
            time = extractTime(subject);
        }
        info.time = time;
        
        // 講師名を抽出（オプション）
        info.teacher = extractTeacher(content);
        
        // 日付または時間が抽出できた場合のみ有効
        if (info.date != null || info.time != null) {
            return info;
        }
        
        return null;
    }
    
    /**
     * メール本文を取得
     */
    private String getMessageContent(Message message) throws Exception {
        Object content = message.getContent();
        if (content instanceof String) {
            return (String) content;
        } else if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            StringBuilder textContent = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (bodyPart.getContentType().contains("text/plain")) {
                    textContent.append(bodyPart.getContent().toString());
                }
            }
            return textContent.toString();
        }
        return "";
    }
    
    /**
     * テキストから日付を抽出
     */
    private LocalDate extractDate(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                return LocalDate.of(year, month, day);
            } catch (NumberFormatException | DateTimeParseException e) {
                logger.debug("日付の抽出に失敗しました: {}", e.getMessage());
            }
        }
        
        // その他の日付形式を試行（例: 2025/11/22）
        Pattern altPattern = Pattern.compile("(\\d{4})/(\\d{1,2})/(\\d{1,2})");
        matcher = altPattern.matcher(text);
        if (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                return LocalDate.of(year, month, day);
            } catch (NumberFormatException | DateTimeParseException e) {
                logger.debug("日付の抽出に失敗しました: {}", e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * テキストから時間を抽出
     */
    private LocalTime extractTime(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        Matcher matcher = TIME_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                int hour = Integer.parseInt(matcher.group(1));
                int minute = Integer.parseInt(matcher.group(2));
                return LocalTime.of(hour, minute);
            } catch (NumberFormatException e) {
                logger.debug("時間の抽出に失敗しました: {}", e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * 講師名を抽出（オプション）
     */
    private String extractTeacher(String text) {
        // 講師名の抽出ロジック（必要に応じて実装）
        // 例: "Teacher Vanessa" や "講師: Vanessa" などのパターン
        Pattern teacherPattern = Pattern.compile("(?:講師|Teacher|teacher)[:：]?\\s*([A-Za-z]+)");
        Matcher matcher = teacherPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * 予約情報を保持する内部クラス
     */
    private static class ReservationInfo {
        LocalDate date;
        LocalTime time;
        String teacher;
    }
    
    /**
     * メール監視が有効かどうか
     */
    private boolean isEmailMonitoringEnabled() {
        return config.isEmailMonitoringEnabled();
    }
}

