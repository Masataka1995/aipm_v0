/**
 * 日付フォーマットユーティリティ
 */

/**
 * 日付文字列を日本語形式にフォーマット
 * @param {string} dateStr - YYYY-MM-DD形式の日付文字列
 * @returns {string} フォーマットされた日付文字列（例: "2025/11/22(土)"）
 */
export function formatDate(dateStr) {
  const date = new Date(dateStr);
  return date.toLocaleDateString("ja-JP", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "short",
  });
}

/**
 * DateオブジェクトをYYYY-MM-DD形式の文字列に変換
 * @param {Date} date - Dateオブジェクト
 * @returns {string} YYYY-MM-DD形式の日付文字列
 */
export function formatDateString(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * タイムスタンプを時刻形式にフォーマット
 * @param {Date|string|number} timestamp - タイムスタンプ
 * @returns {string} フォーマットされた時刻文字列（例: "14:30:25"）
 */
export function formatTimestamp(timestamp) {
  if (!timestamp) return "";
  const date = new Date(timestamp);
  return date.toLocaleTimeString("ja-JP", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}
