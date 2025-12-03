package com.jicoo.bot;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;

/**
 * WebDriver管理クラス
 * ChromeDriverの初期化と設定を行う
 */
public class DriverManager {
    private static final Logger logger = LoggerFactory.getLogger(DriverManager.class);
    
    // インスタンス化を防ぐためのprivateコンストラクタ
    private DriverManager() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * WebDriverを作成
     * @param headless ヘッドレスモードで起動するか
     * @param timeoutSeconds タイムアウト（秒）
     * @param implicitWaitSeconds 暗黙的な待機時間（秒）
     * @return 初期化されたWebDriver
     */
    public static WebDriver createWebDriver(boolean headless, int timeoutSeconds, int implicitWaitSeconds) {
        logger.info("WebDriverを初期化しています... (headless={})", headless);
        
        // ChromeDriverを自動取得
        io.github.bonigarcia.wdm.WebDriverManager.chromedriver().setup();
        
        // ChromeOptionsの設定
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless");
            logger.info("ヘッドレスモードで起動します");
        }
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", 
            Collections.singletonList("enable-automation"));
        
        // WebDriverの作成
        WebDriver driver = new ChromeDriver(options);
        
        // タイムアウト設定
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(timeoutSeconds));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(implicitWaitSeconds));
        
        // ウィンドウを最大化
        driver.manage().window().maximize();
        
        logger.info("WebDriverの初期化が完了しました");
        return driver;
    }
    
    /**
     * WebDriverを終了（エラーを無視して安全に終了）
     * @param driver 終了するWebDriver
     */
    public static void closeWebDriver(WebDriver driver) {
        closeWebDriver(driver, false);
    }
    
    /**
     * WebDriverを終了
     * @param driver 終了するWebDriver
     * @param silent エラーをログに出力しない場合true（デフォルト: false）
     */
    public static void closeWebDriver(WebDriver driver, boolean silent) {
        if (driver == null) {
            return;
        }
        
        try {
            // まず通常の終了を試みる
            driver.quit();
            if (!silent) {
                logger.info("WebDriverを終了しました");
            }
        } catch (Exception e) {
            // InterruptedExceptionが原因の場合は警告レベル（シャットダウン処理が中断されただけ）
            Throwable cause = e.getCause();
            if (cause instanceof InterruptedException || e instanceof InterruptedException) {
                if (!silent) {
                    logger.warn("WebDriverの終了処理が中断されました（無視して続行）: {}", e.getMessage());
                }
                // スレッドの割り込み状態を復元
                Thread.currentThread().interrupt();
            } else {
                // その他のエラーも警告レベル（WebDriverは既に終了している可能性がある）
                if (!silent) {
                    logger.warn("WebDriverの終了中にエラーが発生しました（無視して続行）: {}", e.getMessage());
                }
            }
            
            // quit()が失敗した場合、close()を試みる
            try {
                driver.close();
                if (!silent) {
                    logger.debug("WebDriverをclose()で終了しました");
                }
            } catch (Exception closeException) {
                if (!silent) {
                    logger.debug("WebDriverのclose()も失敗しました（無視）: {}", closeException.getMessage());
                }
            }
        }
    }
}

