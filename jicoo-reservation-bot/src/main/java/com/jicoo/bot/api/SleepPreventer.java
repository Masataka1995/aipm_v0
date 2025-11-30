package com.jicoo.bot.api;

import com.sun.jna.Library;
import com.sun.jna.Native;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Windowsのスリープモードを防ぐユーティリティ
 */
public class SleepPreventer {
    private static final Logger logger = LoggerFactory.getLogger(SleepPreventer.class);
    
    // Windows API定数
    private static final int ES_CONTINUOUS = 0x80000000;
    private static final int ES_SYSTEM_REQUIRED = 0x00000001;
    private static final int ES_DISPLAY_REQUIRED = 0x00000002;
    
    private static boolean isPreventingSleep = false;
    private static java.util.concurrent.ScheduledExecutorService scheduler = null;
    
    // Windows APIインターフェース
    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        long SetThreadExecutionState(int esFlags);
    }
    
    /**
     * スリープモードを防ぐ
     * Windows以外のOSでは何もしない
     */
    public static void preventSleep() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("windows")) {
            logger.debug("Windows以外のOSのため、スリープ防止機能は使用しません");
            return;
        }
        
        try {
            // JNAを使用してWindows APIを呼び出す
            long result = Kernel32.INSTANCE.SetThreadExecutionState(
                ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED
            );
            
            if (result != 0) {
                isPreventingSleep = true;
                logger.info("スリープモードを防ぐ設定を有効にしました（監視中はスリープしません）");
                
                // 定期的にスリープ防止を更新（30秒ごと）
                // これにより、スリープモードに入った場合でも復帰後に再開できる
                if (scheduler == null || scheduler.isShutdown()) {
                    scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
                    scheduler.scheduleAtFixedRate(() -> {
                        try {
                            if (isPreventingSleep) {
                                Kernel32.INSTANCE.SetThreadExecutionState(
                                    ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED
                                );
                            }
                        } catch (Exception e) {
                            logger.debug("スリープ防止の更新中にエラーが発生しました（無視します）", e);
                        }
                    }, 30, 30, java.util.concurrent.TimeUnit.SECONDS);
                    logger.debug("スリープ防止の定期更新を開始しました（30秒ごと）");
                }
            } else {
                logger.warn("スリープモード防止の設定に失敗しました");
            }
        } catch (Exception e) {
            logger.warn("スリープモード防止機能の初期化に失敗しました（監視は続行します）", e);
        }
    }
    
    /**
     * スリープモード防止を解除
     */
    public static void allowSleep() {
        if (!isPreventingSleep) {
            return;
        }
        
        // 定期更新スケジューラーを停止
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("windows")) {
            return;
        }
        
        try {
            Kernel32.INSTANCE.SetThreadExecutionState(ES_CONTINUOUS);
            isPreventingSleep = false;
            logger.info("スリープモード防止を解除しました");
        } catch (Exception e) {
            logger.warn("スリープモード防止の解除に失敗しました", e);
        }
    }
    
    /**
     * スリープモード防止が有効かどうか
     */
    public static boolean isPreventingSleep() {
        return isPreventingSleep;
    }
}

