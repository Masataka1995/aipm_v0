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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST APIサーブレット
 */
public class ApiServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ApiServlet.class);
    
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
        String path = req.getPathInfo();
        if (path == null) {
            path = "/";
        }
        
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        
        try {
            String response = handleGetRequest(path, req);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(response);
        } catch (Exception e) {
            logger.error("APIリクエスト処理中にエラーが発生しました", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null) {
            path = "/";
        }
        
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        
        try {
            String body = readRequestBody(req);
            String response = handlePostRequest(path, body, req);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(response);
        } catch (Exception e) {
            logger.error("APIリクエスト処理中にエラーが発生しました", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
    
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null) {
            path = "/";
        }
        
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        
        try {
            String body = readRequestBody(req);
            String response = handlePutRequest(path, body, req);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(response);
        } catch (Exception e) {
            logger.error("APIリクエスト処理中にエラーが発生しました", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
    
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null) {
            path = "/";
        }
        
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        
        try {
            String response = handleDeleteRequest(path, req);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(response);
        } catch (Exception e) {
            logger.error("APIリクエスト処理中にエラーが発生しました", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }
    
    private String handleGetRequest(String path, HttpServletRequest req) {
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
                    dateMap.put("enabled", info.isEnabled());
                    dateMap.put("status", info.getStatus().name());
                    dateMap.put("selectedTimeSlots", info.getSelectedTimeSlots());
                    return dateMap;
                })
                .collect(Collectors.toList());
            return gson.toJson(dates);
        } else if (path.equals("/completed-reservations")) {
            Map<LocalDate, List<String>> completedWithTimeSlots = dateManager.getCompletedReservationsWithTimeSlots();
            // 後方互換性のため、日付のみのリストも返す
            List<LocalDate> completed = dateManager.getCompletedReservations();
            
            // 時間帯情報を含む形式で返す
            List<Map<String, Object>> result = new ArrayList<>();
            for (LocalDate date : completed) {
                Map<String, Object> item = new HashMap<>();
                item.put("date", date.toString());
                List<String> timeSlots = completedWithTimeSlots.get(date);
                item.put("timeSlots", timeSlots != null ? timeSlots : new ArrayList<>());
                result.add(item);
            }
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
            throw new IllegalArgumentException("Unknown endpoint: " + path);
        }
    }
    
    private String handlePostRequest(String path, String body, HttpServletRequest req) {
        if (path.equals("/monitoring/start")) {
            Map<String, Object> request = gson.fromJson(body, Map.class);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "監視を開始しました");
            
            // 別スレッドで監視を開始
            new Thread(() -> {
                try {
                    Map<LocalDate, List<String>> datesWithTimeSlots = dateManager.getEnabledDatesWithTimeSlots();
                    bot.startMonitoring(datesWithTimeSlots);
                } catch (Exception e) {
                    logger.error("監視開始中にエラーが発生しました", e);
                }
            }).start();
            
            return gson.toJson(response);
        } else if (path.equals("/monitoring/stop")) {
            bot.stopMonitoring();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "監視を停止しました");
            return gson.toJson(response);
        } else if (path.equals("/dates")) {
            Map<String, Object> request = gson.fromJson(body, Map.class);
            String dateStr = (String) request.get("date");
            LocalDate date = LocalDate.parse(dateStr);
            dateManager.addDate(date);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "日付を追加しました");
            return gson.toJson(response);
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
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "手動予約を開始しました");
            return gson.toJson(response);
        } else {
            throw new IllegalArgumentException("Unknown endpoint: " + path);
        }
    }
    
    private String handlePutRequest(String path, String body, HttpServletRequest req) {
        if (path.startsWith("/dates/")) {
            String dateStr = path.substring("/dates/".length());
            LocalDate date = LocalDate.parse(dateStr);
            Map<String, Object> request = gson.fromJson(body, Map.class);
            
            if (request.containsKey("enabled")) {
                boolean enabled = (Boolean) request.get("enabled");
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
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "日付を更新しました");
            return gson.toJson(response);
        } else if (path.equals("/config/monitoring-time-restriction")) {
            Map<String, Object> request = gson.fromJson(body, Map.class);
            boolean enabled = (Boolean) request.get("enabled");
            Config.getInstance().setMonitoringTimeRestrictionEnabled(enabled);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "監視時間制限を更新しました");
            return gson.toJson(response);
        } else {
            throw new IllegalArgumentException("Unknown endpoint: " + path);
        }
    }
    
    private String handleDeleteRequest(String path, HttpServletRequest req) {
        if (path.startsWith("/dates/")) {
            String dateStr = path.substring("/dates/".length());
            LocalDate date = LocalDate.parse(dateStr);
            dateManager.removeDate(date);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "日付を削除しました");
            return gson.toJson(response);
        } else {
            throw new IllegalArgumentException("Unknown endpoint: " + path);
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

