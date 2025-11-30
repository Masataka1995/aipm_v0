@echo off
chcp 65001 > nul
setlocal

SET SCRIPT_DIR=%~dp0
CD /D "%SCRIPT_DIR%"

SET JAR_FILE=target\jicoo-reservation-bot-1.0.0.jar

ECHO Jicoo 自動予約 BOT (Web版) を起動します...

IF NOT EXIST "%JAR_FILE%" (
    ECHO.
    ECHO JARファイルが見つかりません。ビルドを開始します...
    CALL build.bat
    IF ERRORLEVEL 1 (
        ECHO.
        ECHO ビルドに失敗しました。
        PAUSE
        EXIT /B 1
    )
    ECHO.
    ECHO ビルドが完了しました。
)

ECHO.
ECHO Webサーバーを起動中...
REM バックグラウンドでサーバーを起動
REM /Bオプション: 同じコンソールウィンドウでバックグラウンド実行（ウィンドウを閉じてもプロセスは継続）
REM /MINオプション: 最小化された状態で起動
REM 実行可能JARファイルなので -jar オプションを使用
START /B /MIN "" java -jar "%JAR_FILE%"

REM サーバー起動を待つ（最大10秒）
SET SERVER_STARTED=0
FOR /L %%i IN (1,1,10) DO (
    timeout /t 1 /nobreak >NUL
    netstat -ano | findstr ":8080" | findstr "LISTENING" >NUL
    IF NOT ERRORLEVEL 1 (
        SET SERVER_STARTED=1
        GOTO :server_ready
    )
)

:server_ready
IF %SERVER_STARTED%==0 (
    ECHO.
    ECHO [警告] サーバーの起動確認に失敗しました。
    ECHO ログファイル（logs/jicoo-bot.log）を確認してください。
    ECHO.
    timeout /t 2 /nobreak >NUL
) ELSE (
    ECHO サーバーが正常に起動しました。
    ECHO.
    ECHO ブラウザを開きます: http://localhost:8080/index.html
    start "" "http://localhost:8080/index.html"
)

ECHO.
ECHO ========================================
ECHO サーバーはバックグラウンドで実行中です。
ECHO システムトレイのアイコンから操作できます。
ECHO 停止するには、システムトレイアイコンを右クリック→終了
ECHO ========================================
ECHO.
ECHO このウィンドウは閉じても問題ありません。
timeout /t 2 /nobreak >NUL

IF ERRORLEVEL 1 (
    ECHO.
    ECHO アプリケーションの実行中にエラーが発生しました。
    ECHO エラーコード: %ERRORLEVEL%
    PAUSE
    EXIT /B 1
)

EXIT /B 0
