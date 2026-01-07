# 実装指示書：React移行 + UI改善

## 概要

このドキュメントは、`REACT_MIGRATION_GUIDE.md`と`UI_IMPROVEMENT_PLAN.md`を基に、効率的にReact移行とUI改善を実装するための具体的な指示書です。

---

## 📋 実装戦略

### 基本方針

1. **段階的移行**: 既存機能を壊さず、段階的に移行
2. **UI改善と同時進行**: React移行時にUI改善も同時に実装
3. **バックエンド非変更**: 既存のAPIエンドポイントは変更しない
4. **機能の完全性**: すべての既存機能を維持

---

## 🎯 実装フェーズ

### フェーズ0: 準備（1-2時間）

#### 0.1 環境セットアップ

```bash
# プロジェクトルートに移動
cd jicoo-reservation-bot

# Reactプロジェクトの作成
npx create-react-app frontend --template minimal

# 必要なパッケージのインストール
cd frontend
npm install axios date-fns
npm install --save-dev @types/react @types/react-dom
```

#### 0.2 既存ファイルのバックアップ

```bash
# Windows (PowerShell)
Copy-Item -Path "src\main\webapp" -Destination "src\main\webapp.backup" -Recurse

# または Git でコミット
git add .
git commit -m "バックアップ: React移行前の状態"
```

#### 0.3 プロジェクト構造の作成

```
frontend/
├── src/
│   ├── components/
│   │   ├── common/          # 共通コンポーネント
│   │   │   ├── Button.jsx
│   │   │   ├── Card.jsx
│   │   │   ├── Toast.jsx
│   │   │   └── Loading.jsx
│   │   ├── Header/
│   │   │   └── Header.jsx
│   │   ├── TeacherSelection/
│   │   │   └── TeacherSelection.jsx
│   │   ├── DateManagement/
│   │   │   ├── DateManagement.jsx
│   │   │   ├── DatePicker.jsx
│   │   │   ├── Calendar.jsx
│   │   │   └── DateList.jsx
│   │   ├── LogSection/
│   │   │   └── LogSection.jsx
│   │   └── ControlPanel/
│   │       ├── ControlPanel.jsx
│   │       └── CompletedReservations.jsx
│   ├── hooks/
│   │   ├── useWebSocket.js
│   │   ├── useApi.js
│   │   ├── useReservation.js
│   │   └── useToast.js
│   ├── services/
│   │   ├── api.js
│   │   └── websocket.js
│   ├── contexts/
│   │   ├── AppContext.js
│   │   └── ReservationContext.js
│   ├── utils/
│   │   ├── dateUtils.js
│   │   └── constants.js
│   ├── styles/
│   │   ├── variables.css      # CSS変数（カラーパレットなど）
│   │   ├── common.css         # 共通スタイル
│   │   └── components/        # コンポーネント別スタイル
│   ├── App.jsx
│   └── index.js
├── public/
└── package.json
```

---

### フェーズ1: 基盤構築（2-3時間）

#### 1.1 設定ファイルの作成

**`frontend/src/utils/constants.js`**
```javascript
export const API_BASE = process.env.REACT_APP_API_BASE || '/api';
export const WS_URL = process.env.REACT_APP_WS_URL || 
  `ws://${window.location.host}/ws`;
```

**`frontend/package.json` に追加**
```json
{
  "proxy": "http://localhost:8080",
  "scripts": {
    "build": "react-scripts build",
    "build:copy": "react-scripts build && xcopy /E /I /Y build\\* ..\\src\\main\\webapp\\"
  }
}
```

**`frontend/.env.development`**
```
REACT_APP_API_BASE=http://localhost:8080/api
REACT_APP_WS_URL=ws://localhost:8080/ws
```

#### 1.2 CSS変数の設定（UI改善: フェーズ1）

**`frontend/src/styles/variables.css`**
```css
:root {
  /* 既存のカラー */
  --primary-color: #667eea;
  --secondary-color: #764ba2;
  
  /* 追加カラー */
  --accent-color: #f093fb;
  --surface-color: #ffffff;
  --surface-variant: #f8f9fa;
  --text-primary: #1a1a1a;
  --text-secondary: #666666;
  --border-color: #e0e0e0;
  --divider-color: #e5e5e5;
  
  /* ダークモード用 */
  --dark-bg: #121212;
  --dark-surface: #1e1e1e;
  --dark-text: #ffffff;
  --dark-text-secondary: #b0b0b0;
  
  /* スペーシング */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;
}

