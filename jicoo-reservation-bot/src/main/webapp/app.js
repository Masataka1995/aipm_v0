// WebSocket接続
let ws = null;
let reconnectInterval = null;

// APIベースURL
const API_BASE = "/api";

// カレンダー用の現在の月
let currentCalendarMonth = new Date();
let allDates = []; // 全日付データ（カレンダー表示用）

// 初期化
(function () {
  function init() {
    console.log("初期化を開始します");
    try {
      // まずイベントリスナーを設定
      setupEventListeners();

      // 次に初期データを読み込み
      loadInitialData().catch((error) => {
        console.error("初期データ読み込みエラー:", error);
        appendLog(
          "初期データの読み込みに失敗しました: " + error.message,
          "error"
        );
      });

      // WebSocket接続を開始
      initializeWebSocket();

      // ポーリングを開始
      startPolling();

      console.log("初期化が完了しました");
    } catch (error) {
      console.error("初期化エラー:", error);
      appendLog("初期化中にエラーが発生しました: " + error.message, "error");
    }
  }

  // DOMContentLoadedを待つ
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    // DOMContentLoadedが既に発火している場合
    init();
  }
})();

// WebSocket接続
function initializeWebSocket() {
  // 既存の接続を閉じる
  if (ws && ws.readyState !== WebSocket.CLOSED) {
    ws.close();
  }

  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const wsUrl = `${protocol}//${window.location.host}/ws`;
  console.log("WebSocket接続を試みます:", wsUrl);

  try {
    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      console.log("WebSocket接続が確立されました");
      appendLog("WebSocket接続が確立されました", "success");
      if (reconnectInterval) {
        clearInterval(reconnectInterval);
        reconnectInterval = null;
      }
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        handleWebSocketMessage(data);
      } catch (error) {
        console.error("WebSocketメッセージ解析エラー:", error);
        appendLog("WebSocketメッセージの解析に失敗しました", "error");
      }
    };

    ws.onclose = (event) => {
      console.log("WebSocket接続が切断されました", {
        code: event.code,
        reason: event.reason,
      });
      // 正常な切断（1000）の場合は再接続しない
      if (event.code === 1000) {
        appendLog("WebSocket接続が正常に切断されました", "info");
        return;
      }
      appendLog("WebSocket接続が切断されました。再接続を試みます...", "warn");
      // 5秒後に再接続
      if (!reconnectInterval) {
        reconnectInterval = setInterval(() => {
          if (!ws || ws.readyState === WebSocket.CLOSED) {
            initializeWebSocket();
          }
        }, 5000);
      }
    };

    ws.onerror = (error) => {
      console.error("WebSocketエラー:", error);
      appendLog("WebSocketエラーが発生しました", "error");
    };
  } catch (error) {
    console.error("WebSocket接続の作成に失敗しました:", error);
    appendLog("WebSocket接続の作成に失敗しました", "error");
    // 5秒後に再接続を試みる
    if (!reconnectInterval) {
      reconnectInterval = setInterval(() => {
        initializeWebSocket();
      }, 5000);
    }
  }
}

// WebSocketメッセージ処理
function handleWebSocketMessage(data) {
  switch (data.type) {
    case "log":
      appendLog(data.message, "info");
      break;
    case "reservationResult":
      updateReservationResult(data.date, data.success);
      break;
    case "status":
      updateStatus(data.status);
      break;
  }
}

// 初期データ読み込み
async function loadInitialData() {
  try {
    console.log("初期データを読み込みます...");

    // ステータス取得
    const statusResponse = await fetch(`${API_BASE}/status`);
    if (!statusResponse.ok) {
      throw new Error(`ステータス取得エラー: ${statusResponse.status}`);
    }
    const status = await statusResponse.json();
    updateMonitoringTimeStatus(status);

    // 日付リスト取得
    const datesResponse = await fetch(`${API_BASE}/dates`);
    if (!datesResponse.ok) {
      throw new Error(`日付リスト取得エラー: ${datesResponse.status}`);
    }
    const dates = await datesResponse.json();
    allDates = dates || []; // カレンダー表示用に保存
    renderDateList(allDates);
    renderCalendar(); // カレンダーを描画

    // 予約完了日取得
    const completedResponse = await fetch(`${API_BASE}/completed-reservations`);
    if (!completedResponse.ok) {
      throw new Error(`予約完了日取得エラー: ${completedResponse.status}`);
    }
    const completed = await completedResponse.json();
    renderCompletedList(completed || []);

    console.log("初期データの読み込みが完了しました");
  } catch (error) {
    console.error("初期データ読み込みエラー:", error);
    appendLog("初期データの読み込みに失敗しました: " + error.message, "error");
  }
}

