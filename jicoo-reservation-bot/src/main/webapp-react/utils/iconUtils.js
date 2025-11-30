/**
 * アイコン取得ユーティリティ
 */

/**
 * ステータスに応じたアイコンを取得（予約ステータス用）
 * @param {string} status - ステータス（SUCCESS, FAILED, PENDING等）
 * @returns {string} アイコン文字列
 */
export function getReservationStatusIcon(status) {
  switch (status?.toLowerCase()) {
    case "success":
      return "✅";
    case "failed":
      return "❌";
    case "pending":
      return "⏳";
    default:
      return "⚪";
  }
}

/**
 * ログレベルに応じたアイコンを取得
 * @param {string} level - ログレベル（success, error, warn, info）
 * @returns {string} アイコン文字列
 */
export function getLogIcon(level) {
  switch (level) {
    case "success":
      return "✅";
    case "error":
      return "❌";
    case "warn":
      return "⚠️";
    case "info":
    default:
      return "ℹ️";
  }
}

/**
 * 監視ステータスに応じたアイコンを取得
 * @param {string} status - ステータス（実行中, 停止, 待機中）
 * @returns {string} アイコン文字列
 */
export function getMonitoringStatusIcon(status) {
  switch (status) {
    case "実行中":
      return "🟢";
    case "停止":
      return "🔴";
    case "待機中":
      return "🟡";
    default:
      return "⚪";
  }
}
