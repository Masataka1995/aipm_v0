import React, { useEffect, useRef, memo } from "react";
import { formatTimestamp } from "../utils/dateUtils";
import { getLogIcon } from "../utils/iconUtils";

const LogSection = memo(function LogSection({ logs }) {
  const logAreaRef = useRef(null);
  const shouldAutoScrollRef = useRef(true);

  // スクロール位置を監視（ユーザーが上にスクロールした場合は自動スクロールを無効化）
  useEffect(() => {
    const logArea = logAreaRef.current;
    if (!logArea) return;

    const handleScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = logArea;
      // 最下部から50px以内にいる場合のみ自動スクロール
      shouldAutoScrollRef.current =
        scrollHeight - scrollTop - clientHeight < 50;
    };

    logArea.addEventListener("scroll", handleScroll);
    return () => logArea.removeEventListener("scroll", handleScroll);
  }, []);

  useEffect(() => {
    if (logAreaRef.current && shouldAutoScrollRef.current) {
      logAreaRef.current.scrollTop = logAreaRef.current.scrollHeight;
    }
  }, [logs]);

  return (
    <section className="log-section">
      <h2>
        <span className="section-icon">📋</span>
        ログ出力
        <span className="badge">{logs.length}</span>
      </h2>
      <div id="log-area" className="log-area" ref={logAreaRef}>
        {logs.length === 0 ? (
          <div className="log-empty">
            <span className="empty-icon">📝</span>
            <span className="empty-text">ログがありません</span>
          </div>
        ) : (
          logs.map((log, index) => (
            <div key={index} className={`log-line ${log.level}`}>
              <span className="log-timestamp">
                [{formatTimestamp(log.timestamp)}]
              </span>
              <span className="log-icon">{getLogIcon(log.level)}</span>
              <span className="log-message">{log.message}</span>
            </div>
          ))
        )}
      </div>
    </section>
  );
});

export default LogSection;
