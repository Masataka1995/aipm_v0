import React, {
  useState,
  useEffect,
  useCallback,
  useMemo,
  useRef,
} from "react";
import Header from "./components/Header";
import DateManagement from "./components/DateManagement";
import LogSection from "./components/LogSection";
import ControlPanel from "./components/ControlPanel";
import { useWebSocket } from "./hooks/useWebSocket";
import { useApi } from "./hooks/useApi";
import "./styles.css";

// 定数
const API_BASE = "/api";
const MAX_LOGS = 1000; // ログの最大件数
const DATA_REFRESH_INTERVAL = 60000; // 1分

function App() {
  const [status, setStatus] = useState("待機中");
  const [dates, setDates] = useState([]);
  const [completedReservations, setCompletedReservations] = useState([]);
  const [monitoringStatus, setMonitoringStatus] = useState({
    monitoringTimeRestriction: true,
    withinMonitoringHours: true,
  });
  const [isMonitoring, setIsMonitoring] = useState(false);
  const [logs, setLogs] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const isLoadingRef = useRef(false);

  const api = useApi(API_BASE);

  const appendLog = useCallback((message, level = "info") => {
    setLogs((prev) => {
      const newLogs = [...prev, { message, level, timestamp: new Date() }];
      // ログが最大件数を超えた場合は古いログを削除
      return newLogs.slice(-MAX_LOGS);
    });
  }, []);

  const handleWebSocketMessage = useCallback(
    (data) => {
      switch (data.type) {
        case "log":
          appendLog(data.message, "info");
          break;
        case "reservationResult":
          appendLog(
            `予約結果: ${data.date} - ${data.success ? "成功" : "失敗"}`,
            data.success ? "success" : "error"
          );
          loadInitialData();
          break;
        case "status":
          setStatus(data.status);
          break;
      }
    },
    [appendLog]
  );

  const { sendMessage: sendWebSocketMessage } = useWebSocket({
    onMessage: handleWebSocketMessage,
    onOpen: () => {
      appendLog("WebSocket接続が確立されました", "success");
    },
    onClose: () => {
      // 再接続メッセージは表示しない（自動再接続中）
      // appendLog("WebSocket接続が切断されました。再接続を試みます...", "warn");
    },
    onError: () => {
      appendLog("WebSocketエラーが発生しました", "error");
    },
  });

  const loadInitialData = useCallback(async () => {
    // 既に読み込み中の場合はスキップ
    if (isLoadingRef.current) {
      return;
    }

    isLoadingRef.current = true;
    setIsLoading(true);

    try {
      const [statusData, datesData, completedData] = await Promise.all([
        api.get("/status"),
        api.get("/dates"),
        api.get("/completed-reservations"),
      ]);

      setMonitoringStatus(statusData);
      setDates(datesData);
      setCompletedReservations(completedData);
    } catch (error) {
      console.error("初期データ読み込みエラー:", error);
      appendLog("初期データの読み込みに失敗しました", "error");
    } finally {
      isLoadingRef.current = false;
      setIsLoading(false);
    }
  }, [api, appendLog]);

  useEffect(() => {
    loadInitialData();
    const interval = setInterval(loadInitialData, DATA_REFRESH_INTERVAL);
    return () => clearInterval(interval);
  }, [loadInitialData]);

  const handleStartMonitoring = useCallback(async () => {
    try {
      await api.post("/monitoring/start", {});
      setIsMonitoring(true);
      setStatus("実行中");
      appendLog("監視を開始しました", "info");
    } catch (error) {
      console.error("監視開始エラー:", error);
      appendLog("監視の開始に失敗しました", "error");
    }
  }, [api, appendLog]);

  const handleStopMonitoring = useCallback(async () => {
    try {
      await api.post("/monitoring/stop", {});
      setIsMonitoring(false);
      setStatus("停止");
      appendLog("監視を停止しました", "info");
    } catch (error) {
      console.error("監視停止エラー:", error);
      appendLog("監視の停止に失敗しました", "error");
    }
  }, [api, appendLog]);

  const handleToggleMonitoringTime = useCallback(
    async (enabled) => {
      try {
        await api.put("/config/monitoring-time-restriction", { enabled });
        setMonitoringStatus((prev) => ({
          ...prev,
          monitoringTimeRestriction: enabled,
        }));
        appendLog(
          `監視時間制限を${enabled ? "有効" : "無効"}にしました`,
          "info"
        );
        loadInitialData();
      } catch (error) {
        console.error("監視時間設定エラー:", error);
        appendLog("監視時間設定の更新に失敗しました", "error");
      }
    },
    [api, appendLog, loadInitialData]
  );

  const handleAddDate = useCallback(
    async (dateStr) => {
      try {
        await api.post("/dates", { date: dateStr });
        appendLog(`日付を追加しました: ${dateStr}`, "info");
        loadInitialData();
      } catch (error) {
        console.error("日付追加エラー:", error);
        appendLog("日付の追加に失敗しました", "error");
      }
    },
    [api, appendLog, loadInitialData]
  );

  const handleRemoveDate = useCallback(
    async (dateStr) => {
      if (!window.confirm("この日付を削除しますか？")) {
        return;
      }
      try {
        await api.delete(`/dates/${dateStr}`);
        appendLog(`日付を削除しました: ${dateStr}`, "info");
        loadInitialData();
      } catch (error) {
        console.error("日付削除エラー:", error);
        appendLog("日付の削除に失敗しました", "error");
      }
    },
    [api, appendLog, loadInitialData]
  );

  const handleToggleDate = useCallback(
    async (dateStr, enabled) => {
      try {
        await api.put(`/dates/${dateStr}`, { enabled });
        loadInitialData();
      } catch (error) {
        console.error("日付切り替えエラー:", error);
      }
    },
    [api, loadInitialData]
  );

  // デバウンス付き時間帯更新
  const updateTimeSlotsTimeoutRef = useRef({});
  const handleUpdateTimeSlots = useCallback(
    async (dateStr, timeSlots) => {
      // 既存のタイムアウトをクリア
      if (updateTimeSlotsTimeoutRef.current[dateStr]) {
        clearTimeout(updateTimeSlotsTimeoutRef.current[dateStr]);
      }

      // 500ms後に更新（デバウンス）
      updateTimeSlotsTimeoutRef.current[dateStr] = setTimeout(async () => {
        try {
          await api.put(`/dates/${dateStr}`, { timeSlots });
          loadInitialData();
        } catch (error) {
          console.error("時間帯更新エラー:", error);
        }
        delete updateTimeSlotsTimeoutRef.current[dateStr];
      }, 500);
    },
    [api, loadInitialData]
  );

  // メモ化された値
  const memoizedDates = useMemo(() => dates, [dates]);
  const memoizedCompletedReservations = useMemo(
    () => completedReservations,
    [completedReservations]
  );
  const memoizedLogs = useMemo(() => logs, [logs]);

  return (
    <div className="container">
      {isLoading && (
        <div className="loading-overlay">
          <div className="loading-spinner"></div>
          <span>データを読み込み中...</span>
        </div>
      )}
      <Header
        status={status}
        monitoringStatus={monitoringStatus}
        onToggleMonitoringTime={handleToggleMonitoringTime}
      />
      <main className="main-content">
        <DateManagement
          dates={memoizedDates}
          onAddDate={handleAddDate}
          onRemoveDate={handleRemoveDate}
          onToggleDate={handleToggleDate}
          onUpdateTimeSlots={handleUpdateTimeSlots}
        />
        <LogSection logs={memoizedLogs} />
        <ControlPanel
          isMonitoring={isMonitoring}
          onStart={handleStartMonitoring}
          onStop={handleStopMonitoring}
          completedReservations={memoizedCompletedReservations}
        />
      </main>
    </div>
  );
}

export default App;
