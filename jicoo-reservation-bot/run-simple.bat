@echo off
REM 最もシンプルな起動スクリプト
REM コンソールに直接出力し、エラー時も閉じない

chcp 65001 >nul
cd /d "%~dp0"

REM ログディレクトリを作成
if not exist logs mkdir logs

REM JARファイルの確認
if not exist "target\jicoo-reservation-bot-1.0.0.jar" (
    echo.
    echo [エラー] JARファイルが見つかりません。
    echo ビルドを実行してください: build.bat
    echo.
    pause
    exit /b 1
)

REM Javaの確認
where java >nul 2>&1
if errorlevel 1 (
    echo.
    echo [エラー] Javaがインストールされていないか、PATHに設定されていません。
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo Jicoo Reservation Bot - Web Server
echo ========================================
echo.
echo サーバーを起動しています...
echo ログは logs\jicoo-bot.log に記録されます。
echo.

REM サーバーを別ウィンドウで起動（UTF-8エンコーディングを設定）
START "Jicoo Reservation Bot - Web Server" cmd /k "chcp 65001 >nul && java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -jar target\jicoo-reservation-bot-1.0.0.jar"

REM サーバー起動を待つ（最大20秒）
echo サーバーの起動を待っています（最大20秒）...
SET SERVER_STARTED=0
FOR /L %%i IN (1,1,20) DO (
    timeout /t 1 /nobreak >NUL
    REM ポート8080がLISTENING状態か確認
    netstat -ano 2>NUL | findstr ":8080" | findstr "LISTENING" >NUL
    IF NOT ERRORLEVEL 1 (
        SET SERVER_STARTED=1
        echo サーバーが起動しました。
        GOTO :server_ready
    )
    IF %%i LEQ 10 (
        echo 待機中... (%%i/20秒)
    ) ELSE IF %%i EQU 15 (
        echo 待機中... (%%i/20秒) - まだ起動していない可能性があります
    )
)

:server_ready
IF %SERVER_STARTED%==1 (
    echo.
    echo [成功] サーバーが正常に起動しました。
    echo.
    echo Webアプリケーション: http://localhost:8080/index.html
    echo APIエンドポイント: http://localhost:8080/api/
    echo.
    echo ブラウザを開きます...
    timeout /t 1 /nobreak >NUL
    start "" "http://localhost:8080/index.html"
    echo.
    echo ========================================
    echo サーバーはバックグラウンドで実行中です。
    echo システムトレイのアイコンから操作できます。
    echo 停止するには、システムトレイアイコンを右クリック→終了
    echo ========================================
    echo.
    echo このウィンドウを閉じても問題ありません。
    timeout /t 2 /nobreak >NUL
) ELSE (
    echo.
    echo [警告] サーバーの起動確認に失敗しました（20秒経過）。
    echo サーバーは起動している可能性があります。
    echo.
    echo ブラウザを開きます...
    timeout /t 1 /nobreak >NUL
    start "" "http://localhost:8080/index.html"
    echo.
    echo ブラウザで http://localhost:8080/index.html にアクセスしてください。
    echo.
    timeout /t 3 /nobreak >NUL
)