// イベントリスナー設定
function setupEventListeners() {
  console.log("イベントリスナーを設定します");

  // 監視時間ON/OFF
  const monitoringToggle = document.getElementById("monitoring-time-toggle");
  if (!monitoringToggle) {
    console.error("monitoring-time-toggle 要素が見つかりません");
  } else {
    monitoringToggle.addEventListener("click", async () => {
      const current = document
        .getElementById("monitoring-time-toggle")
        .classList.contains("toggle-on");
      const newState = !current;

      try {
        const response = await fetch(
          `${API_BASE}/config/monitoring-time-restriction`,
          {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ enabled: newState }),
          }
        );

        if (response.ok) {
          const btn = document.getElementById("monitoring-time-toggle");
          if (btn) {
            btn.textContent = newState ? "ON" : "OFF";
            btn.className = newState
              ? "toggle-btn toggle-on"
              : "toggle-btn toggle-off";
          }
          appendLog(
            `監視時間制限を${newState ? "有効" : "無効"}にしました`,
            "info"
          );
          loadInitialData();
        } else {
          const errorText = await response.text();
          console.error("監視時間設定エラー:", response.status, errorText);
          appendLog("監視時間設定の更新に失敗しました", "error");
        }
      } catch (error) {
        console.error("監視時間設定エラー:", error);
        appendLog("監視時間設定の更新に失敗しました", "error");
      }
    });
  }

  // 開始ボタン
  const startBtn = document.getElementById("start-btn");
  if (!startBtn) {
    console.error("start-btn 要素が見つかりません");
  } else {
    startBtn.addEventListener("click", async () => {
      try {
        const response = await fetch(`${API_BASE}/monitoring/start`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({}),
        });

        if (response.ok) {
          const startBtn = document.getElementById("start-btn");
          const stopBtn = document.getElementById("stop-btn");
          if (startBtn) startBtn.disabled = true;
          if (stopBtn) stopBtn.disabled = false;
          updateStatus("実行中");
          appendLog("監視を開始しました", "info");
        } else {
          const errorText = await response.text();
          console.error("監視開始エラー:", response.status, errorText);
          appendLog("監視の開始に失敗しました", "error");
        }
      } catch (error) {
        console.error("監視開始エラー:", error);
        appendLog("監視の開始に失敗しました", "error");
      }
    });
  }

  // 停止ボタン
  const stopBtn = document.getElementById("stop-btn");
  if (!stopBtn) {
    console.error("stop-btn 要素が見つかりません");
  } else {
    stopBtn.addEventListener("click", async () => {
      try {
        const response = await fetch(`${API_BASE}/monitoring/stop`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({}),
        });

        if (response.ok) {
          const startBtn = document.getElementById("start-btn");
          const stopBtn = document.getElementById("stop-btn");
          if (startBtn) startBtn.disabled = false;
          if (stopBtn) stopBtn.disabled = true;
          updateStatus("停止");
          appendLog("監視を停止しました", "info");
        } else {
          const errorText = await response.text();
          console.error("監視停止エラー:", response.status, errorText);
          appendLog("監視の停止に失敗しました", "error");
        }
      } catch (error) {
        console.error("監視停止エラー:", error);
        appendLog("監視の停止に失敗しました", "error");
      }
    });
  }

  // 日付追加ボタン
  const addDateBtn = document.getElementById("add-date-btn");
  if (addDateBtn) {
    addDateBtn.addEventListener("click", () => {
      const datePicker = document.getElementById("date-picker");
      if (!datePicker) {
        console.error("date-picker 要素が見つかりません");
        return;
      }
      const dateStr = datePicker.value;
      if (dateStr) {
        // 過去の日付をチェック
        const selectedDate = new Date(dateStr);
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        if (selectedDate < today) {
          alert(
            "過去の日付は選択できません。今日以降の日付を選択してください。"
          );
          return;
        }
        addDate(dateStr);
        datePicker.value = ""; // 入力欄をクリア
        renderCalendar(); // カレンダーを更新
      } else {
        alert("日付を選択してください");
      }
    });
  }

  // カレンダーナビゲーション
  const prevMonthBtn = document.getElementById("prev-month-btn");
  if (prevMonthBtn) {
    prevMonthBtn.addEventListener("click", () => {
      currentCalendarMonth.setMonth(currentCalendarMonth.getMonth() - 1);
      renderCalendar();
    });
  }

  const nextMonthBtn = document.getElementById("next-month-btn");
  if (nextMonthBtn) {
    nextMonthBtn.addEventListener("click", () => {
      currentCalendarMonth.setMonth(currentCalendarMonth.getMonth() + 1);
      renderCalendar();
    });
  }

  // 日付ピッカーで日付が変更されたとき
  const datePicker = document.getElementById("date-picker");
  if (datePicker) {
    // 最小日付を今日に設定
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    datePicker.min = today.toISOString().split("T")[0];

    datePicker.addEventListener("change", (e) => {
      const selectedDate = new Date(e.target.value);
      currentCalendarMonth = new Date(
        selectedDate.getFullYear(),
        selectedDate.getMonth(),
        1
      );
      renderCalendar();
    });
  }

  console.log("イベントリスナーの設定が完了しました");
}