[data-theme="dark"] {
  --bg-gradient: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  --surface-color: var(--dark-surface);
  --text-primary: var(--dark-text);
  --text-secondary: var(--dark-text-secondary);
}
```

#### 1.3 APIサービスの実装

**`frontend/src/services/api.js`**
```javascript
import axios from 'axios';
import { API_BASE } from '../utils/constants';

const api = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const apiService = {
  // ステータス取得
  getStatus: () => api.get('/status'),
  
  // 日付リスト取得
  getDates: () => api.get('/dates'),
  
  // 日付追加
  addDate: (date) => api.post('/dates', { date }),
  
  // 日付削除
  deleteDate: (date) => api.delete(`/dates/${date}`),
  
  // 日付有効化/無効化
  toggleDate: (date, enabled) => 
    api.put(`/dates/${date}`, { enabled }),
  
  // 時間帯設定
  setTimeSlots: (date, timeSlots) => 
    api.put(`/dates/${date}/time-slots`, { timeSlots }),
  
  // 予約完了日取得
  getCompletedReservations: () => 
    api.get('/completed-reservations'),
  
  // 先生リスト取得
  getTeachers: () => api.get('/teachers'),
  
  // 先生選択
  toggleTeacher: (url, selected) => 
    api.put('/teachers', { url, selected }),
  
  // 時間帯リスト取得
  getTimeSlots: () => api.get('/time-slots'),
  
  // 監視開始
  startMonitoring: () => api.post('/monitoring/start'),
  
  // 監視停止
  stopMonitoring: () => api.post('/monitoring/stop'),
  
  // 監視時間制限の切り替え
  toggleMonitoringTimeRestriction: (enabled) => 
    api.put('/config/monitoring-time-restriction', { enabled }),
};

export default apiService;
```

#### 1.4 WebSocketサービスの実装

**`frontend/src/services/websocket.js`**
```javascript
import { WS_URL } from '../utils/constants';

class WebSocketService {
  constructor() {
    this.ws = null;
    this.reconnectInterval = null;
    this.listeners = new Set();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 10;
  }

  connect() {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return;
    }

