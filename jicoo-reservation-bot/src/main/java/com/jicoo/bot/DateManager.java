package com.jicoo.bot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日付管理クラス
 * 予約対象日付の管理と状態を保持
 */
public class DateManager {
    /**
     * 予約結果の状態
     */
    public enum ReservationStatus {
        PENDING,   // 未実行
        SUCCESS,   // 成功（緑）
        FAILED     // 失敗（赤）
    }
    
    /**
     * 日付情報を保持する内部クラス
     */
    public static class DateInfo {
        private final LocalDate date;
        private boolean enabled;
        private ReservationStatus status;
        private List<String> selectedTimeSlots; // 選択された時間帯のリスト
        
        public DateInfo(LocalDate date, boolean enabled) {
            this.date = date;
            this.enabled = enabled;
            this.status = ReservationStatus.PENDING;
            this.selectedTimeSlots = new ArrayList<>();
        }
        
        public LocalDate getDate() {
            return date;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public ReservationStatus getStatus() {
            return status;
        }
        
        public void setStatus(ReservationStatus status) {
            this.status = status;
        }
        
        public List<String> getSelectedTimeSlots() {
            return new ArrayList<>(selectedTimeSlots);
        }
        
        public void setSelectedTimeSlots(List<String> timeSlots) {
            this.selectedTimeSlots = new ArrayList<>(timeSlots);
        }
        
        public void addTimeSlot(String timeSlot) {
            if (!selectedTimeSlots.contains(timeSlot)) {
                selectedTimeSlots.add(timeSlot);
            }
        }
        
        public void removeTimeSlot(String timeSlot) {
            selectedTimeSlots.remove(timeSlot);
        }
        
        public boolean hasTimeSlot(String timeSlot) {
            return selectedTimeSlots.contains(timeSlot);
        }
        
        public String getFormattedDate() {
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd (E)", java.util.Locale.JAPANESE));
        }
    }
    
    /**
     * 利用可能な時間帯のリスト
     */
    public static final List<String> AVAILABLE_TIME_SLOTS = List.of(
        "9:45", "10:30", "11:15", "12:00",
        "16:00", "16:45", "17:30", "18:15", "19:00", "19:45", "20:25"
    );
    
    private final List<DateInfo> dateList;
    private final List<LocalDate> completedReservations; // 予約完了日リスト（後方互換性のため保持）
    private final Map<LocalDate, List<String>> completedReservationsWithTimeSlots; // 予約完了日と時間帯のマッピング
    
    public DateManager() {
        this.dateList = new ArrayList<>();
        this.completedReservations = new ArrayList<>();
        this.completedReservationsWithTimeSlots = new HashMap<>();
        // デフォルトで今日と明日の日付を追加
        addTodayAndTomorrow();
    }
    
    /**
     * 今日と明日の日付を追加
     */
    private void addTodayAndTomorrow() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        dateList.add(new DateInfo(today, true));
        dateList.add(new DateInfo(tomorrow, true));
    }
    
    /**
     * 日付を追加
     */
    public void addDate(LocalDate date) {
        // 重複チェック
        for (DateInfo info : dateList) {
            if (info.getDate().equals(date)) {
                return; // 既に存在する場合は追加しない
            }
        }
        dateList.add(new DateInfo(date, true));
    }
    
    /**
     * 日付を削除
     */
    public boolean removeDate(LocalDate date) {
        return dateList.removeIf(info -> info.getDate().equals(date));
    }
    
    /**
     * 日付のON/OFFを切り替え
     */
    public void toggleDate(LocalDate date) {
        for (DateInfo info : dateList) {
            if (info.getDate().equals(date)) {
                info.setEnabled(!info.isEnabled());
                return;
            }
        }
    }
    
    /**
     * 有効な日付リストを取得
     */
    public List<LocalDate> getEnabledDates() {
        List<LocalDate> enabledDates = new ArrayList<>();
        for (DateInfo info : dateList) {
            if (info.isEnabled()) {
                enabledDates.add(info.getDate());
            }
        }
        return enabledDates;
    }
    
    /**
     * 有効な日付とその時間帯のマッピングを取得
     */
    public Map<LocalDate, List<String>> getEnabledDatesWithTimeSlots() {
        Map<LocalDate, List<String>> result = new java.util.HashMap<>();
        for (DateInfo info : dateList) {
            if (info.isEnabled()) {
                List<String> timeSlots = info.getSelectedTimeSlots();
                // 時間帯が選択されていない場合はデフォルト時間を使用
                if (timeSlots.isEmpty()) {
                    // デフォルトは設定ファイルから取得（後で実装）
                    timeSlots = new ArrayList<>();
                }
                result.put(info.getDate(), timeSlots);
            }
        }
        return result;
    }
    
    /**
     * すべての日付情報を取得
     */
    public List<DateInfo> getAllDateInfo() {
        return new ArrayList<>(dateList);
    }
    
    /**
     * 日付情報を取得
     */
    public DateInfo getDateInfo(LocalDate date) {
        for (DateInfo info : dateList) {
            if (info.getDate().equals(date)) {
                return info;
            }
        }
        return null;
    }
    
    /**
     * 予約結果を設定
     */
    public void setReservationResult(LocalDate date, boolean success) {
        setReservationResult(date, success, null);
    }
    
    /**
     * 予約結果を設定（時間帯付き）
     */
    public void setReservationResult(LocalDate date, boolean success, List<String> timeSlots) {
        DateInfo info = getDateInfo(date);
        if (info != null) {
            info.setStatus(success ? ReservationStatus.SUCCESS : ReservationStatus.FAILED);
            
            // 成功した場合は完了日リストに追加（重複チェック）
            if (success && !completedReservations.contains(date)) {
                completedReservations.add(date);
                // 時間帯も保存
                if (timeSlots != null && !timeSlots.isEmpty()) {
                    completedReservationsWithTimeSlots.put(date, new ArrayList<>(timeSlots));
                } else {
                    // 時間帯が指定されていない場合は、DateInfoから取得
                    List<String> infoTimeSlots = info.getSelectedTimeSlots();
                    if (!infoTimeSlots.isEmpty()) {
                        completedReservationsWithTimeSlots.put(date, new ArrayList<>(infoTimeSlots));
                    }
                }
            }
        }
    }
    
    /**
     * 予約完了日リストを取得
     */
    public List<LocalDate> getCompletedReservations() {
        return new ArrayList<>(completedReservations);
    }
    
    /**
     * 予約完了日と時間帯のマッピングを取得
     */
    public Map<LocalDate, List<String>> getCompletedReservationsWithTimeSlots() {
        return new HashMap<>(completedReservationsWithTimeSlots);
    }
    
    /**
     * 過去の予約完了日を削除（レッスン日を越した日付）
     */
    public void removePastCompletedReservations() {
        LocalDate today = LocalDate.now();
        completedReservations.removeIf(date -> date.isBefore(today));
    }
    
    /**
     * 予約完了日を削除
     */
    public boolean removeCompletedReservation(LocalDate date) {
        return completedReservations.remove(date);
    }
    
    /**
     * すべての日付の状態をリセット
     */
    public void resetAllStatus() {
        for (DateInfo info : dateList) {
            info.setStatus(ReservationStatus.PENDING);
        }
    }
}

