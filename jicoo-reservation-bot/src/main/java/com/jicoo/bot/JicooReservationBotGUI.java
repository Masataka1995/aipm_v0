package com.jicoo.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.openqa.selenium.WebDriver;

/**
 * Jicoo自動予約BOT GUIアプリケーション
 * Swingベースのシンプルな一画面アプリケーション
 */
public class JicooReservationBotGUI extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(JicooReservationBotGUI.class);
    
    private JButton startButton;
    private JButton stopButton;
    private JButton manualReserveButton; // 手動予約ボタン
    private JTextArea logArea;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    
    // 日付管理関連
    private DateManager dateManager;
    private JPanel dateListPanel;
    private JButton addDateButton;
    private Map<LocalDate, JPanel> datePanelMap;
    
    // 時間帯表示関連
    private JTextArea timeSlotsArea;
    
    // 予約完了日表示関連
    private JPanel completedReservationsListPanel;
    private javax.swing.Timer cleanupTimer;
    
    // 監視時間設定関連
    private JToggleButton monitoringTimeToggleButton;
    private JLabel monitoringTimeLabel;
    private JLabel monitoringTimeStatusLabel;
    private javax.swing.Timer monitoringTimeStatusTimer;
    
    private ExecutorService executorService;
    private JicooReservationBot bot;
    private boolean isRunning = false;
    
    // システムトレイ関連
    private SystemTray systemTray;
    private TrayIcon trayIcon;
    private boolean minimizeToTray = true;
    
    public JicooReservationBotGUI() {
        this.dateManager = new DateManager();
        this.datePanelMap = new HashMap<>();
        initializeGUI();
        setupLogAppender();
        setupSystemTray();
        setupCleanupTimer();
    }
    
    /**
     * GUIを初期化
     */
    private void initializeGUI() {
        setTitle("Jicoo 自動予約 BOT");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1400, 800);
        setLocationRelativeTo(null);
        
        // ウィンドウを閉じたときの処理
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (minimizeToTray && systemTray != null && trayIcon != null) {
                    // トレイに最小化
                    setVisible(false);
                    appendLog("アプリケーションをシステムトレイに最小化しました");
                } else {
                    // 終了確認
                    exitApplication();
                }
            }
        });
        
        // メインパネル
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(245, 245, 250));
        
        // 上部パネル（タイトルとステータス）
        JPanel topPanel = new JPanel(new BorderLayout(15, 10));
        topPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createRaisedBevelBorder(),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        topPanel.setBackground(Color.WHITE);
        
        // タイトル
        JLabel titleLabel = new JLabel("Jicoo 自動予約 BOT");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        titleLabel.setForeground(new Color(33, 33, 33));
        topPanel.add(titleLabel, BorderLayout.NORTH);
        
        // ステータス表示パネル
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusPanel.setBackground(Color.WHITE);
        statusLabel = new JLabel("状態: 待機中");
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        statusLabel.setForeground(new Color(33, 150, 243));
        statusPanel.add(statusLabel);
        
        // 監視時間ON/OFFボタンを上部に追加（常時表示）
        JLabel monitoringTimeLabelTop = new JLabel("監視時間制限:");
        monitoringTimeLabelTop.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        monitoringTimeLabelTop.setForeground(new Color(66, 66, 66));
        statusPanel.add(monitoringTimeLabelTop);
        
        monitoringTimeToggleButton = new JToggleButton();
        monitoringTimeToggleButton.setSelected(Config.getInstance().isMonitoringTimeRestrictionEnabled());
        updateMonitoringTimeToggleButton();
        monitoringTimeToggleButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        monitoringTimeToggleButton.setPreferredSize(new Dimension(70, 30));
        monitoringTimeToggleButton.setFocusPainted(false);
        monitoringTimeToggleButton.addActionListener(e -> {
            boolean enabled = monitoringTimeToggleButton.isSelected();
            Config.getInstance().setMonitoringTimeRestrictionEnabled(enabled);
            updateMonitoringTimeToggleButton();
            if (monitoringTimeStatusLabel != null) {
                updateMonitoringTimeStatus(monitoringTimeStatusLabel);
            }
            appendLog(String.format("監視時間制限を%sにしました", enabled ? "有効" : "無効"));
        });
        statusPanel.add(monitoringTimeToggleButton);
        
        // 監視時間ステータスラベル（上部にも表示）
        monitoringTimeStatusLabel = new JLabel();
        monitoringTimeStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        updateMonitoringTimeStatus(monitoringTimeStatusLabel);
        statusPanel.add(monitoringTimeStatusLabel);
        
        topPanel.add(statusPanel, BorderLayout.CENTER);
        
        // プログレスバー
        progressBar = new JProgressBar();
        progressBar.setStringPainted(false);
        progressBar.setIndeterminate(false);
        progressBar.setPreferredSize(new Dimension(0, 8));
        progressBar.setBorderPainted(false);
        topPanel.add(progressBar, BorderLayout.SOUTH);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // 中央パネル（日付管理、時間帯表示、ログ表示を横並び）
        JPanel centerPanel = new JPanel(new BorderLayout(15, 0));
        
        // 左側パネル（日付管理、監視時間設定、予約完了日を縦並び）
        JPanel leftPanel = new JPanel(new BorderLayout(0, 15));
        
        // 日付管理パネル
        JPanel dateManagementPanel = createDateManagementPanel();
        leftPanel.add(dateManagementPanel, BorderLayout.NORTH);
        
        // 監視時間設定パネル
        JPanel monitoringTimePanel = createMonitoringTimePanel();
        leftPanel.add(monitoringTimePanel, BorderLayout.CENTER);
        
        // 予約完了日パネル
        JPanel completedReservationsPanel = createCompletedReservationsPanel();
        leftPanel.add(completedReservationsPanel, BorderLayout.SOUTH);
        
        centerPanel.add(leftPanel, BorderLayout.WEST);
        
        // 時間帯表示パネル（右側のログパネルの下に配置）
        JPanel timeSlotsPanel = createTimeSlotsPanel();
        centerPanel.add(timeSlotsPanel, BorderLayout.EAST);
        
        // 右側：ログ表示
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLoweredBevelBorder(),
            "ログ出力",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 14),
            new Color(66, 66, 66)
        ));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(0, 255, 0));
        logArea.setCaretColor(new Color(0, 255, 0));
        logArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setBackground(new Color(50, 50, 50));
        logPanel.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(logPanel, BorderLayout.CENTER);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // 下部パネル（ボタン）
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        buttonPanel.setBackground(new Color(245, 245, 250));
        
        startButton = new JButton("▶ 開始");
        startButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        startButton.setPreferredSize(new Dimension(140, 50));
        startButton.setBackground(new Color(76, 175, 80));
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.setBorder(BorderFactory.createRaisedBevelBorder());
        startButton.addActionListener(new StartButtonListener());
        
        stopButton = new JButton("■ 停止");
        stopButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        stopButton.setPreferredSize(new Dimension(140, 50));
        stopButton.setBackground(new Color(244, 67, 54));
        stopButton.setForeground(Color.WHITE);
        stopButton.setFocusPainted(false);
        stopButton.setBorder(BorderFactory.createRaisedBevelBorder());
        stopButton.setEnabled(false);
        stopButton.addActionListener(new StopButtonListener());
        
        manualReserveButton = new JButton("🔁 手動予約");
        manualReserveButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        manualReserveButton.setPreferredSize(new Dimension(140, 50));
        manualReserveButton.setBackground(new Color(33, 150, 243));
        manualReserveButton.setForeground(Color.WHITE);
        manualReserveButton.setFocusPainted(false);
        manualReserveButton.setBorder(BorderFactory.createRaisedBevelBorder());
        manualReserveButton.setToolTipText("予約が成功した日付を手動で再予約できます");
        manualReserveButton.addActionListener(e -> showManualReservationDialogForAll());
        
        JButton checkTimeSlotsButton = new JButton("⏰ 時間帯確認");
        checkTimeSlotsButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        checkTimeSlotsButton.setPreferredSize(new Dimension(140, 50));
        checkTimeSlotsButton.setBackground(new Color(156, 39, 176));
        checkTimeSlotsButton.setForeground(Color.WHITE);
        checkTimeSlotsButton.setFocusPainted(false);
        checkTimeSlotsButton.setBorder(BorderFactory.createRaisedBevelBorder());
        checkTimeSlotsButton.setToolTipText("選択した日付とURLで利用可能な時間帯を確認します");
        checkTimeSlotsButton.addActionListener(e -> showCheckTimeSlotsDialog());
        
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(manualReserveButton);
        buttonPanel.add(checkTimeSlotsButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // 日付リストを更新
        updateDateList();
        
        // 初期ログ
        appendLog("========================================");
        appendLog("Jicoo 自動予約 BOT 起動");
        appendLog("========================================");
        appendLog("");
        appendLog("【自動設定】");
        appendLog("今日と明日の日付が自動的に追加されました");
        List<DateManager.DateInfo> initialDates = dateManager.getAllDateInfo();
        for (DateManager.DateInfo info : initialDates) {
            appendLog("  - " + info.getFormattedDate() + (info.isEnabled() ? " [ON]" : " [OFF]"));
        }
        appendLog("");
        appendLog("【使い方】");
        appendLog("1. 各日付の「ON/OFF」ボタンで有効/無効を切り替え");
        appendLog("2. 必要に応じて「日付を追加」ボタンで追加の日付を追加");
        appendLog("3. 「⏰ 時間帯確認」ボタンで利用可能な時間帯を確認");
        appendLog("4. 「開始」ボタンをクリックして予約監視を開始");
        appendLog("   → 4つのURL × 選択された日付を並行監視します");
        appendLog("5. 予約成功後、「🔁 再予約」ボタンで手動予約が可能");
        appendLog("");
        appendLog("【結果表示】");
        appendLog("✓ 緑色: 予約成功（再予約ボタンが表示されます）");
        appendLog("✗ 赤色: 予約失敗");
        appendLog("");
    }
    
    /**
     * 日付管理パネルを作成
     */
    private JPanel createDateManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createRaisedBevelBorder(),
            "📅 予約対象日付管理",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 15),
            new Color(66, 66, 66)
        ));
        panel.setPreferredSize(new Dimension(380, 400));
        panel.setBackground(new Color(250, 250, 255));
        
        // 日付追加ボタン
        addDateButton = new JButton("➕ 日付を追加");
        addDateButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        addDateButton.setPreferredSize(new Dimension(0, 45));
        addDateButton.setBackground(new Color(33, 150, 243));
        addDateButton.setForeground(Color.WHITE);
        addDateButton.setFocusPainted(false);
        addDateButton.setBorder(BorderFactory.createRaisedBevelBorder());
        addDateButton.addActionListener(e -> showAddDateDialog());
        panel.add(addDateButton, BorderLayout.NORTH);
        
        // 日付リストパネル
        dateListPanel = new JPanel();
        dateListPanel.setLayout(new BoxLayout(dateListPanel, BoxLayout.Y_AXIS));
        dateListPanel.setBackground(new Color(250, 250, 255));
        dateListPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane dateScrollPane = new JScrollPane(dateListPanel);
        dateScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        dateScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLoweredBevelBorder(),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        dateScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        dateScrollPane.getVerticalScrollBar().setBackground(new Color(240, 240, 245));
        panel.add(dateScrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 監視時間設定パネルを作成
     */
    private JPanel createMonitoringTimePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createRaisedBevelBorder(),
            "⏰ 監視時間設定",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 15),
            new Color(66, 66, 66)
        ));
        panel.setPreferredSize(new Dimension(380, 120));
        panel.setBackground(new Color(250, 250, 255));
        
        // 中央パネル（情報表示とボタン）
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBackground(new Color(250, 250, 255));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // 監視時間情報ラベル
        Config config = Config.getInstance();
        String timeInfo = String.format("監視時間: %d時〜%d時（日本時間）", 
            config.getMonitoringStartHour(), config.getMonitoringEndHour());
        monitoringTimeLabel = new JLabel(timeInfo);
        monitoringTimeLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        monitoringTimeLabel.setForeground(new Color(66, 66, 66));
        centerPanel.add(monitoringTimeLabel, BorderLayout.NORTH);
        
        // 現在の状態表示（上部にも表示されているが、詳細情報としてここにも表示）
        JLabel statusLabelDetail = new JLabel();
        statusLabelDetail.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        // 上部のラベルと同じ内容を表示するため、updateMonitoringTimeStatusを使用
        Config configForStatus = Config.getInstance();
        boolean restrictionEnabled = Config.getInstance().isMonitoringTimeRestrictionEnabled();
        boolean withinHours = configForStatus.isWithinMonitoringHours();
        
        if (restrictionEnabled) {
            if (withinHours) {
                statusLabelDetail.setText("✓ 現在は監視時間内です");
                statusLabelDetail.setForeground(new Color(76, 175, 80));
            } else {
                long secondsUntilStart = configForStatus.getSecondsUntilMonitoringStart();
                if (secondsUntilStart > 0) {
                    long hours = secondsUntilStart / 3600;
                    long minutes = (secondsUntilStart % 3600) / 60;
                    statusLabelDetail.setText(String.format("⏸ 監視時間外（あと%d時間%d分）", hours, minutes));
                } else {
                    statusLabelDetail.setText("⏸ 監視時間外");
                }
                statusLabelDetail.setForeground(new Color(244, 67, 54));
            }
        } else {
            statusLabelDetail.setText("✓ 監視時間制限は無効です（24時間監視）");
            statusLabelDetail.setForeground(new Color(33, 150, 243));
        }
        centerPanel.add(statusLabelDetail, BorderLayout.CENTER);
        
        // 注意: ON/OFFボタンは上部のステータスパネルに移動済み
        JLabel noteLabel = new JLabel("※ ON/OFFボタンは上部に表示されています");
        noteLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
        noteLabel.setForeground(new Color(150, 150, 150));
        centerPanel.add(noteLabel, BorderLayout.SOUTH);
        
        panel.add(centerPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 監視時間トグルボタンの表示を更新
     */
    private void updateMonitoringTimeToggleButton() {
        boolean enabled = Config.getInstance().isMonitoringTimeRestrictionEnabled();
        monitoringTimeToggleButton.setText(enabled ? "ON" : "OFF");
        monitoringTimeToggleButton.setSelected(enabled);
        if (enabled) {
            monitoringTimeToggleButton.setBackground(new Color(76, 175, 80));
            monitoringTimeToggleButton.setForeground(Color.WHITE);
        } else {
            monitoringTimeToggleButton.setBackground(new Color(158, 158, 158));
            monitoringTimeToggleButton.setForeground(Color.WHITE);
        }
    }
    
    /**
     * 監視時間ステータスラベルの表示を更新
     */
    private void updateMonitoringTimeStatus(JLabel statusLabel) {
        Config config = Config.getInstance();
        boolean restrictionEnabled = config.isMonitoringTimeRestrictionEnabled();
        boolean withinHours = config.isWithinMonitoringHours();
        
        if (restrictionEnabled) {
            if (withinHours) {
                statusLabel.setText("✓ 現在は監視時間内です");
                statusLabel.setForeground(new Color(76, 175, 80));
            } else {
                long secondsUntilStart = config.getSecondsUntilMonitoringStart();
                if (secondsUntilStart > 0) {
                    long hours = secondsUntilStart / 3600;
                    long minutes = (secondsUntilStart % 3600) / 60;
                    statusLabel.setText(String.format("⏸ 監視時間外（あと%d時間%d分）", hours, minutes));
                } else {
                    statusLabel.setText("⏸ 監視時間外");
                }
                statusLabel.setForeground(new Color(244, 67, 54));
            }
        } else {
            statusLabel.setText("✓ 監視時間制限は無効です（24時間監視）");
            statusLabel.setForeground(new Color(33, 150, 243));
        }
    }
    
    /**
     * 予約完了日パネルを作成
     */
    private JPanel createCompletedReservationsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createRaisedBevelBorder(),
            "✅ 予約完了日",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 15),
            new Color(66, 66, 66)
        ));
        panel.setPreferredSize(new Dimension(380, 150));
        panel.setBackground(new Color(250, 250, 255));
        
        // 予約完了日リストパネル
        completedReservationsListPanel = new JPanel();
        completedReservationsListPanel.setLayout(new BoxLayout(completedReservationsListPanel, BoxLayout.Y_AXIS));
        completedReservationsListPanel.setBackground(new Color(250, 250, 255));
        completedReservationsListPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        
        JScrollPane completedScrollPane = new JScrollPane(completedReservationsListPanel);
        completedScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        completedScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLoweredBevelBorder(),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        completedScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        completedScrollPane.getVerticalScrollBar().setBackground(new Color(240, 240, 245));
        panel.add(completedScrollPane, BorderLayout.CENTER);
        
        // 初期表示を更新
        updateCompletedReservationsList();
        
        return panel;
    }
    
    /**
     * 予約完了日リストを更新
     */
    private void updateCompletedReservationsList() {
        SwingUtilities.invokeLater(() -> {
            completedReservationsListPanel.removeAll();
            
            List<LocalDate> completedDates = dateManager.getCompletedReservations();
            if (completedDates.isEmpty()) {
                JLabel emptyLabel = new JLabel("予約完了日はありません");
                emptyLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
                emptyLabel.setForeground(new Color(150, 150, 150));
                emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                emptyLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                completedReservationsListPanel.add(emptyLabel);
            } else {
                for (LocalDate date : completedDates) {
                    JPanel dateItemPanel = createCompletedDateItemPanel(date);
                    completedReservationsListPanel.add(dateItemPanel);
                    completedReservationsListPanel.add(Box.createVerticalStrut(5));
                }
            }
            
            completedReservationsListPanel.revalidate();
            completedReservationsListPanel.repaint();
        });
    }
    
    /**
     * 予約完了日アイテムパネルを作成
     */
    private JPanel createCompletedDateItemPanel(LocalDate date) {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(76, 175, 80), 2),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        panel.setBackground(new Color(240, 255, 240));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        
        JLabel dateLabel = new JLabel("✓ " + date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE)));
        dateLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        dateLabel.setForeground(new Color(46, 125, 50));
        panel.add(dateLabel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * クリーンアップタイマーを設定（過去の予約完了日を削除）
     */
    private void setupCleanupTimer() {
        // 1時間ごとに過去の日付を削除
        cleanupTimer = new javax.swing.Timer(3600000, e -> {
            int removedCount = dateManager.getCompletedReservations().size();
            dateManager.removePastCompletedReservations();
            int remainingCount = dateManager.getCompletedReservations().size();
            
            if (removedCount != remainingCount) {
                updateCompletedReservationsList();
                appendLog(String.format("過去の予約完了日を削除しました（削除: %d件）", removedCount - remainingCount));
            }
        });
        cleanupTimer.start();
        
        // 監視時間ステータス更新タイマー（1分ごと）
        monitoringTimeStatusTimer = new javax.swing.Timer(60000, e -> {
            // 上部のステータスラベルを更新
            if (monitoringTimeStatusLabel != null) {
                updateMonitoringTimeStatus(monitoringTimeStatusLabel);
            }
            // 監視時間設定パネル内のラベルも更新（再描画をトリガー）
            SwingUtilities.invokeLater(() -> {
                // 監視時間設定パネルを再描画
                if (monitoringTimeLabel != null) {
                    // パネル全体を再描画
                    repaint();
                }
            });
        });
        monitoringTimeStatusTimer.start();
    }
    
    /**
     * 時間帯表示パネルを作成
     */
    private JPanel createTimeSlotsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createRaisedBevelBorder(),
            "⏰ 利用可能な時間帯",
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font(Font.SANS_SERIF, Font.BOLD, 15),
            new Color(66, 66, 66)
        ));
        panel.setPreferredSize(new Dimension(380, 200));
        panel.setBackground(new Color(250, 250, 255));
        
        // 時間帯表示エリア
        timeSlotsArea = new JTextArea();
        timeSlotsArea.setEditable(false);
        timeSlotsArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        timeSlotsArea.setBackground(new Color(255, 255, 255));
        timeSlotsArea.setForeground(new Color(33, 33, 33));
        timeSlotsArea.setMargin(new Insets(10, 10, 10, 10));
        timeSlotsArea.setText("「⏰ 時間帯確認」ボタンで\n利用可能な時間帯を確認できます");
        timeSlotsArea.setLineWrap(true);
        timeSlotsArea.setWrapStyleWord(true);
        
        JScrollPane timeSlotsScrollPane = new JScrollPane(timeSlotsArea);
        timeSlotsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        timeSlotsScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLoweredBevelBorder(),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        timeSlotsScrollPane.getVerticalScrollBar().setBackground(new Color(240, 240, 245));
        panel.add(timeSlotsScrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 日付追加ダイアログを表示
     */
    private void showAddDateDialog() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
        
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(
            LocalDate.now().getYear(), 2020, 2100, 1));
        JSpinner monthSpinner = new JSpinner(new SpinnerNumberModel(
            LocalDate.now().getMonthValue(), 1, 12, 1));
        JSpinner daySpinner = new JSpinner(new SpinnerNumberModel(
            LocalDate.now().getDayOfMonth(), 1, 31, 1));
        
        panel.add(new JLabel("年:"));
        panel.add(yearSpinner);
        panel.add(new JLabel("月:"));
        panel.add(monthSpinner);
        panel.add(new JLabel("日:"));
        panel.add(daySpinner);
        
        int result = JOptionPane.showConfirmDialog(
            this,
            panel,
            "日付を追加",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        
        if (result == JOptionPane.OK_OPTION) {
            try {
                int year = (Integer) yearSpinner.getValue();
                int month = (Integer) monthSpinner.getValue();
                int day = (Integer) daySpinner.getValue();
                LocalDate date = LocalDate.of(year, month, day);
                
                if (date.isBefore(LocalDate.now())) {
                    JOptionPane.showMessageDialog(this, "過去の日付は追加できません", 
                        "エラー", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                dateManager.addDate(date);
                updateDateList();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "無効な日付です: " + e.getMessage(), 
                    "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * 日付リストを更新
     */
    private void updateDateList() {
        SwingUtilities.invokeLater(() -> {
            dateListPanel.removeAll();
            datePanelMap.clear();
            
            List<DateManager.DateInfo> dateInfoList = dateManager.getAllDateInfo();
            for (DateManager.DateInfo info : dateInfoList) {
                JPanel dateItemPanel = createDateItemPanel(info);
                dateListPanel.add(dateItemPanel);
                datePanelMap.put(info.getDate(), dateItemPanel);
            }
            
            dateListPanel.revalidate();
            dateListPanel.repaint();
        });
    }
    
    /**
     * 日付アイテムパネルを作成
     */
    private JPanel createDateItemPanel(DateManager.DateInfo info) {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 200), 2),
            BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));
        panel.setBackground(new Color(255, 255, 255));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        
        // メインパネル（縦並び）
        JPanel mainContentPanel = new JPanel(new BorderLayout(8, 8));
        mainContentPanel.setBackground(Color.WHITE);
        
        // 上部パネル（日付ラベルとボタン）
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        topPanel.setBackground(Color.WHITE);
        
        // 左側：日付ラベル
        JPanel dateLabelPanel = new JPanel(new BorderLayout());
        dateLabelPanel.setBackground(Color.WHITE);
        JLabel dateLabel = new JLabel(info.getFormattedDate());
        dateLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        updateDateLabelColor(dateLabel, info);
        dateLabelPanel.add(dateLabel, BorderLayout.WEST);
        topPanel.add(dateLabelPanel, BorderLayout.CENTER);
        
        // 右側：ボタンパネル
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setBackground(Color.WHITE);
        
        // 手動予約ボタン（予約成功時のみ表示）
        if (info.getStatus() == DateManager.ReservationStatus.SUCCESS) {
            JButton manualButton = new JButton("🔁 再予約");
            manualButton.setPreferredSize(new Dimension(95, 32));
            manualButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            manualButton.setBackground(new Color(33, 150, 243));
            manualButton.setForeground(Color.WHITE);
            manualButton.setFocusPainted(false);
            manualButton.setBorder(BorderFactory.createRaisedBevelBorder());
            manualButton.addActionListener(e -> showManualReservationDialog(info.getDate()));
            buttonPanel.add(manualButton);
        }
        
        // ON/OFF切り替えボタン
        JToggleButton toggleButton = new JToggleButton(info.isEnabled() ? "ON" : "OFF");
        toggleButton.setSelected(info.isEnabled());
        toggleButton.setPreferredSize(new Dimension(65, 32));
        toggleButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        toggleButton.setFocusPainted(false);
        if (info.isEnabled()) {
            toggleButton.setBackground(new Color(76, 175, 80));
            toggleButton.setForeground(Color.WHITE);
        } else {
            toggleButton.setBackground(new Color(158, 158, 158));
            toggleButton.setForeground(Color.WHITE);
        }
        toggleButton.addActionListener(e -> {
            dateManager.toggleDate(info.getDate());
            boolean enabled = dateManager.getDateInfo(info.getDate()).isEnabled();
            toggleButton.setText(enabled ? "ON" : "OFF");
            toggleButton.setSelected(enabled);
            if (enabled) {
                toggleButton.setBackground(new Color(76, 175, 80));
            } else {
                toggleButton.setBackground(new Color(158, 158, 158));
            }
        });
        buttonPanel.add(toggleButton);
        
        // 削除ボタン
        JButton deleteButton = new JButton("✕");
        deleteButton.setPreferredSize(new Dimension(32, 32));
        deleteButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        deleteButton.setBackground(new Color(244, 67, 54));
        deleteButton.setForeground(Color.WHITE);
        deleteButton.setFocusPainted(false);
        deleteButton.setBorder(BorderFactory.createRaisedBevelBorder());
        deleteButton.addActionListener(e -> {
            dateManager.removeDate(info.getDate());
            updateDateList();
        });
        buttonPanel.add(deleteButton);
        
        topPanel.add(buttonPanel, BorderLayout.EAST);
        mainContentPanel.add(topPanel, BorderLayout.NORTH);
        
        // 時間帯選択パネル
        JPanel timeSlotsPanel = createTimeSlotsSelectionPanel(info);
        mainContentPanel.add(timeSlotsPanel, BorderLayout.CENTER);
        
        panel.add(mainContentPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 時間帯選択パネルを作成
     */
    private JPanel createTimeSlotsSelectionPanel(DateManager.DateInfo info) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 240), 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        
        // ラベル
        JLabel label = new JLabel("時間帯選択:");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        label.setForeground(new Color(100, 100, 100));
        panel.add(label, BorderLayout.NORTH);
        
        // 時間帯チェックボックスパネル
        JPanel checkboxesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        checkboxesPanel.setBackground(Color.WHITE);
        
        List<String> availableTimeSlots = DateManager.AVAILABLE_TIME_SLOTS;
        List<String> selectedTimeSlots = info.getSelectedTimeSlots();
        
        for (String timeSlot : availableTimeSlots) {
            JCheckBox checkBox = new JCheckBox(timeSlot);
            checkBox.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            checkBox.setSelected(selectedTimeSlots.contains(timeSlot));
            checkBox.setBackground(Color.WHITE);
            checkBox.addActionListener(e -> {
                if (checkBox.isSelected()) {
                    dateManager.getDateInfo(info.getDate()).addTimeSlot(timeSlot);
                } else {
                    dateManager.getDateInfo(info.getDate()).removeTimeSlot(timeSlot);
                }
            });
            checkboxesPanel.add(checkBox);
        }
        
        JScrollPane scrollPane = new JScrollPane(checkboxesPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(0, 50));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * 時間帯確認ダイアログを表示
     */
    private void showCheckTimeSlotsDialog() {
        Config config = Config.getInstance();
        List<String> urls = config.getUrls();
        
        if (urls.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "予約対象URLが設定されていません",
                "エラー",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 日付選択
        List<DateManager.DateInfo> allDates = dateManager.getAllDateInfo();
        if (allDates.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "日付が設定されていません。\nまず日付を追加してください。",
                "エラー",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String[] dateArray = allDates.stream()
            .map(info -> info.getFormattedDate())
            .toArray(String[]::new);
        
        String selectedDateStr = (String) JOptionPane.showInputDialog(
            this,
            "時間帯を確認する日付を選択してください:",
            "時間帯確認 - 日付選択",
            JOptionPane.QUESTION_MESSAGE,
            null,
            dateArray,
            dateArray[0]
        );
        
        if (selectedDateStr == null) {
            return;
        }
        
        LocalDate targetDate = null;
        for (DateManager.DateInfo info : allDates) {
            if (info.getFormattedDate().equals(selectedDateStr)) {
                targetDate = info.getDate();
                break;
            }
        }
        
        if (targetDate == null) {
            return;
        }
        
        // URL選択
        String[] urlArray = urls.toArray(new String[0]);
        String selectedUrl = (String) JOptionPane.showInputDialog(
            this,
            "時間帯を確認するURLを選択してください:\n日付: " + targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE)),
            "時間帯確認 - URL選択",
            JOptionPane.QUESTION_MESSAGE,
            null,
            urlArray,
            urlArray[0]
        );
        
        if (selectedUrl == null) {
            return;
        }
        
        // 時間帯を取得
        appendLog("========================================");
        appendLog("時間帯確認を開始します");
        appendLog("日付: " + targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE)));
        appendLog("URL: " + selectedUrl);
        appendLog("========================================");
        
        // 別スレッドで時間帯を取得
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newCachedThreadPool();
        }
        
        final LocalDate finalTargetDate = targetDate;
        final String finalSelectedUrl = selectedUrl;
        executorService.submit(() -> {
            try {
                WebDriver driver = DriverManager.createWebDriver(
                    config.isHeadless(),
                    config.getTimeoutSeconds(),
                    config.getImplicitWaitSeconds()
                );
                
                if (driver == null) {
                    SwingUtilities.invokeLater(() -> {
                        appendLog("エラー: WebDriverの作成に失敗しました");
                        JOptionPane.showMessageDialog(this,
                            "WebDriverの作成に失敗しました",
                            "エラー",
                            JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }
                
                try {
                    ReservationService reservationService = new ReservationService(config);
                    
                    // URLへアクセス
                    driver.get(finalSelectedUrl);
                    appendLog("ページにアクセスしました");
                    Thread.sleep(2000);
                    
                    // ログインポップアップ処理
                    if (!reservationService.handleLoginPopup(driver)) {
                        SwingUtilities.invokeLater(() -> {
                            appendLog("ログイン処理に失敗しました");
                            JOptionPane.showMessageDialog(this,
                                "ログイン処理に失敗しました",
                                "エラー",
                                JOptionPane.ERROR_MESSAGE);
                        });
                        return;
                    }
                    
                    // 日付を選択
                    if (!reservationService.selectDate(driver, finalTargetDate)) {
                        SwingUtilities.invokeLater(() -> {
                            appendLog("日付選択に失敗しました");
                            JOptionPane.showMessageDialog(this,
                                "日付選択に失敗しました",
                                "エラー",
                                JOptionPane.ERROR_MESSAGE);
                        });
                        return;
                    }
                    
                    appendLog("日付を選択しました。タイムスロットを取得中...");
                    Thread.sleep(2000);
                    
                    // 利用可能な時間帯を取得
                    List<String> availableSlots = reservationService.getAvailableTimeSlots(driver);
                    
                    final List<String> finalAvailableSlots = availableSlots;
                    SwingUtilities.invokeLater(() -> {
                        // UIに時間帯を表示
                        updateTimeSlotsDisplay(finalTargetDate, finalSelectedUrl, finalAvailableSlots);
                        
                        StringBuilder message = new StringBuilder();
                        message.append("利用可能な時間帯:\n\n");
                        message.append("日付: ").append(finalTargetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE))).append("\n");
                        message.append("URL: ").append(finalSelectedUrl).append("\n\n");
                        
                        if (finalAvailableSlots.isEmpty()) {
                            message.append("利用可能な時間帯が見つかりませんでした。\n");
                            message.append("（すべての時間帯が予約済み、または非表示の可能性があります）");
                            appendLog("利用可能な時間帯: なし");
                        } else {
                            message.append("【利用可能な時間帯】\n");
                            for (String slot : finalAvailableSlots) {
                                message.append("  ✓ ").append(slot).append("\n");
                                appendLog("利用可能: " + slot);
                            }
                        }
                        
                        JOptionPane.showMessageDialog(this,
                            message.toString(),
                            "時間帯確認結果",
                            JOptionPane.INFORMATION_MESSAGE);
                    });
                    
                } finally {
                    try {
                        DriverManager.closeWebDriver(driver);
                    } catch (Exception e) {
                        logger.warn("WebDriverのクリーンアップ中にエラー: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.error("時間帯確認中にエラーが発生しました", e);
                SwingUtilities.invokeLater(() -> {
                    appendLog("エラー: " + e.getMessage());
                    JOptionPane.showMessageDialog(this,
                        "時間帯確認中にエラーが発生しました: " + e.getMessage(),
                        "エラー",
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }
    
    /**
     * すべての成功した日付から手動予約を選択
     */
    private void showManualReservationDialogForAll() {
        List<DateManager.DateInfo> successDates = dateManager.getAllDateInfo().stream()
            .filter(info -> info.getStatus() == DateManager.ReservationStatus.SUCCESS)
            .collect(java.util.stream.Collectors.toList());
        
        if (successDates.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "予約が成功した日付がありません。\nまず自動予約を実行してください。",
                "情報",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 日付選択ダイアログ
        String[] dateArray = successDates.stream()
            .map(info -> info.getFormattedDate())
            .toArray(String[]::new);
        
        String selectedDateStr = (String) JOptionPane.showInputDialog(
            this,
            "再予約する日付を選択してください:",
            "手動予約 - 日付選択",
            JOptionPane.QUESTION_MESSAGE,
            null,
            dateArray,
            dateArray[0]
        );
        
        if (selectedDateStr == null) {
            return; // キャンセル
        }
        
        // 選択された日付を取得
        LocalDate targetDate = null;
        for (DateManager.DateInfo info : successDates) {
            if (info.getFormattedDate().equals(selectedDateStr)) {
                targetDate = info.getDate();
                break;
            }
        }
        
        if (targetDate == null) {
            return;
        }
        
        showManualReservationDialog(targetDate);
    }
    
    /**
     * 手動予約ダイアログを表示
     */
    private void showManualReservationDialog(LocalDate targetDate) {
        Config config = Config.getInstance();
        List<String> urls = config.getUrls();
        
        if (urls.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "予約対象URLが設定されていません",
                "エラー",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // URL選択ダイアログ
        String[] urlArray = urls.toArray(new String[0]);
        String selectedUrl = (String) JOptionPane.showInputDialog(
            this,
            "予約するURLを選択してください:\n日付: " + targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE)),
            "手動予約",
            JOptionPane.QUESTION_MESSAGE,
            null,
            urlArray,
            urlArray[0]
        );
        
        if (selectedUrl == null) {
            return; // キャンセル
        }
        
        // 確認ダイアログ
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "以下の内容で予約を実行しますか？\n\n" +
            "日付: " + targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE)) + "\n" +
            "URL: " + selectedUrl + "\n" +
            "時間: " + config.getTargetTime(),
            "手動予約確認",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        // 手動予約を実行
        appendLog("========================================");
        appendLog("手動予約を開始します");
        appendLog("日付: " + targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE)));
        appendLog("URL: " + selectedUrl);
        appendLog("========================================");
        
        // 別スレッドで予約を実行
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newCachedThreadPool();
        }
        
        executorService.submit(() -> {
            try {
                WebDriver driver = DriverManager.createWebDriver(
                    config.isHeadless(),
                    config.getTimeoutSeconds(),
                    config.getImplicitWaitSeconds()
                );
                
                if (driver == null) {
                    SwingUtilities.invokeLater(() -> {
                        appendLog("エラー: WebDriverの作成に失敗しました");
                        JOptionPane.showMessageDialog(this,
                            "WebDriverの作成に失敗しました",
                            "エラー",
                            JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }
                
                try {
                    ReservationService reservationService = new ReservationService(config);
                    boolean success = reservationService.processUrl(driver, selectedUrl, targetDate);
                    
                    SwingUtilities.invokeLater(() -> {
                        if (success) {
                            appendLog("手動予約が成功しました！");
                            updateDateResult(targetDate, true);
                            JOptionPane.showMessageDialog(this,
                                "予約が成功しました！\n\n" +
                                "日付: " + targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE)) + "\n" +
                                "URL: " + selectedUrl,
                                "予約成功",
                                JOptionPane.INFORMATION_MESSAGE);
                            
                            // トレイアイコンに通知
                            if (trayIcon != null) {
                                trayIcon.displayMessage(
                                    "Jicoo 自動予約 BOT",
                                    "手動予約が成功しました: " + targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                                    TrayIcon.MessageType.INFO
                                );
                            }
                        } else {
                            appendLog("手動予約に失敗しました");
                            updateDateResult(targetDate, false);
                            JOptionPane.showMessageDialog(this,
                                "予約に失敗しました。\n\n" +
                                "日付: " + targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE)) + "\n" +
                                "URL: " + selectedUrl + "\n\n" +
                                "ログを確認してください。",
                                "予約失敗",
                                JOptionPane.WARNING_MESSAGE);
                        }
                    });
                } finally {
                    try {
                        DriverManager.closeWebDriver(driver);
                    } catch (Exception e) {
                        logger.warn("WebDriverのクリーンアップ中にエラー: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.error("手動予約中にエラーが発生しました", e);
                SwingUtilities.invokeLater(() -> {
                    appendLog("エラー: " + e.getMessage());
                    JOptionPane.showMessageDialog(this,
                        "予約中にエラーが発生しました: " + e.getMessage(),
                        "エラー",
                        JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }
    
    /**
     * 時間帯表示を更新
     */
    private void updateTimeSlotsDisplay(LocalDate date, String url, List<String> availableSlots) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder text = new StringBuilder();
            text.append("【最新の確認結果】\n");
            text.append("日付: ").append(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE))).append("\n");
            text.append("URL: ").append(url.length() > 40 ? url.substring(0, 37) + "..." : url).append("\n\n");
            
            if (availableSlots.isEmpty()) {
                text.append("利用可能な時間帯: なし\n");
                text.append("（すべて予約済みの可能性があります）");
            } else {
                text.append("【利用可能な時間帯】\n");
                for (int i = 0; i < availableSlots.size(); i++) {
                    text.append("  ✓ ").append(availableSlots.get(i));
                    if ((i + 1) % 3 == 0) {
                        text.append("\n");
                    } else if (i < availableSlots.size() - 1) {
                        text.append("  ");
                    }
                }
                if (availableSlots.size() % 3 != 0) {
                    text.append("\n");
                }
                text.append("\n合計: ").append(availableSlots.size()).append("件");
            }
            
            timeSlotsArea.setText(text.toString());
            timeSlotsArea.setCaretPosition(0);
        });
    }
    
    /**
     * 日付ラベルの色を更新
     */
    private void updateDateLabelColor(JLabel label, DateManager.DateInfo info) {
        switch (info.getStatus()) {
            case SUCCESS:
                label.setForeground(new Color(46, 125, 50)); // 濃い緑
                label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                label.setText("✓ " + info.getFormattedDate());
                break;
            case FAILED:
                label.setForeground(new Color(198, 40, 40)); // 濃い赤
                label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
                label.setText("✗ " + info.getFormattedDate());
                break;
            default:
                label.setForeground(new Color(66, 66, 66));
                label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
                label.setText(info.getFormattedDate());
                break;
        }
    }
    
    /**
     * 日付の予約結果を更新
     */
    public void updateDateResult(LocalDate date, boolean success) {
        dateManager.setReservationResult(date, success);
        SwingUtilities.invokeLater(() -> {
            DateManager.DateInfo info = dateManager.getDateInfo(date);
            if (info != null) {
                JPanel datePanel = datePanelMap.get(date);
                if (datePanel != null) {
                    // ラベルを探して更新
                    for (Component comp : datePanel.getComponents()) {
                        if (comp instanceof JLabel) {
                            updateDateLabelColor((JLabel) comp, info);
                            break;
                        }
                    }
                    datePanel.repaint();
                }
                
                // 予約成功時は完了日リストを更新
                if (success) {
                    updateCompletedReservationsList();
                }
                
                // トレイアイコンに通知を表示
                if (trayIcon != null) {
                    String message = success 
                        ? String.format("予約成功: %s", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                        : String.format("予約失敗: %s", date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    TrayIcon.MessageType messageType = success 
                        ? TrayIcon.MessageType.INFO 
                        : TrayIcon.MessageType.WARNING;
                    trayIcon.displayMessage("Jicoo 自動予約 BOT", message, messageType);
                }
            }
        });
    }
    
    /**
     * ログアペンダーを設定（GUIにログを表示）
     */
    private void setupLogAppender() {
        // GUIログアペンダーを設定
        GUILogAppender.setLogTextArea(logArea);
        
        // Logbackのコンテキストにアペンダーを追加
        ch.qos.logback.classic.LoggerContext loggerContext = 
            (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        
        GUILogAppender guiAppender = new GUILogAppender();
        guiAppender.setContext(loggerContext);
        guiAppender.start();
        
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(guiAppender);
    }
    
    /**
     * ログエリアにメッセージを追加
     */
    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    /**
     * ステータスを更新
     */
    private void updateStatus(String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("状態: " + status);
            statusLabel.setForeground(color);
            // ステータスに応じてアイコンを追加
            String icon = "";
            if (status.contains("実行中")) {
                icon = "⏳ ";
            } else if (status.contains("完了")) {
                icon = "✓ ";
            } else if (status.contains("エラー")) {
                icon = "⚠ ";
            } else if (status.contains("停止")) {
                icon = "■ ";
            } else {
                icon = "○ ";
            }
            statusLabel.setText(icon + "状態: " + status);
        });
    }
    
    /**
     * 開始ボタンのリスナー
     */
    private class StartButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (isRunning) {
                return;
            }
            
            isRunning = true;
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            progressBar.setIndeterminate(true);
            updateStatus("実行中...", Color.ORANGE);
            
            appendLog("----------------------------------------");
            appendLog("予約監視を開始します");
            appendLog("----------------------------------------");
            
            // 有効な日付と時間帯のマッピングを取得
            Map<LocalDate, List<String>> datesWithTimeSlots = dateManager.getEnabledDatesWithTimeSlots();
            if (datesWithTimeSlots.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(JicooReservationBotGUI.this, 
                        "予約対象日付が設定されていません。\n日付を追加してONにしてください。", 
                        "警告", JOptionPane.WARNING_MESSAGE);
                });
                isRunning = false;
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                progressBar.setIndeterminate(false);
                updateStatus("待機中", Color.BLUE);
                return;
            }
            
            // 時間帯が選択されていない日付をチェック
            List<LocalDate> datesWithoutTimeSlots = new ArrayList<>();
            for (Map.Entry<LocalDate, List<String>> entry : datesWithTimeSlots.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    datesWithoutTimeSlots.add(entry.getKey());
                }
            }
            
            if (!datesWithoutTimeSlots.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    StringBuilder message = new StringBuilder();
                    message.append("以下の日付に時間帯が選択されていません:\n\n");
                    for (LocalDate date : datesWithoutTimeSlots) {
                        message.append("  - ").append(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE))).append("\n");
                    }
                    message.append("\n時間帯を選択してから開始してください。");
                    JOptionPane.showMessageDialog(JicooReservationBotGUI.this, 
                        message.toString(), 
                        "警告", JOptionPane.WARNING_MESSAGE);
                });
                isRunning = false;
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                progressBar.setIndeterminate(false);
                updateStatus("待機中", Color.BLUE);
                return;
            }
            
            appendLog("予約対象日付数: " + datesWithTimeSlots.size());
            for (Map.Entry<LocalDate, List<String>> entry : datesWithTimeSlots.entrySet()) {
                appendLog("  - " + entry.getKey().format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE)) + 
                         " (時間帯: " + String.join(", ", entry.getValue()) + ")");
            }
            
            // 別スレッドでBOTを実行
            executorService = Executors.newSingleThreadExecutor();
            executorService.submit(() -> {
                try {
                    bot = new JicooReservationBot();
                    // GUIコールバックを設定
                    bot.setReservationCallback((date, success) -> {
                        updateDateResult(date, success);
                    });
                    // 有効な日付と時間帯のマッピングを渡して監視開始
                    bot.startMonitoring(datesWithTimeSlots);
                    
                    // 正常終了時の処理
                    SwingUtilities.invokeLater(() -> {
                        isRunning = false;
                        startButton.setEnabled(true);
                        stopButton.setEnabled(false);
                        progressBar.setIndeterminate(false);
                        updateStatus("完了", Color.GREEN);
                        
                        // トレイアイコンに通知
                        if (trayIcon != null) {
                            trayIcon.displayMessage(
                                "Jicoo 自動予約 BOT",
                                "すべての予約処理が完了しました",
                                TrayIcon.MessageType.INFO
                            );
                        }
                    });
                } catch (Exception ex) {
                    logger.error("BOT実行中にエラーが発生しました", ex);
                    SwingUtilities.invokeLater(() -> {
                        isRunning = false;
                        startButton.setEnabled(true);
                        stopButton.setEnabled(false);
                        progressBar.setIndeterminate(false);
                        updateStatus("エラー", Color.RED);
                    });
                }
            });
        }
    }
    
    /**
     * 停止ボタンのリスナー
     */
    private class StopButtonListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (!isRunning) {
                return;
            }
            
            appendLog("----------------------------------------");
            appendLog("予約監視を停止します");
            appendLog("----------------------------------------");
            
            isRunning = false;
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            progressBar.setIndeterminate(false);
            updateStatus("停止中...", Color.GRAY);
            
            // BOTを停止
            if (bot != null) {
                bot.stopMonitoring();
            }
            
            // スレッドプールを終了
            if (executorService != null) {
                executorService.shutdown();
            }
            
            updateStatus("停止", Color.BLUE);
            appendLog("予約監視を停止しました");
        }
    }
    
    /**
     * アプリケーションを起動
     */
    public static void main(String[] args) {
        // Look and Feelを設定（システムのデフォルト）
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // デフォルトのLook and Feelを使用
        }
        
        SwingUtilities.invokeLater(() -> {
            JicooReservationBotGUI gui = new JicooReservationBotGUI();
            gui.setVisible(true);
        });
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
            
            // トレイアイコンの画像を作成（簡易版）
            Image image = createTrayIconImage();
            
            // ポップアップメニューを作成
            PopupMenu popup = new PopupMenu();
            
            MenuItem showItem = new MenuItem("表示");
            showItem.addActionListener(e -> {
                setVisible(true);
                setState(Frame.NORMAL);
                toFront();
            });
            popup.add(showItem);
            
            popup.addSeparator();
            
            MenuItem exitItem = new MenuItem("終了");
            exitItem.addActionListener(e -> exitApplication());
            popup.add(exitItem);
            
            // トレイアイコンを作成
            trayIcon = new TrayIcon(image, "Jicoo 自動予約 BOT", popup);
            trayIcon.setImageAutoSize(true);
            
            // ダブルクリックでウィンドウを表示
            trayIcon.addActionListener(e -> {
                setVisible(true);
                setState(Frame.NORMAL);
                toFront();
            });
            
            // システムトレイに追加
            systemTray.add(trayIcon);
            
            // 初期通知
            trayIcon.displayMessage(
                "Jicoo 自動予約 BOT",
                "アプリケーションが起動しました\nシステムトレイでバックグラウンド実行中",
                TrayIcon.MessageType.INFO
            );
            
            logger.info("システムトレイを設定しました");
        } catch (Exception e) {
            logger.error("システムトレイの設定に失敗しました", e);
        }
    }
    
    /**
     * トレイアイコン用の画像を作成
     */
    private Image createTrayIconImage() {
        // 簡易的なアイコン画像を作成
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 背景
        g.setColor(new Color(33, 150, 243));
        g.fillRoundRect(0, 0, 16, 16, 3, 3);
        
        // 文字 "J"
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString("J", 5, 13);
        
        g.dispose();
        return image;
    }
    
    /**
     * アプリケーションを終了
     */
    private void exitApplication() {
        if (isRunning) {
            int result = JOptionPane.showConfirmDialog(
                this,
                "実行中の処理があります。終了しますか？",
                "確認",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        // タイマーを停止
        if (cleanupTimer != null) {
            cleanupTimer.stop();
        }
        if (monitoringTimeStatusTimer != null) {
            monitoringTimeStatusTimer.stop();
        }
        
        // リソースをクリーンアップ
        if (bot != null) {
            bot.stopMonitoring();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
        }
        
        System.exit(0);
    }
}