// 日付追加
async function addDate(dateStr) {
  try {
    // 過去の日付をチェック
    const selectedDate = new Date(dateStr);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (selectedDate < today) {
      appendLog(
        "過去の日付は選択できません。今日以降の日付を選択してください。",
        "warn"
      );
      return;
    }

    const response = await fetch(`${API_BASE}/dates`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ date: dateStr }),
    });

    if (response.ok) {
      appendLog(`日付を追加しました: ${dateStr}`, "success");
      await loadInitialData(); // カレンダーも更新される
    } else {
      const errorText = await response.text();
      console.error("日付追加エラー:", response.status, errorText);
      appendLog("日付の追加に失敗しました: " + errorText, "error");
    }
  } catch (error) {
    console.error("日付追加エラー:", error);
    appendLog("日付の追加に失敗しました: " + error.message, "error");
  }
}

// 日付リスト表示
function renderDateList(dates) {
  const container = document.getElementById("date-list");
  if (!container) {
    console.error("date-list 要素が見つかりません");
    return;
  }

  container.innerHTML = "";

  if (!dates || dates.length === 0) {
    container.innerHTML =
      '<div class="empty-state">予約対象日付がありません</div>';
    return;
  }

  dates.forEach((dateInfo) => {
    const item = document.createElement("div");
    item.className = `date-item ${
      dateInfo.enabled ? "enabled" : ""
    } ${dateInfo.status.toLowerCase()}`;

    const date = new Date(dateInfo.date);
    const formattedDate = date.toLocaleDateString("ja-JP", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      weekday: "short",
    });

    item.innerHTML = `
            <div class="date-info">
                <div class="date-label">${formattedDate}</div>
                <div class="time-slots">
                    ${renderTimeSlots(
                      dateInfo.date,
                      dateInfo.selectedTimeSlots || []
                    )}
                </div>
            </div>
            <div class="date-actions">
                <button class="btn btn-small" onclick="toggleDate('${
                  dateInfo.date
                }')">
                    ${dateInfo.enabled ? "ON" : "OFF"}
                </button>
                <button class="btn btn-small btn-danger" onclick="removeDate('${
                  dateInfo.date
                }')">✕</button>
            </div>
        `;

    container.appendChild(item);
  });
}

