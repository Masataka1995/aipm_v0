import React, { useState, memo } from "react";
import Calendar from "./Calendar";
import DateList from "./DateList";
import { AVAILABLE_TIME_SLOTS } from "../constants/timeSlots";

const DateManagement = memo(function DateManagement({
  dates,
  onAddDate,
  onRemoveDate,
  onToggleDate,
  onUpdateTimeSlots,
}) {
  const [selectedDate, setSelectedDate] = useState("");

  const handleAddDate = () => {
    if (selectedDate) {
      const selected = new Date(selectedDate);
      const today = new Date();
      today.setHours(0, 0, 0, 0);

      if (selected < today) {
        alert("過去の日付は選択できません。今日以降の日付を選択してください。");
        return;
      }

      onAddDate(selectedDate);
      setSelectedDate("");
    } else {
      alert("日付を選択してください");
    }
  };

  return (
    <section className="date-management">
      <h2>
        <span className="section-icon">📅</span>
        予約対象日付管理
        <span className="badge">{dates.length}</span>
      </h2>

      <div className="add-date-panel">
        <label htmlFor="date-picker" className="date-picker-label">
          <span className="label-icon">📆</span>
          日付を選択
        </label>
        <input
          type="date"
          id="date-picker"
          className="date-picker"
          value={selectedDate}
          onChange={(e) => setSelectedDate(e.target.value)}
          min={new Date().toISOString().split("T")[0]}
          title="予約したい日付を選択してください"
        />
        <button
          id="add-date-btn"
          className="btn btn-primary"
          onClick={handleAddDate}
          disabled={!selectedDate}
          title="選択した日付を追加します"
        >
          <span className="btn-icon">➕</span>
          追加
        </button>
      </div>

      <Calendar dates={dates} onDateClick={onAddDate} />

      <DateList
        dates={dates}
        availableTimeSlots={AVAILABLE_TIME_SLOTS}
        onRemove={onRemoveDate}
        onToggle={onToggleDate}
        onUpdateTimeSlots={onUpdateTimeSlots}
      />
    </section>
  );
});

export default DateManagement;
