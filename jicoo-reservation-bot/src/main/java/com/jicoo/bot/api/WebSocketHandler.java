package com.jicoo.bot.api;

import com.google.gson.Gson;
import com.jicoo.bot.GUILogAppender;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocketハンドラー
 * リアルタイムログ配信とイベント通知
 */
public class WebSocketHandler implements WebSocketListener {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    private static final Set<Session> sessions = new CopyOnWriteArraySet<>();
    private static final Gson gson = new Gson();
    
    private Session session;
    
    @Override
    public void onWebSocketConnect(Session session) {
        this.session = session;
        sessions.add(session);
        logger.info("【WebSocket】接続が確立されました: {}", session.getRemoteAddress());
        logger.info("【WebSocket】現在の接続数: {}", sessions.size());
        
        // セッションのアイドルタイムアウトを無効化（0 = 無制限）
        try {
            session.setIdleTimeout(java.time.Duration.ZERO);
        } catch (Exception e) {
            logger.debug("セッションのアイドルタイムアウト設定に失敗しました（無視）", e);
        }
        
        // ログアペンダーにWebSocketハンドラーを登録
        GUILogAppender.setWebSocketHandler(this);
    }
    
    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        if (session != null) {
            sessions.remove(session);
        }
        logger.info("【WebSocket】接続が切断されました: ステータスコード={}, 理由={}", statusCode, reason);
        logger.info("【WebSocket】現在の接続数: {}", sessions.size());
    }
    
    @Override
    public void onWebSocketError(Throwable cause) {
        // タイムアウトエラーは警告レベルで処理（正常な動作の一部）
        String errorClass = cause.getClass().getName();
        if (errorClass.contains("Timeout") || errorClass.contains("Idle")) {
            logger.debug("【WebSocket】アイドルタイムアウト（正常な動作）: {}", cause.getMessage());
            // タイムアウトは正常な切断なので、セッションを削除
            if (session != null) {
                sessions.remove(session);
            }
        } else {
            // その他のエラーは警告レベルで記録
            logger.warn("【WebSocket】エラーが発生しました: {}", cause.getMessage());
            logger.debug("【WebSocket】エラーの詳細", cause);
            if (session != null) {
                sessions.remove(session);
            }
        }
    }
    
    @Override
    public void onWebSocketText(String message) {
        // クライアントからのメッセージ処理（必要に応じて実装）
    }
    
    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        // バイナリメッセージは使用しない
    }
    
    /**
     * ログメッセージをブロードキャスト
     */
    // 定数：メッセージタイプ
    private static final String TYPE_LOG = "log";
    private static final String TYPE_RESERVATION_RESULT = "reservationResult";
    private static final String TYPE_STATUS = "status";
    
    public static void broadcastLog(String message) {
        Map<String, String> logMessage = new HashMap<>(4); // 初期サイズを指定
        logMessage.put("type", TYPE_LOG);
        logMessage.put("message", message);
        broadcast(gson.toJson(logMessage));
    }
    
    /**
     * 予約結果をブロードキャスト
     */
    public static void broadcastReservationResult(LocalDate date, boolean success) {
        Map<String, Object> result = new HashMap<>(4); // 初期サイズを指定
        result.put("type", TYPE_RESERVATION_RESULT);
        result.put("date", date.toString());
        result.put("success", success);
        broadcast(gson.toJson(result));
    }
    
    /**
     * ステータス更新をブロードキャスト
     */
    public static void broadcastStatus(String status) {
        Map<String, String> statusMessage = new HashMap<>(4); // 初期サイズを指定
        statusMessage.put("type", TYPE_STATUS);
        statusMessage.put("status", status);
        broadcast(gson.toJson(statusMessage));
    }
    
    private static void broadcast(String message) {
        sessions.removeIf(session -> {
            try {
                if (session.isOpen()) {
                    session.getRemote().sendString(message);
                    return false;
                } else {
                    return true;
                }
            } catch (IOException e) {
                logger.warn("WebSocket送信エラー", e);
                return true;
            }
        });
    }
}