    try {
      this.ws = new WebSocket(WS_URL);
      
      this.ws.onopen = () => {
        console.log('WebSocket接続が確立されました');
        this.reconnectAttempts = 0;
        this.notifyListeners({ type: 'connected' });
      };

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.notifyListeners(data);
        } catch (error) {
          console.error('WebSocketメッセージのパースエラー:', error);
        }
      };

      this.ws.onerror = (error) => {
        console.error('WebSocketエラー:', error);
        this.notifyListeners({ type: 'error', error });
      };

      this.ws.onclose = () => {
        console.log('WebSocket接続が閉じられました');
        this.notifyListeners({ type: 'disconnected' });
        this.scheduleReconnect();
      };
    } catch (error) {
      console.error('WebSocket接続エラー:', error);
      this.scheduleReconnect();
    }
  }

  scheduleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('WebSocket再接続の最大試行回数に達しました');
      return;
    }

    if (this.reconnectInterval) {
      return;
    }

    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    this.reconnectAttempts++;

    this.reconnectInterval = setTimeout(() => {
      this.reconnectInterval = null;
      console.log(`WebSocket再接続を試みます (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
      this.connect();
    }, delay);
  }

  subscribe(listener) {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  notifyListeners(data) {
    this.listeners.forEach(listener => {
      try {
        listener(data);
      } catch (error) {
        console.error('WebSocketリスナーのエラー:', error);
      }
    });
  }

  disconnect() {
    if (this.reconnectInterval) {
      clearTimeout(this.reconnectInterval);
      this.reconnectInterval = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }
}

export const wsService = new WebSocketService();
```

#### 1.5 カスタムフック: useWebSocket

**`frontend/src/hooks/useWebSocket.js`**
```javascript
import { useEffect, useRef } from 'react';
import { wsService } from '../services/websocket';

export function useWebSocket(onMessage) {
  const onMessageRef = useRef(onMessage);

  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);

  useEffect(() => {
    const handleMessage = (data) => {
      onMessageRef.current?.(data);
    };

    const unsubscribe = wsService.subscribe(handleMessage);
    wsService.connect();

    return () => {
      unsubscribe();
    };
  }, []);
}
```

#### 1.6 カスタムフック: useApi

**`frontend/src/hooks/useApi.js`**
```javascript
import { useState, useEffect } from 'react';
import { apiService } from '../services/api';

export function useApi(apiCall, dependencies = []) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    const fetchData = async () => {
      setLoading(true);
      setError(null);

      try {
        const response = await apiCall();
        if (!cancelled) {
          setData(response.data);
          setLoading(false);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err);
          setLoading(false);
        }
      }
    };

    fetchData();

    return () => {
      cancelled = true;
    };
  }, dependencies);

  return { data, loading, error };
}
```

#### 1.7 トースト通知システム（UI改善: フェーズ1）

**`frontend/src/hooks/useToast.js`**
```javascript
import { useState, useCallback } from 'react';

export function useToast() {
  const [toasts, setToasts] = useState([]);

  const showToast = useCallback((message, type = 'info', duration = 3000) => {
    const id = Date.now();
    const toast = { id, message, type };

    setToasts(prev => [...prev, toast]);

    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, duration);

    return id;
  }, []);

  const removeToast = useCallback((id) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  return { toasts, showToast, removeToast };
}
```

**`frontend/src/components/common/Toast.jsx`**
```jsx
import React from 'react';
import './Toast.css';

export function Toast({ toasts, onRemove }) {
  return (
    <div className="toast-container">
      {toasts.map(toast => (
        <div
          key={toast.id}
          className={`toast toast-${toast.type} toast-enter`}
          onClick={() => onRemove(toast.id)}
        >
          {toast.message}
        </div>
      ))}
    </div>
  );
}
```

---

### フェーズ2: 共通コンポーネント（2-3時間）

#### 2.1 Buttonコンポーネント（UI改善: フェーズ1）

**`frontend/src/components/common/Button.jsx`**
```jsx
import React from 'react';
import './Button.css';

export function Button({
  children,
  variant = 'primary',
  size = 'medium',
  disabled = false,
  loading = false,
  onClick,
  className = '',
  ...props
}) {
  return (
    <button
      className={`btn btn-${variant} btn-${size} ${loading ? 'btn-loading' : ''} ${className}`}
      disabled={disabled || loading}
      onClick={onClick}
      {...props}
    >
      {loading && <span className="btn-spinner"></span>}
      {children}
    </button>
  );
}
```

#### 2.2 Cardコンポーネント（UI改善: フェーズ1）

**`frontend/src/components/common/Card.jsx`**
```jsx
import React from 'react';
import './Card.css';

export function Card({ children, className = '', ...props }) {
  return (
    <div className={`card ${className}`} {...props}>
      {children}
    </div>
  );
}
```

#### 2.3 Loadingコンポーネント（UI改善: フェーズ1）

**`frontend/src/components/common/Loading.jsx`**
```jsx
import React from 'react';
import './Loading.css';

export function Loading({ size = 'medium', fullScreen = false }) {
  return (
    <div className={`loading-container ${fullScreen ? 'loading-fullscreen' : ''}`}>
      <div className={`spinner spinner-${size}`}></div>
    </div>
  );
}
```

---

### フェーズ3: 主要コンポーネント（4-6時間）

#### 3.1 Headerコンポーネント

**`frontend/src/components/Header/Header.jsx`**
```jsx
import React from 'react';
import { Button } from '../common/Button';
import './Header.css';

export function Header({ status, monitoringTimeEnabled, onToggleMonitoringTime }) {
  return (
    <header className="header">
      <div className="header-content">
        <h1>
          <span className="icon">🤖</span>
          Jicoo 自動予約 BOT
        </h1>
        <div className="status-bar">
          <div className="status-item">
            <span className="status-icon">{status === 'running' ? '▶' : '⏸'}</span>
            <span>状態: {status === 'running' ? '実行中' : '待機中'}</span>
          </div>
          <span className="separator">|</span>
          <div className="status-item">
            <span>監視時間制限:</span>
            <Button
              variant={monitoringTimeEnabled ? 'success' : 'secondary'}
              size="small"
              onClick={onToggleMonitoringTime}
            >
              {monitoringTimeEnabled ? 'ON' : 'OFF'}
            </Button>
          </div>
        </div>
      </div>
    </header>
  );
}
```

#### 3.2 TeacherSelectionコンポーネント

**`frontend/src/components/TeacherSelection/TeacherSelection.jsx`**
```jsx
import React from 'react';
import { Card } from '../common/Card';
import './TeacherSelection.css';

export function TeacherSelection({ teachers, onToggleTeacher }) {
  return (
    <section className="teacher-selection">
      <h2>
        <span className="icon">👨‍🏫</span>
        先生選択
      </h2>
      <div className="teacher-list">
        {teachers.map(teacher => (
          <Card key={teacher.url} className="teacher-card">
            <label>
              <input
                type="checkbox"
                checked={teacher.selected}
                onChange={() => onToggleTeacher(teacher.url, !teacher.selected)}
              />
              <span>{teacher.name}</span>
            </label>
          </Card>
        ))}
      </div>
    </section>
  );
}
```

#### 3.3 DateManagementコンポーネント

**`frontend/src/components/DateManagement/DateManagement.jsx`**
```jsx
import React, { useState } from 'react';
import { Card } from '../common/Card';
import { Button } from '../common/Button';
import { DatePicker } from './DatePicker';
import { Calendar } from './Calendar';
import { DateList } from './DateList';
import './DateManagement.css';

export function DateManagement({
  dates,
  availableTimeSlots,
  onAddDate,
  onDeleteDate,
  onToggleDate,
  onSetTimeSlots,
}) {
  const [selectedDate, setSelectedDate] = useState('');

  const handleAddDate = () => {
    if (selectedDate) {
      onAddDate(selectedDate);
      setSelectedDate('');
    }
  };

  return (
    <section className="date-management">
      <h2>
        <span className="icon">📅</span>
        予約対象日付管理
      </h2>

      <DatePicker
        selectedDate={selectedDate}
        onDateChange={setSelectedDate}
        onAdd={handleAddDate}
      />

      <Calendar
        dates={dates}
        onDateClick={onAddDate}
      />

      <DateList
        dates={dates}
        availableTimeSlots={availableTimeSlots}
        onDelete={onDeleteDate}
        onToggle={onToggleDate}
        onSetTimeSlots={onSetTimeSlots}
      />
    </section>
  );
}
```

#### 3.4 LogSectionコンポーネント（UI改善: フェーズ2）

**`frontend/src/components/LogSection/LogSection.jsx`**
```jsx
import React, { useEffect, useRef } from 'react';
import { Button } from '../common/Button';
import './LogSection.css';

export function LogSection({ logs, onClear }) {
  const logAreaRef = useRef(null);

  useEffect(() => {
    if (logAreaRef.current) {
      logAreaRef.current.scrollTop = logAreaRef.current.scrollHeight;
    }
  }, [logs]);

  return (
    <section className="log-section">
      <div className="log-header">
        <h2>
          <span className="icon">📋</span>
          ログ出力
        </h2>
        <Button variant="info" size="small" onClick={onClear}>
          クリア
        </Button>
      </div>
      <div ref={logAreaRef} className="log-area">
        {logs.map((log, index) => (
          <div key={index} className={`log-entry log-${log.type}`}>
            <span className="log-time">{log.time}</span>
            <span className="log-message">{log.message}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
```

#### 3.5 ControlPanelコンポーネント

**`frontend/src/components/ControlPanel/ControlPanel.jsx`**
```jsx
import React from 'react';
import { Button } from '../common/Button';
import { CompletedReservations } from './CompletedReservations';
import './ControlPanel.css';

export function ControlPanel({
  isMonitoring,
  onStart,
  onStop,
  completedReservations,
}) {
  return (
    <section className="control-panel">
      <h2>
        <span className="icon">⚙️</span>
        操作パネル
      </h2>
      <div className="button-group">
        <Button
          variant="success"
          size="large"
          onClick={onStart}
          disabled={isMonitoring}
          loading={isMonitoring}
        >
          <span className="btn-icon">▶</span>
          監視開始
        </Button>
        <Button
          variant="danger"
          size="large"
          onClick={onStop}
          disabled={!isMonitoring}
        >
          <span className="btn-icon">⏹</span>
          監視停止
        </Button>
      </div>

      <CompletedReservations reservations={completedReservations} />
    </section>
  );
}
```

---

### フェーズ4: Context APIと状態管理（2-3時間）

#### 4.1 AppContextの実装

**`frontend/src/contexts/AppContext.js`**
```javascript
import React, { createContext, useContext, useState, useEffect } from 'react';
import { apiService } from '../services/api';
import { useWebSocket } from '../hooks/useWebSocket';

const AppContext = createContext();

export function AppProvider({ children }) {
  const [dates, setDates] = useState([]);
  const [teachers, setTeachers] = useState([]);
  const [completedReservations, setCompletedReservations] = useState([]);
  const [availableTimeSlots, setAvailableTimeSlots] = useState([]);
  const [isMonitoring, setIsMonitoring] = useState(false);
  const [monitoringTimeEnabled, setMonitoringTimeEnabled] = useState(true);
  const [logs, setLogs] = useState([]);

  // 初期データ読み込み
  useEffect(() => {
    loadInitialData();
  }, []);

  const loadInitialData = async () => {
    try {
      const [statusRes, datesRes, completedRes, teachersRes, timeSlotsRes] = 
        await Promise.all([
          apiService.getStatus(),
          apiService.getDates(),
          apiService.getCompletedReservations(),
          apiService.getTeachers(),
          apiService.getTimeSlots(),
        ]);

      setIsMonitoring(statusRes.data.isMonitoring || false);
      setMonitoringTimeEnabled(statusRes.data.monitoringTimeEnabled !== false);
      setDates(datesRes.data || []);
      setCompletedReservations(completedRes.data || []);
      setTeachers(teachersRes.data || []);
      setAvailableTimeSlots(timeSlotsRes.data || []);
    } catch (error) {
      console.error('初期データ読み込みエラー:', error);
    }
  };

  // WebSocketメッセージ処理
  useWebSocket((data) => {
    if (data.type === 'log') {
      setLogs(prev => [...prev, {
        time: new Date().toLocaleTimeString(),
        message: data.message,
        type: data.level || 'info',
      }]);
    } else if (data.type === 'status') {
      setIsMonitoring(data.isMonitoring || false);
    } else if (data.type === 'date-updated') {
      loadInitialData();
    }
  });

  const value = {
    dates,
    teachers,
    completedReservations,
    availableTimeSlots,
    isMonitoring,
    monitoringTimeEnabled,
    logs,
    setDates,
    setTeachers,
    setCompletedReservations,
    setAvailableTimeSlots,
    setIsMonitoring,
    setMonitoringTimeEnabled,
    setLogs,
    loadInitialData,
  };

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp() {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useApp must be used within AppProvider');
  }
  return context;
}
```

---

### フェーズ5: メインAppコンポーネント（2-3時間）

#### 5.1 App.jsxの実装

**`frontend/src/App.jsx`**
```jsx
import React from 'react';
import { AppProvider, useApp } from './contexts/AppContext';
import { Header } from './components/Header/Header';
import { TeacherSelection } from './components/TeacherSelection/TeacherSelection';
import { DateManagement } from './components/DateManagement/DateManagement';
import { LogSection } from './components/LogSection/LogSection';
import { ControlPanel } from './components/ControlPanel/ControlPanel';
import { Toast } from './components/common/Toast';
import { useToast } from './hooks/useToast';
import { apiService } from './services/api';
import './App.css';

function AppContent() {
  const {
    dates,
    teachers,
    completedReservations,
    availableTimeSlots,
    isMonitoring,
    monitoringTimeEnabled,
    logs,
    setDates,
    setTeachers,
    setIsMonitoring,
    setMonitoringTimeEnabled,
    setLogs,
    loadInitialData,
  } = useApp();

  const { toasts, showToast, removeToast } = useToast();

  const handleAddDate = async (date) => {
    try {
      await apiService.addDate(date);
      await loadInitialData();
      showToast('日付を追加しました', 'success');
    } catch (error) {
      showToast('日付の追加に失敗しました', 'error');
    }
  };

  const handleDeleteDate = async (date) => {
    try {
      await apiService.deleteDate(date);
      await loadInitialData();
      showToast('日付を削除しました', 'success');
    } catch (error) {
      showToast('日付の削除に失敗しました', 'error');
    }
  };

  const handleToggleDate = async (date, enabled) => {
    try {
      await apiService.toggleDate(date, enabled);
      await loadInitialData();
    } catch (error) {
      showToast('日付の更新に失敗しました', 'error');
    }
  };

  const handleSetTimeSlots = async (date, timeSlots) => {
    try {
      await apiService.setTimeSlots(date, timeSlots);
      await loadInitialData();
      showToast('時間帯を設定しました', 'success');
    } catch (error) {
      showToast('時間帯の設定に失敗しました', 'error');
    }
  };

  const handleToggleTeacher = async (url, selected) => {
    try {
      await apiService.toggleTeacher(url, selected);
      await loadInitialData();
    } catch (error) {
      showToast('先生の選択に失敗しました', 'error');
    }
  };

  const handleStartMonitoring = async () => {
    try {
      await apiService.startMonitoring();
      setIsMonitoring(true);
      showToast('監視を開始しました', 'success');
    } catch (error) {
      showToast('監視の開始に失敗しました', 'error');
    }
  };

  const handleStopMonitoring = async () => {
    try {
      await apiService.stopMonitoring();
      setIsMonitoring(false);
      showToast('監視を停止しました', 'info');
    } catch (error) {
      showToast('監視の停止に失敗しました', 'error');
    }
  };

  const handleToggleMonitoringTime = async () => {
    try {
      const newValue = !monitoringTimeEnabled;
      await apiService.toggleMonitoringTimeRestriction(newValue);
      setMonitoringTimeEnabled(newValue);
      showToast(`監視時間制限を${newValue ? '有効' : '無効'}にしました`, 'info');
    } catch (error) {
      showToast('監視時間制限の更新に失敗しました', 'error');
    }
  };

  const handleClearLogs = () => {
    setLogs([]);
  };

  return (
    <div className="container">
      <Header
        status={isMonitoring ? 'running' : 'idle'}
        monitoringTimeEnabled={monitoringTimeEnabled}
        onToggleMonitoringTime={handleToggleMonitoringTime}
      />

      <main className="main-content">
        <TeacherSelection
          teachers={teachers}
          onToggleTeacher={handleToggleTeacher}
        />

        <DateManagement
          dates={dates}
          availableTimeSlots={availableTimeSlots}
          onAddDate={handleAddDate}
          onDeleteDate={handleDeleteDate}
          onToggleDate={handleToggleDate}
          onSetTimeSlots={handleSetTimeSlots}
        />

        <LogSection logs={logs} onClear={handleClearLogs} />

        <ControlPanel
          isMonitoring={isMonitoring}
          onStart={handleStartMonitoring}
          onStop={handleStopMonitoring}
          completedReservations={completedReservations}
        />
      </main>

      <Toast toasts={toasts} onRemove={removeToast} />
    </div>
  );
}

function App() {
  return (
    <AppProvider>
      <AppContent />
    </AppProvider>
  );
}

export default App;
```

---

### フェーズ6: スタイリング（3-4時間）

#### 6.1 基本スタイルの移行

既存の`styles.css`をコンポーネント別に分割し、CSS変数を使用してUI改善を実装。

#### 6.2 ダークモードの実装（UI改善: フェーズ2）

**`frontend/src/components/common/ThemeToggle.jsx`**
```jsx
import React, { useEffect, useState } from 'react';
import './ThemeToggle.css';

export function ThemeToggle() {
  const [theme, setTheme] = useState(
    localStorage.getItem('theme') || 'light'
  );

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme(prev => prev === 'light' ? 'dark' : 'light');
  };

  return (
    <button className="theme-toggle" onClick={toggleTheme}>
      {theme === 'light' ? '🌙' : '☀️'}
    </button>
  );
}
```

---

### フェーズ7: ビルドと統合（1-2時間）

#### 7.1 ビルドスクリプトの作成

**`build-frontend.bat` (Windows)**
```batch
@echo off
cd frontend
call npm run build
xcopy /E /I /Y build\* ..\src\main\webapp\
echo ビルドが完了しました
```

#### 7.2 Mavenビルドとの統合

既存のMavenビルドプロセスは変更不要。フロントエンドをビルドしてからMavenビルドを実行。

---

## ✅ 実装チェックリスト

### フェーズ0: 準備
- [ ] Node.js環境の確認
- [ ] Reactプロジェクトの作成
- [ ] 既存ファイルのバックアップ
- [ ] プロジェクト構造の作成

### フェーズ1: 基盤構築
- [ ] 設定ファイルの作成
- [ ] CSS変数の設定
- [ ] APIサービスの実装
- [ ] WebSocketサービスの実装
- [ ] カスタムフックの実装
- [ ] トースト通知システム

### フェーズ2: 共通コンポーネント
- [ ] Buttonコンポーネント
- [ ] Cardコンポーネント
- [ ] Loadingコンポーネント
- [ ] Toastコンポーネント

### フェーズ3: 主要コンポーネント
- [ ] Headerコンポーネント
- [ ] TeacherSelectionコンポーネント
- [ ] DateManagementコンポーネント
- [ ] LogSectionコンポーネント
- [ ] ControlPanelコンポーネント

### フェーズ4: 状態管理
- [ ] AppContextの実装
- [ ] 状態管理の統合

### フェーズ5: メインApp
- [ ] App.jsxの実装
- [ ] イベントハンドラーの実装

### フェーズ6: スタイリング
- [ ] 基本スタイルの移行
- [ ] ダークモードの実装
- [ ] レスポンシブデザインの確認

### フェーズ7: ビルドと統合
- [ ] ビルドスクリプトの作成
- [ ] ビルドの確認
- [ ] 統合テスト

---

## 🚀 実装の優先順位

### 最優先（必須機能）
1. APIサービスとWebSocketサービスの実装
2. 基本コンポーネント（Header, ControlPanel, LogSection）
3. 状態管理（AppContext）
4. メインAppコンポーネント

### 高優先度（UI改善: フェーズ1）
1. トースト通知システム
2. Buttonコンポーネントの改善（ローディング状態）
3. Loadingコンポーネント
4. CSS変数の設定

### 中優先度（UI改善: フェーズ2）
1. ダークモード対応
2. カレンダーの改善
3. ログエリアの機能強化

### 低優先度（後回し可能）
1. データビジュアライゼーション
2. 統計ダッシュボード
3. エクスポート機能

---

## ⚠️ 注意事項

### 既存機能の維持
- すべての既存機能をReactで再実装
- バックエンドAPIは変更しない
- WebSocketプロトコルは維持

### パフォーマンス
- 不要な再レンダリングを防ぐ（React.memo, useMemo, useCallback）
- 大量のログ表示時のパフォーマンスに注意
- 仮想スクロールの検討（ログが多くなった場合）

### ブラウザ互換性
- 既存のブラウザサポートを維持
- WebSocketのフォールバック処理

### テスト
- 各フェーズで動作確認
- 既存機能との比較テスト
- エラーハンドリングの確認

---

## 📝 実装時のコマンド

### 開発環境
```bash
# フロントエンド開発サーバー起動
cd frontend
npm start

# バックエンドサーバー起動（別ターミナル）
cd ..
mvn clean package
java -jar target/jicoo-reservation-bot-1.0.0.jar
```

### ビルド
```bash
# フロントエンドビルド
cd frontend
npm run build

# ビルドファイルをwebappにコピー（Windows）
xcopy /E /I /Y build\* ..\src\main\webapp\

# Mavenビルド
cd ..
mvn clean package
```

---

## 🔄 段階的移行の推奨手順

1. **フェーズ0-1を完了**: 基盤を構築
2. **フェーズ2-3を完了**: 小さなコンポーネントから実装
3. **動作確認**: 各コンポーネントを個別にテスト
4. **フェーズ4-5を完了**: 状態管理とメインAppを実装
5. **統合テスト**: 全体の動作確認
6. **フェーズ6を完了**: スタイリングとUI改善
7. **フェーズ7を完了**: ビルドとデプロイ準備

---

## 📚 参考リソース

- React公式ドキュメント: https://react.dev/
- Context API: https://react.dev/reference/react/useContext
- WebSocket API: https://developer.mozilla.org/en-US/docs/Web/API/WebSocket

---

## 🎯 完了の定義

以下の条件を満たした場合、実装完了とします：

1. ✅ すべての既存機能がReactで動作する
2. ✅ UI改善（フェーズ1）が実装されている
3. ✅ ビルドが正常に完了する
4. ✅ バックエンドとの統合が正常に動作する
5. ✅ エラーハンドリングが適切に実装されている

---

この指示書に従って、段階的に実装を進めてください。各フェーズで動作確認を行い、問題があれば修正してから次のフェーズに進むことを推奨します。

