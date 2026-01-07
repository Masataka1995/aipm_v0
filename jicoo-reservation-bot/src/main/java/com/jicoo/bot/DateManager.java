package com.jicoo.bot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final Logger logger = LoggerFactory.getLogger(DateManager.class);
    private static final String DATA_DIR = "data";
    private static final String COMPLETED_RESERVATIONS_FILE = "completed-reservations.json";
    private static final String SELECTED_TEACHERS_FILE = "selected-teachers.json";
    private static final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    
    // 選択された先生のURLリスト（デフォルトはすべて選択）
    private List<String> selectedTeacherUrls;
    
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
        "13:00", "13:45", "14:30", "15:15",
        "16:00", "16:45", "17:30", "18:15", "19:00", "19:45"
    );
    
    private final List<DateInfo> dateList;
    private final List<LocalDate> completedReservations; // 予約完了日リスト（後方互換性のため保持）
    private final Map<LocalDate, List<String>> completedReservationsWithTimeSlots; // 予約完了日と時間帯のマッピング
    private final Map<LocalDate, String> completedReservationsWithTeacherUrl; // 予約完了日と先生URLのマッピング
    
    public DateManager() {
        this.dateList = new ArrayList<>();
        this.completedReservations = new ArrayList<>();
        this.completedReservationsWithTimeSlots = new HashMap<>();
        this.completedReservationsWithTeacherUrl = new HashMap<>();
        this.selectedTeacherUrls = new ArrayList<>();
        // デフォルトで今日と明日の日付を追加
        addTodayAndTomorrow();
        // 永続化されたデータを読み込む
        loadCompletedReservations();
        // 選択された先生を読み込む（デフォルトはすべて選択）
        loadSelectedTeachers();
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
        setReservationResult(date, success, timeSlots, null);
    }
    
    /**
     * 予約結果を設定（時間帯と先生URL付き）
     */
    public void setReservationResult(LocalDate date, boolean success, List<String> timeSlots, String teacherUrl) {
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
                // 先生URLも保存
                if (teacherUrl != null && !teacherUrl.isEmpty()) {
                    completedReservationsWithTeacherUrl.put(date, teacherUrl);
                    logger.info("予約完了: 日付={}, 先生URL={}", date, teacherUrl);
                } else {
                    logger.warn("予約完了: 日付={}, 先生URLが空です", date);
                }
                // データを永続化
                saveCompletedReservations();
            } else if (!success) {
                // 失敗した場合は完了リストから削除（もしあれば）
                completedReservations.remove(date);
                completedReservationsWithTimeSlots.remove(date);
                completedReservationsWithTeacherUrl.remove(date);
                // データを永続化
                saveCompletedReservations();
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
     * 予約完了日と時間帯のマッピングを取得（後方互換性のため保持）
     */
    public Map<LocalDate, List<String>> getCompletedReservationsWithTimeSlotsMap() {
        return new HashMap<>(completedReservationsWithTimeSlots);
    }
    
    /**
     * 予約完了日と先生URLのマッピングを取得
     */
    public Map<LocalDate, String> getCompletedReservationsWithTeacherUrl() {
        return new HashMap<>(completedReservationsWithTeacherUrl);
    }
    
    /**
     * 予約完了日と時間帯、先生URLを含む情報を取得（API用）
     */
    public List<Map<String, Object>> getCompletedReservationsWithDetails() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate date : completedReservations) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", date.toString());
            List<String> timeSlots = completedReservationsWithTimeSlots.get(date);
            item.put("timeSlots", timeSlots != null ? timeSlots : new ArrayList<>());
            String teacherUrl = completedReservationsWithTeacherUrl.get(date);
            item.put("teacherUrl", teacherUrl != null ? teacherUrl : "");
            result.add(item);
        }
        return result;
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
    
    /**
     * 予約完了データをJSONファイルから読み込む
     */
    private void loadCompletedReservations() {
        Path dataDir = Paths.get(DATA_DIR);
        Path filePath = dataDir.resolve(COMPLETED_RESERVATIONS_FILE);
        
        if (!Files.exists(filePath)) {
            logger.info("予約完了データファイルが存在しません: {}", filePath);
            return;
        }
        
        try (FileReader reader = new FileReader(filePath.toFile())) {
            List<Map<String, Object>> data = gson.fromJson(reader, new TypeToken<List<Map<String, Object>>>(){}.getType());
            
            if (data == null) {
                logger.warn("予約完了データファイルが空です: {}", filePath);
                return;
            }
            
            int loadedCount = 0;
            for (Map<String, Object> item : data) {
                try {
                    String dateStr = (String) item.get("date");
                    if (dateStr == null) {
                        continue;
                    }
                    
                    LocalDate date = LocalDate.parse(dateStr);
                    
                    // 日付が過去の場合はスキップ（レッスン日を越したものは保持しない）
                    if (date.isBefore(LocalDate.now())) {
                        continue;
                    }
                    
                    // 完了リストに追加
                    if (!completedReservations.contains(date)) {
                        completedReservations.add(date);
                    }
                    
                    // 時間帯を取得
                    @SuppressWarnings("unchecked")
                    List<String> timeSlots = (List<String>) item.get("timeSlots");
                    if (timeSlots != null && !timeSlots.isEmpty()) {
                        completedReservationsWithTimeSlots.put(date, new ArrayList<>(timeSlots));
                    }
                    
                    // 先生URLを取得
                    String url = (String) item.get("teacherUrl");
                    if (url != null && !url.isEmpty()) {
                        completedReservationsWithTeacherUrl.put(date, url);
                    }
                    
                    loadedCount++;
                } catch (Exception e) {
                    logger.warn("予約完了データの読み込み中にエラーが発生しました（スキップします）: {}", e.getMessage());
                }
            }
            
            logger.info("予約完了データを読み込みました: {}件", loadedCount);
        } catch (IOException e) {
            logger.warn("予約完了データファイルの読み込みに失敗しました: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("予約完了データの読み込み中に予期しないエラーが発生しました", e);
        }
    }
    
    /**
     * 予約完了データをJSONファイルに保存
     */
    private void saveCompletedReservations() {
        try {
            // データディレクトリを作成
            Path dataDir = Paths.get(DATA_DIR);
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                logger.debug("データディレクトリを作成しました: {}", dataDir);
            }
            
            Path filePath = dataDir.resolve(COMPLETED_RESERVATIONS_FILE);
            
            // データをJSON形式に変換
            List<Map<String, Object>> data = getCompletedReservationsWithDetails();
            
            // ファイルに書き込み
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                gson.toJson(data, writer);
                logger.debug("予約完了データを保存しました: {}件, ファイル: {}", data.size(), filePath);
            }
        } catch (IOException e) {
            logger.error("予約完了データファイルの保存に失敗しました: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("予約完了データの保存中に予期しないエラーが発生しました", e);
        }
    }
    
    /**
     * 選択された先生のURLリストを取得
     */
    public List<String> getSelectedTeacherUrls() {
        return new ArrayList<>(selectedTeacherUrls);
    }
    
    /**
     * 選択された先生のURLリストを設定
     */
    public void setSelectedTeacherUrls(List<String> urls) {
        this.selectedTeacherUrls = new ArrayList<>(urls);
        saveSelectedTeachers();
    }
    
    /**
     * 選択された先生を読み込む
     */
    private void loadSelectedTeachers() {
        Path dataDir = Paths.get(DATA_DIR);
        Path filePath = dataDir.resolve(SELECTED_TEACHERS_FILE);
        
        if (!Files.exists(filePath)) {
            logger.info("選択された先生のデータファイルが存在しません。デフォルト（すべて選択）を使用します: {}", filePath);
            // デフォルト：ConfigからすべてのURLを取得
            Config config = Config.getInstance();
            this.selectedTeacherUrls = new ArrayList<>(config.getUrls());
            saveSelectedTeachers();
            return;
        }
        
        try (FileReader reader = new FileReader(filePath.toFile())) {
            @SuppressWarnings("unchecked")
            List<String> loaded = gson.fromJson(reader, new TypeToken<List<String>>(){}.getType());
            if (loaded != null && !loaded.isEmpty()) {
                this.selectedTeacherUrls = new ArrayList<>(loaded);
                logger.info("選択された先生を読み込みました: {}件", selectedTeacherUrls.size());
            } else {
                // 空の場合はデフォルト（すべて選択）
                Config config = Config.getInstance();
                this.selectedTeacherUrls = new ArrayList<>(config.getUrls());
                saveSelectedTeachers();
            }
        } catch (Exception e) {
            logger.warn("選択された先生の読み込み中にエラーが発生しました。デフォルト（すべて選択）を使用します: {}", e.getMessage());
            Config config = Config.getInstance();
            this.selectedTeacherUrls = new ArrayList<>(config.getUrls());
        }
    }
    
    /**
     * 選択された先生を保存
     */
    private void saveSelectedTeachers() {
        try {
            // データディレクトリを作成
            Path dataDir = Paths.get(DATA_DIR);
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            
            Path filePath = dataDir.resolve(SELECTED_TEACHERS_FILE);
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                gson.toJson(selectedTeacherUrls, writer);
                logger.debug("選択された先生を保存しました: {}件", selectedTeacherUrls.size());
            }
        } catch (Exception e) {
            logger.error("選択された先生の保存中にエラーが発生しました", e);
        }
    }
}

