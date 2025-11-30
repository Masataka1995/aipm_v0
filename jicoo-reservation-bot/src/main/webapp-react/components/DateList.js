import React, { memo, useCallback } from "react";
import { formatDate } from "../utils/dateUtils";
import { getReservationStatusIcon } from "../utils/iconUtils";

const DateList = memo(function DateList({
  dates,
  availableTimeSlots,
  onRemove,
  onToggle,
  onUpdateTimeSlots,
}) {
  const handleTimeSlotChange = useCallback(
    (dateStr, timeSlot, checked) => {
      const dateInfo = dates.find((d) => d.date === dateStr);
      if (!dateInfo) return;

      let selectedSlots = dateInfo.selectedTimeSlots || [];
      if (checked) {
        if (!selectedSlots.includes(timeSlot)) {
          selectedSlots = [...selectedSlots, timeSlot];
        }
      } else {
        selectedSlots = selectedSlots.filter((s) => s !== timeSlot);
      }

      onUpdateTimeSlots(dateStr, selectedSlots);
    },
    [dates, onUpdateTimeSlots]
  );

  return (
    <div id="date-list" className="date-list">
      {dates.length === 0 ? (
        <div className="empty-state">
          <span className="empty-icon">📅</span>
          <span className="empty-text">予約対象日付がありません</span>
          <span className="empty-hint">
            カレンダーまたは日付選択から追加してください
          </span>
        </div>
      ) : (
        dates.map((dateInfo) => (
          <div
            key={dateInfo.date}
            className={`date-item ${
              dateInfo.enabled ? "enabled" : "disabled"
            } ${dateInfo.status?.toLowerCase() || "pending"}`}
          >
            <div className="date-info">
              <div className="date-header">
                <span className="status-icon">
                  {getReservationStatusIcon(dateInfo.status)}
                </span>
                <span className="date-label">{formatDate(dateInfo.date)}</span>
                {dateInfo.enabled && (
                  <span
                    className="enabled-badge"
                    title="この日付は監視対象です"
                  >
                    ON
                  </span>
                )}
              </div>
              {((dateInfo.selectedTimeSlots &&
                dateInfo.selectedTimeSlots.length > 0) ||
                dateInfo.enabled) && (
                <div className="time-slots">
                  <span className="time-slots-label">時間帯:</span>
                  <div className="time-slots-grid">
                    {availableTimeSlots.map((slot) => {
                      const isSelected = (
                        dateInfo.selectedTimeSlots || []
                      ).includes(slot);
                      return (
                        <label
                          key={slot}
                          className={`time-slot-checkbox ${
                            isSelected ? "selected" : ""
                          }`}
                          title={`${slot}を${isSelected ? "解除" : "選択"}`}
                        >
                          <input
                            type="checkbox"
                            value={slot}
                            checked={isSelected}
                            onChange={(e) =>
                              handleTimeSlotChange(
                                dateInfo.date,
                                slot,
                                e.target.checked
                              )
                            }
                          />
                          <span className="time-slot-text">{slot}</span>
                        </label>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
            <div className="date-actions">
              <button
                className={`btn btn-small toggle-btn ${
                  dateInfo.enabled ? "btn-success" : "btn-secondary"
                }`}
                onClick={() => onToggle(dateInfo.date, !dateInfo.enabled)}
                title={`監視を${dateInfo.enabled ? "無効" : "有効"}にする`}
              >
                {dateInfo.enabled ? "✓ ON" : "○ OFF"}
              </button>
              <button
                className="btn btn-small btn-danger"
                onClick={() => onRemove(dateInfo.date)}
                title="この日付を削除します"
              >
                🗑️
              </button>
            </div>
          </div>
        ))
      )}
    </div>
  );
});

export default DateList;
