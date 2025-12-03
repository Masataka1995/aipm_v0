@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM スクリプトのディレクトリに移動
cd /d "%~dp0"

SET JAR_FILE=target\jicoo-reservation-bot-1.0.0.jar
SET MODE=web

REM 引数チェック
IF "%~1"=="--gui" SET MODE=gui
IF "%~1"=="-g" SET MODE=gui
IF "%~1"=="--web" SET MODE=web
IF "%~1"=="-w" SET MODE=web
IF "%~1"=="/?" GOTO :help
IF "%~1"=="--help" GOTO :help
IF "%~1"=="-h" GOTO :help

REM JARファイルの存在確認
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

IF "%MODE%"=="gui" GOTO :gui_mode
IF "%MODE%"=="web" GOTO :web_mode

:gui_mode
ECHO.
ECHO ========================================
ECHO Jicoo 自動予約 BOT (GUI版) を起動します...
ECHO ========================================
ECHO.
java -jar "%JAR_FILE%"

IF ERRORLEVEL 1 (
    ECHO.
    ECHO アプリケーションの起動に失敗しました。
    ECHO Javaがインストールされているか確認してください。
    PAUSE
    EXIT /B 1
)
GOTO :end

:web_mode
ECHO.
ECHO ========================================
ECHO Jicoo 自動予約 BOT (Web版) を起動します...
ECHO ========================================
ECHO.
ECHO Webサーバーを起動中...
REM バックグラウンドでサーバーを起動
REM /Bオプション: 同じコンソールウィンドウでバックグラウンド実行（ウィンドウを閉じてもプロセスは継続）
REM /MINオプション: 最小化された状態で起動
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
GOTO :end

:help
ECHO.
ECHO 使用方法:
ECHO   run.bat           Web版を起動（デフォルト）
ECHO   run.bat --web     Web版を起動
ECHO   run.bat --gui     GUI版（Swing）を起動
ECHO   run.bat -w        Web版を起動（短縮形）
ECHO   run.bat -g        GUI版を起動（短縮形）
ECHO   run.bat --help    このヘルプを表示
ECHO.
EXIT /B 0

:end
endlocal
EXIT /B 0