// 時間帯チェックボックス表示
function renderTimeSlots(date, selectedSlots) {
  const availableSlots = [
    "9:45",
    "10:30",
    "11:15",
    "12:00",
    "16:00",
    "16:45",
    "17:30",
    "18:15",
    "19:00",
    "19:45",
    "20:25",
  ];

  return availableSlots
    .map((slot) => {
      const checked = selectedSlots.includes(slot) ? "checked" : "";
      return `
            <label class="time-slot-checkbox">
                <input type="checkbox" value="${slot}" ${checked} 
                       onchange="updateTimeSlots('${date}', this.value, this.checked)">
                ${slot}
            </label>
        `;
    })
    .join("");
}

// 日付ON/OFF切り替え（グローバルスコープに公開）
window.toggleDate = async function toggleDate(dateStr) {
  try {
    // 現在の状態を取得
    const datesResponse = await fetch(`${API_BASE}/dates`);
    const dates = await datesResponse.json();
    const dateInfo = dates.find((d) => d.date === dateStr);
    const currentEnabled = dateInfo ? dateInfo.enabled : false;
    const newEnabled = !currentEnabled;

    const response = await fetch(`${API_BASE}/dates/${dateStr}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled: newEnabled }),
    });

    if (response.ok) {
      appendLog(
        `日付の監視を${newEnabled ? "有効" : "無効"}にしました: ${dateStr}`,
        "info"
      );
      loadInitialData();
    }
  } catch (error) {
    console.error("日付切り替えエラー:", error);
    appendLog("日付の切り替えに失敗しました", "error");
  }
};

// 日付削除（グローバルスコープに公開）
window.removeDate = async function removeDate(dateStr) {
  if (!confirm("この日付を削除しますか？")) {
    return;
  }

  try {
    const response = await fetch(`${API_BASE}/dates/${dateStr}`, {
      method: "DELETE",
    });

    if (response.ok) {
      appendLog(`日付を削除しました: ${dateStr}`, "success");
      loadInitialData();
    } else {
      const errorText = await response.text();
      console.error("日付削除エラー:", response.status, errorText);
      appendLog("日付の削除に失敗しました: " + errorText, "error");
    }
  } catch (error) {
    console.error("日付削除エラー:", error);
    appendLog("日付の削除に失敗しました: " + error.message, "error");
  }
};

// 時間帯更新（グローバルスコープに公開）
window.updateTimeSlots = async function updateTimeSlots(
  dateStr,
  timeSlot,
  checked
) {
  try {
    const datesResponse = await fetch(`${API_BASE}/dates`);
    if (!datesResponse.ok) {
      throw new Error(`日付リスト取得エラー: ${datesResponse.status}`);
    }
    const dates = await datesResponse.json();
    const dateInfo = dates.find((d) => d.date === dateStr);

    if (!dateInfo) {
      console.error("日付情報が見つかりません:", dateStr);
      appendLog("日付情報が見つかりません", "error");
      return;
    }

    let selectedSlots = dateInfo.selectedTimeSlots || [];
    if (checked) {
      if (!selectedSlots.includes(timeSlot)) {
        selectedSlots.push(timeSlot);
      }
    } else {
      selectedSlots = selectedSlots.filter((s) => s !== timeSlot);
    }

    const response = await fetch(`${API_BASE}/dates/${dateStr}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ timeSlots: selectedSlots }),
    });

    if (response.ok) {
      loadInitialData();
    } else {
      const errorText = await response.text();
      console.error("時間帯更新エラー:", response.status, errorText);
      appendLog("時間帯の更新に失敗しました", "error");
    }
  } catch (error) {
    console.error("時間帯更新エラー:", error);
    appendLog("時間帯の更新に失敗しました: " + error.message, "error");
  }
};

