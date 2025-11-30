import React, { useState, memo } from "react";
import { formatDate } from "../utils/dateUtils";

const ControlPanel = memo(function ControlPanel({
  isMonitoring,
  onStart,
  onStop,
  completedReservations,
}) {
  const [isLoading, setIsLoading] = useState(false);

  const handleStart = async () => {
    setIsLoading(true);
    try {
      await onStart();
    } finally {
      setIsLoading(false);
    }
  };

  const handleStop = async () => {
    if (!window.confirm("監視を停止しますか？")) {
      return;
    }
    setIsLoading(true);
    try {
      await onStop();
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <section className="control-panel">
      <h2>🎮 操作パネル</h2>
      <div className="button-group">
        <button
          id="start-btn"
          className="btn btn-success btn-large"
          onClick={handleStart}
          disabled={isMonitoring || isLoading}
          title="予約監視を開始します"
        >
          {isLoading ? (
            <>
              <span className="spinner"></span>
              開始中...
            </>
          ) : (
            <>
              <span className="btn-icon">▶️</span>
              監視開始
            </>
          )}
        </button>
        <button
          id="stop-btn"
          className="btn btn-danger btn-large"
          onClick={handleStop}
          disabled={!isMonitoring || isLoading}
          title="予約監視を停止します"
        >
          {isLoading ? (
            <>
              <span className="spinner"></span>
              停止中...
            </>
          ) : (
            <>
              <span className="btn-icon">⏹️</span>
              監視停止
            </>
          )}
        </button>
        <div className="button-divider"></div>
        <button
          id="manual-reserve-btn"
          className="btn btn-info"
          title="手動で予約を実行します"
        >
          <span className="btn-icon">🔁</span>
          手動予約
        </button>
        <button
          id="check-time-slots-btn"
          className="btn btn-purple"
          title="利用可能な時間帯を確認します"
        >
          <span className="btn-icon">⏰</span>
          時間帯確認
        </button>
      </div>

      <div className="completed-reservations">
        <h3>
          <span className="section-icon">✅</span>
          予約完了日
          <span className="badge">{completedReservations.length}</span>
        </h3>
        <div id="completed-list" className="completed-list">
          {completedReservations.length === 0 ? (
            <div className="completed-item empty-state">
              <span className="empty-icon">📭</span>
              <span className="empty-text">予約完了日はありません</span>
            </div>
          ) : (
            completedReservations.map((dateStr) => (
              <div key={dateStr} className="completed-item success-item">
                <span className="check-icon">✓</span>
                <span className="date-text">{formatDate(dateStr)}</span>
              </div>
            ))
          )}
        </div>
      </div>
    </section>
  );
});

export default ControlPanel;
