import React, { memo } from "react";
import { getMonitoringStatusIcon } from "../utils/iconUtils";

const Header = memo(function Header({
  status,
  monitoringStatus,
  onToggleMonitoringTime,
}) {
  const getMonitoringTimeStatusText = () => {
    if (!monitoringStatus.monitoringTimeRestriction) {
      return {
        text: "24時間監視中",
        icon: "🌙",
        color: "#2196f3",
        bgColor: "#e3f2fd",
      };
    }
    if (monitoringStatus.withinMonitoringHours) {
      return {
        text: "監視時間内",
        icon: "✅",
        color: "#4caf50",
        bgColor: "#e8f5e9",
      };
    }
    return {
      text: "監視時間外",
      icon: "⏸️",
      color: "#f44336",
      bgColor: "#ffebee",
    };
  };

  const statusInfo = getMonitoringTimeStatusText();

  return (
    <header className="header">
      <div className="header-left">
        <h1>🎯 Jicoo 自動予約 BOT</h1>
        <div className="status-badge">
          <span className="status-icon">{getMonitoringStatusIcon(status)}</span>
          <span id="status-label" className="status-label">
            {status}
          </span>
        </div>
      </div>
      <div className="status-bar">
        <div className="monitoring-time-control">
          <label className="monitoring-time-label">
            <span className="label-text">監視時間制限</span>
            <button
              id="monitoring-time-toggle"
              className={`toggle-btn ${
                monitoringStatus.monitoringTimeRestriction
                  ? "toggle-on"
                  : "toggle-off"
              }`}
              onClick={() =>
                onToggleMonitoringTime(
                  !monitoringStatus.monitoringTimeRestriction
                )
              }
              title={
                monitoringStatus.monitoringTimeRestriction
                  ? "監視時間制限を無効にする"
                  : "監視時間制限を有効にする"
              }
            >
              {monitoringStatus.monitoringTimeRestriction ? "ON" : "OFF"}
            </button>
          </label>
        </div>
        <div
          id="monitoring-time-status"
          className="status-badge status-info"
          style={{
            color: statusInfo.color,
            backgroundColor: statusInfo.bgColor,
          }}
          title={
            monitoringStatus.monitoringTimeRestriction
              ? "監視時間制限が有効です"
              : "24時間監視モードです"
          }
        >
          <span className="status-icon">{statusInfo.icon}</span>
          <span className="status-text">{statusInfo.text}</span>
        </div>
      </div>
    </header>
  );
});

export default Header;
