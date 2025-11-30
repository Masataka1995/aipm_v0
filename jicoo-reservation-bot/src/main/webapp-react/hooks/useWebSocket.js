import { useEffect, useRef, useCallback } from "react";

export function useWebSocket({ onMessage, onOpen, onClose, onError }) {
  const wsRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);
  const reconnectAttemptsRef = useRef(0);
  const isManualCloseRef = useRef(false);
  const callbacksRef = useRef({ onMessage, onOpen, onClose, onError });

  // コールバックを最新の状態に保つ
  useEffect(() => {
    callbacksRef.current = { onMessage, onOpen, onClose, onError };
  }, [onMessage, onOpen, onClose, onError]);

  const connect = useCallback(() => {
    // 既存の接続を閉じる
    if (wsRef.current) {
      try {
        if (
          wsRef.current.readyState === WebSocket.OPEN ||
          wsRef.current.readyState === WebSocket.CONNECTING
        ) {
          isManualCloseRef.current = true;
          wsRef.current.close();
        }
      } catch (error) {
        console.warn("既存のWebSocket接続を閉じる際にエラー:", error);
      }
      wsRef.current = null;
    }

    // 再接続タイムアウトをクリア
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${protocol}//${window.location.host}/ws`;

    console.log(
      `WebSocket接続を試みます: ${wsUrl} (試行回数: ${
        reconnectAttemptsRef.current + 1
      })`
    );

    try {
      const ws = new WebSocket(wsUrl);

      ws.onopen = () => {
        console.log("WebSocket接続が確立されました");
        reconnectAttemptsRef.current = 0; // 接続成功したらリセット
        isManualCloseRef.current = false;

        if (callbacksRef.current.onOpen) {
          callbacksRef.current.onOpen();
        }

        // 再接続タイムアウトをクリア
        if (reconnectTimeoutRef.current) {
          clearTimeout(reconnectTimeoutRef.current);
          reconnectTimeoutRef.current = null;
        }
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (callbacksRef.current.onMessage) {
            callbacksRef.current.onMessage(data);
          }
        } catch (error) {
          console.error("WebSocketメッセージ解析エラー:", error);
        }
      };

      ws.onclose = (event) => {
        console.log("WebSocket接続が切断されました", {
          code: event.code,
          reason: event.reason,
          wasClean: event.wasClean,
        });

        wsRef.current = null;

        if (callbacksRef.current.onClose) {
          callbacksRef.current.onClose();
        }

        // 手動で閉じた場合は再接続しない
        if (isManualCloseRef.current) {
          console.log("手動で閉じられたため、再接続しません");
          isManualCloseRef.current = false;
          return;
        }

        // 正常な切断（コード1000）の場合は再接続しない
        if (event.code === 1000) {
          console.log("正常な切断のため、再接続しません");
          return;
        }

        // 再接続を試みる（指数バックオフ）
        reconnectAttemptsRef.current += 1;
        const delay = Math.min(
          1000 * Math.pow(2, reconnectAttemptsRef.current - 1),
          30000
        ); // 最大30秒

        console.log(
          `${delay}ms後に再接続を試みます... (試行回数: ${reconnectAttemptsRef.current})`
        );

        reconnectTimeoutRef.current = setTimeout(() => {
          reconnectTimeoutRef.current = null;
          connect();
        }, delay);
      };

      ws.onerror = (error) => {
        console.error("WebSocketエラー:", error);
        console.error("WebSocket URL:", wsUrl);
        console.error("WebSocket readyState:", ws.readyState);

        if (callbacksRef.current.onError) {
          callbacksRef.current.onError(error);
        }
      };

      wsRef.current = ws;
    } catch (error) {
      console.error("WebSocket接続の作成に失敗しました:", error);

      // 再接続を試みる
      reconnectAttemptsRef.current += 1;
      const delay = Math.min(
        1000 * Math.pow(2, reconnectAttemptsRef.current - 1),
        30000
      );

      reconnectTimeoutRef.current = setTimeout(() => {
        reconnectTimeoutRef.current = null;
        connect();
      }, delay);
    }
  }, []);

  useEffect(() => {
    connect();

    return () => {
      isManualCloseRef.current = true;

      if (wsRef.current) {
        try {
          wsRef.current.close();
        } catch (error) {
          console.warn("WebSocket接続を閉じる際にエラー:", error);
        }
        wsRef.current = null;
      }

      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
    };
  }, [connect]);

  const sendMessage = useCallback((message) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      try {
        wsRef.current.send(JSON.stringify(message));
      } catch (error) {
        console.error("WebSocketメッセージ送信エラー:", error);
      }
    } else {
      console.warn(
        "WebSocketが接続されていません。メッセージを送信できません。"
      );
    }
  }, []);

  return { sendMessage };
}
