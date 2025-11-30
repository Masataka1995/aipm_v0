import React, { useState, memo, useMemo, useCallback } from "react";
import { formatDateString } from "../utils/dateUtils";

const Calendar = memo(function Calendar({ dates, onDateClick }) {
  const [currentMonth, setCurrentMonth] = useState(new Date());

  const handlePrevMonth = useCallback(() => {
    setCurrentMonth((prev) => {
      const newDate = new Date(prev);
      newDate.setMonth(newDate.getMonth() - 1);
      return newDate;
    });
  }, []);

  const handleNextMonth = useCallback(() => {
    setCurrentMonth((prev) => {
      const newDate = new Date(prev);
      newDate.setMonth(newDate.getMonth() + 1);
      return newDate;
    });
  }, []);

  const handleDateClick = useCallback(
    (date, isCurrentMonth) => {
      const dateStr = formatDateString(date);
      if (isCurrentMonth) {
        const dateInfo = dates.find((d) => d.date === dateStr);
        if (!dateInfo) {
          onDateClick(dateStr);
        }
      } else {
        // 他の月の日付をクリックした場合は、その月に移動
        setCurrentMonth(new Date(date.getFullYear(), date.getMonth(), 1));
      }
    },
    [dates, formatDateString, onDateClick]
  );

  const { year, month, calendarDays, dayNames } = useMemo(() => {
    const year = currentMonth.getFullYear();
    const month = currentMonth.getMonth();
    const firstDay = new Date(year, month, 1);
    const startDate = new Date(firstDay);
    startDate.setDate(startDate.getDate() - firstDay.getDay());

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const dayNames = ["日", "月", "火", "水", "木", "金", "土"];
    const calendarDays = [];

    // datesをMapに変換して高速検索
    const datesMap = new Map(dates.map((d) => [d.date, d]));

    for (let i = 0; i < 42; i++) {
      const currentDate = new Date(startDate);
      currentDate.setDate(startDate.getDate() + i);
      const dateStr = formatDateString(currentDate);
      const dateInfo = datesMap.get(dateStr); // Mapを使った高速検索
      const isCurrentMonth = currentDate.getMonth() === month;
      const isToday = currentDate.getTime() === today.getTime();

      calendarDays.push({
        date: currentDate,
        dateStr,
        dateInfo,
        isCurrentMonth,
        isToday,
      });
    }

    return { year, month, calendarDays, dayNames };
  }, [currentMonth, dates, formatDateString]);

  return (
    <div className="calendar-container">
      <div className="calendar-header">
        <button
          className="calendar-nav-btn"
          onClick={handlePrevMonth}
          title="前の月"
        >
          ◀
        </button>
        <span className="calendar-month-year">
          {year}年{month + 1}月
        </span>
        <button
          className="calendar-nav-btn"
          onClick={handleNextMonth}
          title="次の月"
        >
          ▶
        </button>
      </div>
      <div className="calendar">
        {dayNames.map((dayName) => (
          <div key={dayName} className="calendar-day-header">
            {dayName}
          </div>
        ))}
        {calendarDays.map((day, index) => {
          const classes = ["calendar-day"];
          if (!day.isCurrentMonth) classes.push("other-month");
          if (day.isToday) classes.push("today");
          if (day.dateInfo) {
            classes.push("has-reservation");
            if (day.dateInfo.status === "SUCCESS") classes.push("success");
            if (day.dateInfo.status === "FAILED") classes.push("failed");
            if (day.dateInfo.enabled) classes.push("selected");
          }

          const getTooltip = () => {
            if (!day.isCurrentMonth) return "他の月の日付（クリックで移動）";
            if (day.dateInfo) {
              const statusText =
                day.dateInfo.status === "SUCCESS"
                  ? "予約成功"
                  : day.dateInfo.status === "FAILED"
                  ? "予約失敗"
                  : "予約待機中";
              return `${day.dateStr} - ${statusText}${
                day.dateInfo.enabled ? " (監視中)" : ""
              }`;
            }
            return `${day.dateStr} - クリックで追加`;
          };

          return (
            <div
              key={index}
              className={classes.join(" ")}
              onClick={() => handleDateClick(day.date, day.isCurrentMonth)}
              title={getTooltip()}
            >
              {day.date.getDate()}
              {day.dateInfo && day.dateInfo.enabled && (
                <span className="calendar-day-badge">●</span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
});

export default Calendar;