// 予約完了リスト表示
function renderCompletedList(completed) {
  const container = document.getElementById("completed-list");
  if (!container) {
    console.error("completed-list 要素が見つかりません");
    return;
  }

  container.innerHTML = "";

  let totalMinutes = 0; // レッスン合計時間（分）
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  if (completed.length === 0) {
    // 予約完了日がない場合でも合計時間を表示
    const emptyDiv = document.createElement("div");
    emptyDiv.className = "completed-item";
    emptyDiv.textContent = "予約完了日はありません";
    container.appendChild(emptyDiv);
  } else {
    completed.forEach((item) => {
      // 新しい形式（時間帯情報付き）と古い形式（日付のみ）の両方に対応
      const dateStr = item.date || item;
      const timeSlots = item.timeSlots || [];

      const date = new Date(dateStr);
      date.setHours(0, 0, 0, 0);
      const formattedDate = date.toLocaleDateString("ja-JP", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        weekday: "short",
      });

      const itemDiv = document.createElement("div");
      itemDiv.className = "completed-item";

      // 日付が過ぎているかチェック
      const isPast = date < today;

      // 時間帯を表示
      let timeSlotText = "";
      if (timeSlots.length > 0) {
        timeSlotText = ` (${timeSlots.join(", ")})`;
      }

      // 日付が過ぎている場合は+40分を追加
      if (isPast) {
        totalMinutes += 40;
        itemDiv.innerHTML = `✓ ${formattedDate}${timeSlotText} <span class="lesson-time">(+40分)</span>`;
      } else {
        itemDiv.innerHTML = `✓ ${formattedDate}${timeSlotText}`;
      }

      container.appendChild(itemDiv);
    });
  }

  // レッスン合計時間を常に表示
  const totalHours = Math.floor(totalMinutes / 60);
  const remainingMinutes = totalMinutes % 60;
  let totalTimeText = "";
  if (totalHours > 0) {
    totalTimeText = `${totalHours}時間${
      remainingMinutes > 0 ? remainingMinutes + "分" : ""
    }`;
  } else {
    totalTimeText = `${remainingMinutes}分`;
  }

  const totalDiv = document.createElement("div");
  totalDiv.className = "completed-item total-time";
  totalDiv.innerHTML = `<strong>レッスン合計時間: ${totalTimeText}</strong>`;
  container.appendChild(totalDiv);
}

// ログ追加
function appendLog(message, level = "info") {
  const logArea = document.getElementById("log-area");
  if (!logArea) {
    console.log(`[${level}] ${message}`);
    return;
  }

  const logLine = document.createElement("div");
  logLine.className = `log-line ${level}`;

  // タイムスタンプを追加
  const timestamp = new Date().toLocaleTimeString("ja-JP", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });

  const timestampSpan = document.createElement("span");
  timestampSpan.style.color = "#888";
  timestampSpan.textContent = `[${timestamp}] `;
  logLine.appendChild(timestampSpan);

  const messageSpan = document.createElement("span");
  messageSpan.textContent = message;
  logLine.appendChild(messageSpan);

  logArea.appendChild(logLine);
  logArea.scrollTop = logArea.scrollHeight;

  // ログが1000件を超えたら古いログを削除
  const logs = logArea.querySelectorAll(".log-line");
  if (logs.length > 1000) {
    for (let i = 0; i < logs.length - 1000; i++) {
      logs[i].remove();
    }
  }
}

// ステータス更新
function updateStatus(status) {
  const statusLabel = document.getElementById("status-label");
  if (statusLabel) {
    statusLabel.textContent = `状態: ${status}`;
  }
}

// 監視時間ステータス更新
function updateMonitoringTimeStatus(status) {
  const btn = document.getElementById("monitoring-time-toggle");
  if (btn) {
    btn.textContent = status.monitoringTimeRestriction ? "ON" : "OFF";
    btn.className = status.monitoringTimeRestriction
      ? "toggle-btn toggle-on"
      : "toggle-btn toggle-off";
  }

  const statusText = document.getElementById("monitoring-time-status");
  if (statusText) {
    if (status.monitoringTimeRestriction) {
      if (status.withinMonitoringHours) {
        statusText.textContent = "✓ 現在は監視時間内です";
        statusText.style.color = "#4caf50";
        statusText.style.backgroundColor = "#e8f5e9";
      } else {
        statusText.textContent = "⏸ 監視時間外";
        statusText.style.color = "#f44336";
        statusText.style.backgroundColor = "#ffebee";
      }
    } else {
      statusText.textContent = "✓ 監視時間制限は無効です（24時間監視）";
      statusText.style.color = "#2196f3";
      statusText.style.backgroundColor = "#e3f2fd";
    }
  }
}

