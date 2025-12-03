package com.jicoo.bot.api;

import com.google.gson.Gson;
import com.jicoo.bot.Config;
import com.jicoo.bot.DateManager;
import com.jicoo.bot.JicooReservationBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST APIサーブレット
 */
public class ApiServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ApiServlet.class);
    
    // 定数定義
    private static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String ERROR_KEY = "error";
    private static final String SUCCESS_KEY = "success";
    private static final String MESSAGE_KEY = "message";
    private static final String ENABLED_KEY = "enabled";
    private static final String API_ERROR_MSG = "APIリクエスト処理中にエラーが発生しました";
    private static final String DATES_PATH_PREFIX = "/dates/";
    private static final String UNKNOWN_ENDPOINT_MSG = "Unknown endpoint: ";
    
    private final Gson gson;
    private final DateManager dateManager;
    private final JicooReservationBot bot;
    private final RestApiServer server;
    
    public ApiServlet(Gson gson, DateManager dateManager, JicooReservationBot bot, RestApiServer server) {
        this.gson = gson;
        this.dateManager = dateManager;
        this.bot = bot;
        this.server = server;
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleRequest(req, resp, () -> {
            String path = getPath(req);
            return handleGetRequest(path);
        });
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleRequest(req, resp, () -> {
            String path = getPath(req);
            String body = readRequestBody(req);
            return handlePostRequest(path, body);
        });
    }
    
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleRequest(req, resp, () -> {
            String path = getPath(req);
            String body = readRequestBody(req);
            return handlePutRequest(path, body);
        });
    }
    
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleRequest(req, resp, () -> {
            String path = getPath(req);
            return handleDeleteRequest(path);
        });
    }
    
    /**
     * 共通のリクエスト処理
     */
    private void handleRequest(HttpServletRequest req, HttpServletResponse resp, RequestHandler handler) throws IOException {
        resp.setContentType(CONTENT_TYPE_JSON);
        resp.setCharacterEncoding(CHARSET_UTF8);
        
        try {
            String response = handler.handle();
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(response);
        } catch (Exception e) {
            logger.error(API_ERROR_MSG, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeErrorResponse(resp, e.getMessage());
        }
    }
    
    /**
     * パス情報を取得
     */
    private String getPath(HttpServletRequest req) {
        String path = req.getPathInfo();
        return path != null ? path : "/";
    }
    
    /**
     * エラーレスポンスを書き込み
     */
    private void writeErrorResponse(HttpServletResponse resp, String message) throws IOException {
        Map<String, String> error = new HashMap<>();
        error.put(ERROR_KEY, message);
        resp.getWriter().write(gson.toJson(error));
    }
    
    /**
     * 成功レスポンスを生成
     */
    private String createSuccessResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put(SUCCESS_KEY, true);
        response.put(MESSAGE_KEY, message);
        return gson.toJson(response);
    }
    
    /**
     * リクエストハンドラーインターフェース
     */
    @FunctionalInterface
    private interface RequestHandler {
        String handle() throws Exception;
    }
    
    private String handleGetRequest(String path) {
        if (path.equals("/status")) {
            Map<String, Object> status = new HashMap<>();
            status.put("running", server.isRunning());
            Config config = Config.getInstance();
            status.put("monitoringTimeRestriction", config.isMonitoringTimeRestrictionEnabled());
            status.put("monitoringStartHour", config.getMonitoringStartHour());
            status.put("monitoringEndHour", config.getMonitoringEndHour());
            status.put("withinMonitoringHours", config.isWithinMonitoringHours());
            return gson.toJson(status);
        } else if (path.equals("/dates")) {
            List<Map<String, Object>> dates = dateManager.getAllDateInfo().stream()
                .map(info -> {
                    Map<String, Object> dateMap = new HashMap<>();
                    dateMap.put("date", info.getDate());
                    dateMap.put(ENABLED_KEY, info.isEnabled());
                    dateMap.put("status", info.getStatus().name());
                    dateMap.put("selectedTimeSlots", info.getSelectedTimeSlots());
                    return dateMap;
                })
                .toList();
            return gson.toJson(dates);
        } else if (path.equals("/completed-reservations")) {
            // 時間帯と先生URL情報を含む形式で返す
            List<Map<String, Object>> result = dateManager.getCompletedReservationsWithDetails();
            return gson.toJson(result);
        } else if (path.equals("/config")) {
            Config config = Config.getInstance();
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("monitoringStartHour", config.getMonitoringStartHour());
            configMap.put("monitoringEndHour", config.getMonitoringEndHour());
            configMap.put("monitoringIntervalSeconds", config.getMonitoringIntervalSeconds());
            configMap.put("monitoringTimeRestriction", config.isMonitoringTimeRestrictionEnabled());
            return gson.toJson(configMap);
        } else {
            throw new IllegalArgumentException(UNKNOWN_ENDPOINT_MSG + path);
        }
    }
    
    private String handlePostRequest(String path, String body) {
        if (path.equals("/monitoring/start")) {
            // 別スレッドで監視を開始
            new Thread(() -> {
                try {
                    Map<LocalDate, List<String>> datesWithTimeSlots = dateManager.getEnabledDatesWithTimeSlots();
                    bot.startMonitoring(datesWithTimeSlots);
                } catch (Exception e) {
                    logger.error("監視開始中にエラーが発生しました", e);
                }
            }).start();
            
            return createSuccessResponse("監視を開始しました");
        } else if (path.equals("/monitoring/stop")) {
            bot.stopMonitoring();
            return createSuccessResponse("監視を停止しました");
        } else if (path.equals("/dates")) {
            Map<String, Object> request = gson.fromJson(body, Map.class);
            String dateStr = (String) request.get("date");
            LocalDate date = LocalDate.parse(dateStr);
            dateManager.addDate(date);
            
            return createSuccessResponse("日付を追加しました");
        } else if (path.equals("/manual-reserve")) {
            Map<String, Object> request = gson.fromJson(body, Map.class);
            String dateStr = (String) request.get("date");
            String url = (String) request.get("url");
            LocalDate date = LocalDate.parse(dateStr);
            
            // 手動予約を実行
            new Thread(() -> {
                try {
                    // 手動予約の実装
                    logger.info("手動予約を開始: date={}, url={}", date, url);
                } catch (Exception e) {
                    logger.error("手動予約中にエラーが発生しました", e);
                }
            }).start();
            
            return createSuccessResponse("手動予約を開始しました");
        } else {
            throw new IllegalArgumentException(UNKNOWN_ENDPOINT_MSG + path);
        }
    }
    
    private String handlePutRequest(String path, String body) {
        if (path.startsWith(DATES_PATH_PREFIX)) {
            String dateStr = path.substring(DATES_PATH_PREFIX.length());
            LocalDate date = LocalDate.parse(dateStr);
            Map<String, Object> request = gson.fromJson(body, Map.class);
            
            if (request.containsKey(ENABLED_KEY)) {
                boolean enabled = (Boolean) request.get(ENABLED_KEY);
                if (enabled != dateManager.getDateInfo(date).isEnabled()) {
                    dateManager.toggleDate(date);
                }
            }
            
            if (request.containsKey("timeSlots")) {
                @SuppressWarnings("unchecked")
                List<String> timeSlots = (List<String>) request.get("timeSlots");
                DateManager.DateInfo info = dateManager.getDateInfo(date);
                if (info != null) {
                    info.setSelectedTimeSlots(timeSlots);
                }
            }
            
            return createSuccessResponse("日付を更新しました");
        } else if (path.equals("/config/monitoring-time-restriction")) {
            Map<String, Object> request = gson.fromJson(body, Map.class);
            boolean enabled = (Boolean) request.get(ENABLED_KEY);
            Config.getInstance().setMonitoringTimeRestrictionEnabled(enabled);
            
            return createSuccessResponse("監視時間制限を更新しました");
        } else {
            throw new IllegalArgumentException(UNKNOWN_ENDPOINT_MSG + path);
        }
    }
    
    private String handleDeleteRequest(String path) {
        if (path.startsWith(DATES_PATH_PREFIX)) {
            String dateStr = path.substring(DATES_PATH_PREFIX.length());
            LocalDate date = LocalDate.parse(dateStr);
            dateManager.removeDate(date);
            
            return createSuccessResponse("日付を削除しました");
        } else {
            throw new IllegalArgumentException(UNKNOWN_ENDPOINT_MSG + path);
        }
    }
    
    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}

