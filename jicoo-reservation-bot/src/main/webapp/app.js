// WebSocket接続
let ws = null;
let reconnectInterval = null;

// APIベースURL
const API_BASE = "/api";

// カレンダー用の現在の月
let currentCalendarMonth = new Date();
let allDates = []; // 全日付データ（カレンダー表示用）
let availableTimeSlots = []; // 利用可能な時間帯リスト（APIから取得）

// 初期化
(function () {
  function init() {
    console.log("初期化を開始します");
    try {
      // ブラウザ通知の許可をリクエスト
      if (
        "Notification" in globalThis &&
        Notification.permission === "default"
      ) {
        Notification.requestPermission().then((permission) => {
          if (permission === "granted") {
            appendLog("ブラウザ通知が有効になりました", "success");
          }
        });
      }

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

  const protocol = globalThis.location.protocol === "https:" ? "wss:" : "ws:";
  const wsUrl = `${protocol}//${globalThis.location.host}/ws`;
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
      // 時間帯情報と先生URLも受け取る
      const timeSlots = data.timeSlots || [];
      const teacherUrl = data.teacherUrl || "";
      updateReservationResult(data.date, data.success, timeSlots, teacherUrl);
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
    // 監視状態に応じてボタンの状態を更新
    updateMonitoringButtons(status.isMonitoring || false);

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

    // 先生リスト取得
    const teachersResponse = await fetch(`${API_BASE}/teachers`);
    if (!teachersResponse.ok) {
      throw new Error(`先生リスト取得エラー: ${teachersResponse.status}`);
    }
    const teachers = await teachersResponse.json();
    renderTeacherList(teachers || []);

    // 時間帯リスト取得
    const timeSlotsResponse = await fetch(`${API_BASE}/time-slots`);
    if (!timeSlotsResponse.ok) {
      throw new Error(`時間帯リスト取得エラー: ${timeSlotsResponse.status}`);
    }
    availableTimeSlots = (await timeSlotsResponse.json()) || [];
    console.log("利用可能な時間帯:", availableTimeSlots);

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
          if (startBtn) {
            startBtn.disabled = true;
            startBtn.innerHTML = '<span class="loading"></span> 監視開始中...';
          }
          if (stopBtn) stopBtn.disabled = false;
          updateStatus("実行中");
          appendLog("監視を開始しました", "success");

          // ボタンのテキストを元に戻す（少し遅延して）
          setTimeout(() => {
            if (startBtn) {
              startBtn.innerHTML = '<span class="btn-icon">▶</span> 監視開始';
            }
          }, 1000);
        } else {
          const errorText = await response.text();
          console.error("監視開始エラー:", response.status, errorText);
          // 既に監視中の場合は特別なメッセージを表示
          if (response.status === 200 && errorText.includes("既に監視中")) {
            appendLog(
              "既に監視中です。他のタブ/ウィンドウで監視が実行されている可能性があります。",
              "warn"
            );
          } else {
            appendLog("監視の開始に失敗しました", "error");
          }
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

  // ログクリアボタン
  const clearLogBtn = document.getElementById("clear-log-btn");
  if (clearLogBtn) {
    clearLogBtn.addEventListener("click", () => {
      const logArea = document.getElementById("log-area");
      if (logArea) {
        logArea.innerHTML = "";
        appendLog("ログをクリアしました", "info");
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
  // APIから取得した時間帯リストを使用（取得できていない場合は空配列）
  const availableSlots =
    availableTimeSlots.length > 0
      ? availableTimeSlots
      : [
          "9:45",
          "10:30",
          "11:15",
          "12:00",
          "13:00",
          "13:45",
          "14:30",
          "15:15",
          "16:00",
          "16:45",
          "17:30",
          "18:15",
          "19:00",
          "19:45",
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
globalThis.toggleDate = async function toggleDate(dateStr) {
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
globalThis.removeDate = async function removeDate(dateStr) {
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
globalThis.updateTimeSlots = async function updateTimeSlots(
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

// 先生リスト表示
function renderTeacherList(teachers) {
  const container = document.getElementById("teacher-list");
  if (!container) {
    console.error("teacher-list 要素が見つかりません");
    return;
  }

  container.innerHTML = "";

  if (!teachers || teachers.length === 0) {
    container.innerHTML =
      '<p class="empty-message">先生が登録されていません</p>';
    return;
  }

  teachers.forEach((teacher, index) => {
    const item = document.createElement("div");
    item.className = "teacher-item";

    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.id = `teacher-${index}`;
    checkbox.dataset.url = teacher.url; // data-url属性にURLを保存
    checkbox.checked = teacher.selected !== false; // デフォルトは選択
    checkbox.addEventListener("change", async () => {
      await updateSelectedTeachers();
    });

    const label = document.createElement("label");
    label.htmlFor = `teacher-${index}`;
    label.textContent = teacher.name || extractTeacherName(teacher.url);
    label.className = "teacher-label";

    item.appendChild(checkbox);
    item.appendChild(label);
    container.appendChild(item);
  });
}

// 選択された先生を更新
async function updateSelectedTeachers() {
  const checkboxes = document.querySelectorAll(
    "#teacher-list input[type='checkbox']"
  );
  const selectedUrls = [];

  checkboxes.forEach((checkbox) => {
    if (checkbox.checked) {
      // data-url属性からURLを取得
      const url = checkbox.dataset.url;
      if (url) {
        selectedUrls.push(url);
      }
    }
  });

  try {
    const response = await fetch(`${API_BASE}/teachers/selected`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(selectedUrls),
    });

    if (response.ok) {
      appendLog(
        `選択された先生を更新しました: ${selectedUrls.length}名`,
        "success"
      );
    } else {
      const errorText = await response.text();
      console.error("先生選択更新エラー:", response.status, errorText);
      appendLog("先生選択の更新に失敗しました", "error");
    }
  } catch (error) {
    console.error("先生選択更新エラー:", error);
    appendLog("先生選択の更新に失敗しました: " + error.message, "error");
  }
}

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
    emptyDiv.className = "completed-item empty-state";
    emptyDiv.innerHTML = `
      <div style="text-align: center; padding: 20px; color: #888;">
        <div style="font-size: 48px; margin-bottom: 10px;">📅</div>
        <div>予約完了日はありません</div>
      </div>
    `;
    container.appendChild(emptyDiv);
  } else {
    // 日付順にソート（新しい日付から）
    const sortedCompleted = [...completed].sort((a, b) => {
      const dateA = new Date(a.date || a);
      const dateB = new Date(b.date || b);
      return dateB - dateA; // 降順（新しい日付が上）
    });

    sortedCompleted.forEach((item) => {
      // 新しい形式（時間帯情報付き）と古い形式（日付のみ）の両方に対応
      const dateStr = item.date || item;
      const timeSlots = item.timeSlots || [];
      const teacherUrl = item.teacherUrl || "";

      // デバッグ: teacherUrlが正しく取得できているか確認
      if (!teacherUrl && item) {
        console.debug("予約完了データ:", item);
        console.debug("teacherUrlが空です。date:", dateStr);
      }

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

      // 時間帯を明確に表示
      let timeSlotDisplay = "";
      if (timeSlots.length > 0) {
        // 時間帯をソートして表示
        const sortedTimeSlots = [...timeSlots].sort();
        timeSlotDisplay = `
          <div class="reservation-time-slots">
            <span class="time-label">⏰ 予約時間:</span>
            <span class="time-values">${sortedTimeSlots.join(", ")}</span>
          </div>
        `;
      } else {
        timeSlotDisplay = `
          <div class="reservation-time-slots">
            <span class="time-label">⏰ 予約時間:</span>
            <span class="time-values no-time">時間未設定</span>
          </div>
        `;
      }

      // 先生名を表示（クリック可能なリンク）
      let teacherDisplay = "";
      if (teacherUrl && teacherUrl.trim() !== "") {
        const teacherName = extractTeacherName(teacherUrl);
        if (teacherName) {
          // HTMLエスケープ
          const escapedUrl = teacherUrl
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
          const escapedName = teacherName
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;");
          teacherDisplay = `
            <div class="reservation-teacher">
              <span class="teacher-label">👤 先生:</span>
              <a href="${escapedUrl}" target="_blank" rel="noopener noreferrer" class="teacher-name-link">${escapedName}</a>
            </div>
          `;
        } else {
          // 先生名が抽出できない場合でもURLを表示
          const escapedUrl = teacherUrl
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
          teacherDisplay = `
            <div class="reservation-teacher">
              <span class="teacher-label">👤 先生URL:</span>
              <a href="${escapedUrl}" target="_blank" rel="noopener noreferrer" class="teacher-name-link">${escapedUrl}</a>
            </div>
          `;
        }
      } else {
        // デバッグ用: teacherUrlが空の場合のログ
        console.debug("teacherUrlが空です。item:", item);
      }

      // 日付が過ぎている場合は+40分を追加
      if (isPast) {
        totalMinutes += 40;
        itemDiv.innerHTML = `
          <div class="reservation-date">
            <span class="date-icon">✅</span>
            <span class="date-text">${formattedDate}</span>
            <span class="lesson-time past">(+40分)</span>
          </div>
          ${timeSlotDisplay}
          ${teacherDisplay}
        `;
      } else {
        itemDiv.innerHTML = `
          <div class="reservation-date">
            <span class="date-icon">✅</span>
            <span class="date-text">${formattedDate}</span>
          </div>
          ${timeSlotDisplay}
          ${teacherDisplay}
        `;
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
  totalDiv.innerHTML = `
    <div style="display: flex; align-items: center; justify-content: center; gap: 8px;">
      <span style="font-size: 24px;">📊</span>
      <strong>レッスン合計時間: ${totalTimeText}</strong>
    </div>
  `;
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

  // 絵文字アイコンを追加
  let icon = "";
  switch (level) {
    case "error":
      icon = "❌";
      break;
    case "warn":
      icon = "⚠️";
      break;
    case "success":
      icon = "✅";
      break;
    case "info":
    default:
      icon = "ℹ️";
      break;
  }

  const timestampSpan = document.createElement("span");
  timestampSpan.style.color = "#888";
  timestampSpan.textContent = `[${timestamp}] `;
  logLine.appendChild(timestampSpan);

  const iconSpan = document.createElement("span");
  iconSpan.textContent = `${icon} `;
  iconSpan.style.marginRight = "4px";
  logLine.appendChild(iconSpan);

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

// 監視ボタンの状態を更新
function updateMonitoringButtons(isMonitoring) {
  const startBtn = document.getElementById("start-btn");
  const stopBtn = document.getElementById("stop-btn");

  if (startBtn) {
    startBtn.disabled = isMonitoring;
  }
  if (stopBtn) {
    stopBtn.disabled = !isMonitoring;
  }

  if (isMonitoring) {
    updateStatus("実行中");
  } else {
    updateStatus("停止");
  }
}

// ステータス更新
function updateStatus(status) {
  const statusLabel = document.getElementById("status-label");
  const statusIcon = document.getElementById("status-icon");

  if (statusLabel) {
    statusLabel.textContent = `状態: ${status}`;
  }

  if (statusIcon) {
    switch (status) {
      case "実行中":
      case "監視中":
        statusIcon.textContent = "▶";
        statusIcon.style.color = "#4caf50";
        break;
      case "停止":
      case "待機中":
        statusIcon.textContent = "⏸";
        statusIcon.style.color = "#9e9e9e";
        break;
      case "エラー":
        statusIcon.textContent = "⚠";
        statusIcon.style.color = "#f44336";
        break;
      default:
        statusIcon.textContent = "⏸";
        statusIcon.style.color = "#9e9e9e";
    }
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
        statusText.textContent = "✓ 監視時間内";
        statusText.className = "status-badge status-ok";
      } else {
        statusText.textContent = "⏸ 監視時間外";
        statusText.className = "status-badge status-warning";
      }
    } else {
      statusText.textContent = "✓ 24時間監視";
      statusText.className = "status-badge status-ok";
    }
  }
}

// URLから先生名を抽出
function extractTeacherName(url) {
  if (!url || !url.trim()) {
    return "";
  }
  // URL形式: https://www.jicoo.com/t/_XDgWVCOgMPP/e/Teacher_Vanessa
  // 最後の /e/ 以降を取得
  const match = url.match(/\/e\/([^\/\?]+)/);
  if (match && match[1]) {
    // Teacher_Vanessa -> Teacher Vanessa に変換
    return match[1].replace(/_/g, " ");
  }
  return "";
}

// 予約結果更新
function updateReservationResult(
  dateStr,
  success,
  timeSlots = [],
  teacherUrl = ""
) {
  if (success) {
    // 日付をフォーマット
    const date = new Date(dateStr);
    const formattedDate = date.toLocaleDateString("ja-JP", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      weekday: "short",
    });

    // 時間帯の表示
    let timeSlotText = "";
    if (timeSlots && timeSlots.length > 0) {
      timeSlotText = ` (${timeSlots.join(", ")})`;
    }

    // 先生名の表示
    let teacherText = "";
    if (teacherUrl) {
      const teacherName = extractTeacherName(teacherUrl);
      if (teacherName) {
        teacherText = ` - ${teacherName}`;
      }
    }

    appendLog(
      `🎉 予約成功: ${formattedDate}${timeSlotText}${teacherText}`,
      "success"
    );

    // 成功通知を表示（オプション）
    showReservationNotification(dateStr, timeSlots, true, teacherUrl);
  } else {
    appendLog(`❌ 予約失敗: ${dateStr}`, "error");
  }

  // データを再読み込みして最新の状態を表示
  loadInitialData();
}

// 予約完了通知を表示
function showReservationNotification(
  dateStr,
  timeSlots,
  success,
  teacherUrl = ""
) {
  const date = new Date(dateStr);
  const formattedDate = date.toLocaleDateString("ja-JP", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "short",
  });

  let timeSlotText = "";
  if (timeSlots && timeSlots.length > 0) {
    timeSlotText = `\n⏰ 予約時間: ${timeSlots.join(", ")}`;
  }

  // 先生名を取得
  let teacherText = "";
  if (teacherUrl) {
    const teacherName = extractTeacherName(teacherUrl);
    if (teacherName) {
      teacherText = `\n👤 先生: ${teacherName}`;
    }
  }

  // ブラウザの通知APIを使用（許可されている場合）
  if ("Notification" in globalThis && Notification.permission === "granted") {
    new Notification("予約完了", {
      body: `日付: ${formattedDate}${timeSlotText}${teacherText}`,
      icon: "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>✅</text></svg>",
      tag: `reservation-${dateStr}`,
    });
  }

  // ログエリアに強調表示
  appendLog(`━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`, "success");
  appendLog(`✅ 予約が完了しました！`, "success");
  appendLog(`📅 予約日: ${formattedDate}`, "success");
  if (timeSlots && timeSlots.length > 0) {
    appendLog(`⏰ 予約時間: ${timeSlots.join(", ")}`, "success");
  }
  if (teacherText) {
    appendLog(`👤 先生: ${extractTeacherName(teacherUrl)}`, "success");
  }
  appendLog(`━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`, "success");
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
