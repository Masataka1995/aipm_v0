package com.jicoo.bot;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.jicoo.bot.api.WebSocketHandler;

import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * GUIにログを表示するカスタムアペンダー
 * WebSocketにもログを送信
 */
public class GUILogAppender extends AppenderBase<ILoggingEvent> {
    private static JTextArea logTextArea;
    private static WebSocketHandler webSocketHandler;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    /**
     * ログ表示用のテキストエリアを設定
     */
    public static void setLogTextArea(JTextArea textArea) {
        logTextArea = textArea;
    }
    
    /**
     * WebSocketハンドラーを設定
     */
    public static void setWebSocketHandler(WebSocketHandler handler) {
        webSocketHandler = handler;
    }
    
    @Override
    protected void append(ILoggingEvent event) {
        // ログメッセージをフォーマット
        String timestamp = dateFormat.format(new Date(event.getTimeStamp()));
        String level = event.getLevel().toString();
        String message = event.getFormattedMessage();
        String logLine = String.format("%s [%s] %s", timestamp, level, message);
        
        // Swing GUIに表示
        if (logTextArea != null) {
            SwingUtilities.invokeLater(() -> {
                if (logTextArea != null) {
                    logTextArea.append(logLine + "\n");
                    logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
                }
            });
        }
        
        // WebSocketに送信
        if (webSocketHandler != null) {
            WebSocketHandler.broadcastLog(logLine);
        }
    }
}