// 予約結果更新
function updateReservationResult(dateStr, success) {
  appendLog(
    `予約結果: ${dateStr} - ${success ? "成功" : "失敗"}`,
    success ? "success" : "error"
  );
  loadInitialData();
}

// ポーリング（ステータス更新）
function startPolling() {
  setInterval(async () => {
    try {
      const response = await fetch(`${API_BASE}/status`);
      const status = await response.json();
      updateMonitoringTimeStatus(status);
    } catch (error) {
      console.error("ステータス取得エラー:", error);
    }
  }, 60000); // 1分ごと
}

// カレンダーを描画
function renderCalendar() {
  const calendar = document.getElementById("calendar");
  const monthYearLabel = document.getElementById("calendar-month-year");

  if (!calendar || !monthYearLabel) {
    console.error("カレンダー要素が見つかりません");
    return;
  }

  // 月と年の表示
  const year = currentCalendarMonth.getFullYear();
  const month = currentCalendarMonth.getMonth();
  monthYearLabel.textContent = `${year}年${month + 1}月`;

  // カレンダーをクリア
  calendar.innerHTML = "";

  // 曜日ヘッダー
  const dayNames = ["日", "月", "火", "水", "木", "金", "土"];
  dayNames.forEach((dayName) => {
    const dayHeader = document.createElement("div");
    dayHeader.className = "calendar-day-header";
    dayHeader.textContent = dayName;
    calendar.appendChild(dayHeader);
  });

  // 月の最初の日を取得
  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0);
  const startDate = new Date(firstDay);
  startDate.setDate(startDate.getDate() - firstDay.getDay()); // 週の最初の日（日曜日）

  // 6週間分の日付を表示
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  for (let i = 0; i < 42; i++) {
    const currentDate = new Date(startDate);
    currentDate.setDate(startDate.getDate() + i);

    const dayElement = document.createElement("div");
    dayElement.className = "calendar-day";
    dayElement.textContent = currentDate.getDate();

    // 他の月の日付
    if (currentDate.getMonth() !== month) {
      dayElement.classList.add("other-month");
    }

    // 今日
    if (currentDate.getTime() === today.getTime()) {
      dayElement.classList.add("today");
    }

    // 日付文字列（YYYY-MM-DD形式）
    const dateStr = formatDateString(currentDate);

    // この日付がリストに含まれているか確認
    const dateInfo = allDates.find((d) => d.date === dateStr);
    if (dateInfo) {
      dayElement.classList.add("has-reservation");
      if (dateInfo.status === "SUCCESS") {
        dayElement.classList.add("success");
      } else if (dateInfo.status === "FAILED") {
        dayElement.classList.add("failed");
      }
      if (dateInfo.enabled) {
        dayElement.classList.add("selected");
      }
    }

    // クリックイベント
    dayElement.addEventListener("click", () => {
      if (currentDate.getMonth() === month) {
        // 日付ピッカーに設定
        const datePicker = document.getElementById("date-picker");
        if (datePicker) {
          datePicker.value = dateStr;
        }
        // 日付を追加（既に存在する場合は何もしない）
        if (!dateInfo) {
          addDate(dateStr);
        } else {
          // 既に存在する場合は、その日付の情報を表示
          console.log("日付は既に登録されています:", dateInfo);
        }
      } else {
        // 他の月の日付をクリックした場合は、その月に移動
        currentCalendarMonth = new Date(
          currentDate.getFullYear(),
          currentDate.getMonth(),
          1
        );
        renderCalendar();
      }
    });

    calendar.appendChild(dayElement);
  }
}

// 日付をYYYY-MM-DD形式の文字列に変換
function formatDateString(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
