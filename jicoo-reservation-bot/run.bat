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

REM Javaの存在確認
where java >nul 2>&1
IF ERRORLEVEL 1 (
    ECHO.
    ECHO [エラー] Javaがインストールされていないか、PATHに設定されていません。
    ECHO Javaをインストールしてから再度実行してください。
    PAUSE
    EXIT /B 1
)

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

REM JARファイルの存在確認
IF NOT EXIST "%JAR_FILE%" (
    ECHO [エラー] JARファイルが見つかりません: %JAR_FILE%
    ECHO ビルドを実行してください: build.bat
    PAUSE
    EXIT /B 1
)

REM GUI版を起動（JARファイルのクラスパスを使用）
java -cp "%JAR_FILE%" com.jicoo.bot.JicooReservationBotGUI

IF ERRORLEVEL 1 (
    ECHO.
    ECHO [エラー] アプリケーションの起動に失敗しました。
    ECHO ログファイル（logs/jicoo-bot.log）を確認してください。
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

REM JARファイルの存在確認
IF NOT EXIST "%JAR_FILE%" (
    ECHO [エラー] JARファイルが見つかりません: %JAR_FILE%
    ECHO ビルドを実行してください: build.bat
    PAUSE
    EXIT /B 1
)

ECHO Webサーバーを起動中...
ECHO.

REM 既存のJavaプロセスを確認（ポート8080を使用している可能性がある）
netstat -ano 2>NUL | findstr ":8080" | findstr "LISTENING" >NUL
IF NOT ERRORLEVEL 1 (
    ECHO [警告] ポート8080は既に使用されています。
    ECHO 既存のサーバーを停止するか、別のポートを使用してください。
    ECHO.
    ECHO ブラウザで http://localhost:8080/index.html にアクセスしてください。
    timeout /t 3 /nobreak >NUL
    GOTO :end
)

REM エラーログファイルのディレクトリを作成（存在しない場合）
IF NOT EXIST logs mkdir logs

REM エラーログファイルのパス
SET LOG_FILE=%~dp0logs\server-error.log
SET CURRENT_DIR=%~dp0
SET FULL_JAR_PATH=%CURRENT_DIR%%JAR_FILE%

REM シンプルなバッチファイルを作成（確実にログを取得）
SET TEMP_BAT=%~dp0logs\run-server.bat

REM バッチファイルを作成（変数を展開して絶対パスを使用）
(
    echo @echo off
    echo chcp 65001 ^>nul
    echo setlocal enabledelayedexpansion
    echo cd /d "%CURRENT_DIR%"
    echo.
    echo echo ========================================
    echo echo Jicoo Reservation Bot - Web Server
    echo echo ========================================
    echo echo.
    echo echo サーバーを起動しています...
    echo echo ログファイル: %LOG_FILE%
    echo echo.
    echo echo 開始時刻: %%date%% %%time%%
    echo echo 開始時刻: %%date%% %%time%% ^>^> "%LOG_FILE%"
    echo echo.
    echo.
    echo REM Javaアプリケーションを実行（コンソールに直接表示、UTF-8エンコーディング設定）
    echo REM ログはlogback.xmlで設定されたlogs\jicoo-bot.logにも記録されます
    echo REM エラー出力も含めて、すべてコンソールに表示されます
    echo java -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -jar "%FULL_JAR_PATH%"
    echo SET EXIT_CODE=%%ERRORLEVEL%%
    echo.
    echo echo 終了時刻: %%date%% %%time%%
    echo echo 終了コード: %%EXIT_CODE%%
    echo echo 終了時刻: %%date%% %%time%% ^>^> "%LOG_FILE%"
    echo echo 終了コード: %%EXIT_CODE%% ^>^> "%LOG_FILE%"
    echo.
    echo if %%EXIT_CODE%% NEQ 0 (
    echo     echo.
    echo     echo ========================================
    echo     echo エラーが発生しました！
    echo     echo ========================================
    echo     echo.
    echo     echo 終了コード: %%EXIT_CODE%%
    echo     echo.
    echo     if exist "%LOG_FILE%" (
    echo         echo === エラーログファイルの内容 ===
    echo         type "%LOG_FILE%"
    echo         echo === エラーログファイルの内容（終了） ===
    echo     ) else (
    echo         echo エラーログファイルが見つかりません: %LOG_FILE%
    echo     )
    echo     echo.
    echo     pause
    echo ) else (
    echo     echo サーバーが正常に終了しました。
    echo     echo このウィンドウを閉じるには、何かキーを押してください...
    echo     pause
    echo )
) > "%TEMP_BAT%"

REM 作成されたバッチファイルの内容を確認（デバッグ用）
ECHO [情報] 一時バッチファイルを作成しました: logs\run-server.bat
ECHO [情報] サーバーを起動します...
ECHO [情報] コンソールウィンドウが開きます。ログがリアルタイムで表示されます。
ECHO [情報] サーバーが起動すると、ログがコンソールに表示されます。
ECHO.

REM バッチファイルを実行（ウィンドウを開いたままにする）
START "Jicoo Reservation Bot - Web Server" cmd /k ""%TEMP_BAT%""

REM 少し待機してからログファイルを確認
timeout /t 5 /nobreak >NUL

REM ログファイルの確認
IF EXIST "%LOG_FILE%" (
    ECHO.
    ECHO [情報] ログファイルが作成されました: logs\server-error.log
    ECHO.
    ECHO === ログファイルの内容（最初の30行） ===
    powershell -Command "if (Test-Path '%LOG_FILE%') { Get-Content '%LOG_FILE%' -Encoding UTF8 -TotalCount 30 } else { Write-Host 'ファイルが見つかりません' }"
    ECHO === ログファイルの内容（終了） ===
    ECHO.
) ELSE (
    ECHO [警告] ログファイルがまだ作成されていません。
    ECHO サーバーが起動中か、エラーが発生している可能性があります。
    ECHO.
)

REM サーバー起動を待つ（最大20秒）
ECHO サーバーの起動を待っています（最大20秒）...
SET SERVER_STARTED=0
FOR /L %%i IN (1,1,20) DO (
    timeout /t 1 /nobreak >NUL
    REM ポート8080がLISTENING状態か確認
    netstat -ano 2>NUL | findstr ":8080" | findstr "LISTENING" >NUL
    IF NOT ERRORLEVEL 1 (
        SET SERVER_STARTED=1
        ECHO ポート8080でサーバーが検出されました。
        GOTO :server_ready
    )
    IF %%i LEQ 10 (
        ECHO 待機中... (%%i/20秒)
    ) ELSE IF %%i EQU 15 (
        ECHO 待機中... (%%i/20秒) - まだ起動していない可能性があります
    )
)

:server_ready
IF !SERVER_STARTED!==0 (
    ECHO.
    ECHO [警告] サーバーの起動確認に失敗しました（20秒経過）。
    ECHO.
    ECHO 確認事項:
    ECHO   1. 「Jicoo Reservation Bot - Web Server」というタイトルのウィンドウを確認してください
    ECHO   2. ログファイルを確認: logs\server-error.log
    ECHO   3. ログファイルを確認: logs\jicoo-bot.log
    ECHO   4. Javaプロセスが実行中か確認: tasklist ^| findstr java.exe
    ECHO   5. ポート8080が使用中か確認: netstat -ano ^| findstr :8080
    ECHO.
    
    REM Javaプロセスの確認
    tasklist | findstr java.exe >NUL
    IF ERRORLEVEL 1 (
        ECHO [エラー] Javaプロセスが実行されていません。
        ECHO サーバーの起動に失敗した可能性があります。
        ECHO.
    ) ELSE (
        ECHO [情報] Javaプロセスは実行中です。
        ECHO サーバーは起動しているが、ポート8080で待機していない可能性があります。
        ECHO.
    )
    
    REM エラーログファイルの確認
    IF EXIST "%LOG_FILE%" (
        ECHO === エラーログファイル（logs\server-error.log）の内容 ===
        powershell -Command "Get-Content '%LOG_FILE%' -Encoding UTF8"
        ECHO === エラーログファイルの内容（終了） ===
        ECHO.
    ) ELSE (
        ECHO [警告] エラーログファイルが見つかりません: logs\server-error.log
        ECHO.
    )
    
    REM ログファイルの確認
    IF EXIST logs\jicoo-bot.log (
        ECHO === ログファイル（logs\jicoo-bot.log）の最後の30行 ===
        powershell -Command "Get-Content logs\jicoo-bot.log -Encoding UTF8 -Tail 30"
        ECHO === ログファイルの最後の30行（終了） ===
        ECHO.
    ) ELSE (
        ECHO [警告] ログファイルが見つかりません: logs\jicoo-bot.log
        ECHO サーバーが起動していない可能性があります。
        ECHO.
    )
    
    ECHO サーバーは起動している可能性があります。
    ECHO ブラウザで http://localhost:8080/index.html にアクセスしてください。
    ECHO.
    timeout /t 5 /nobreak >NUL
) ELSE (
    ECHO.
    ECHO [成功] サーバーが正常に起動しました。
    ECHO.
    ECHO Webアプリケーション: http://localhost:8080/index.html
    ECHO APIエンドポイント: http://localhost:8080/api/
    ECHO.
    ECHO ブラウザを開きます...
    timeout /t 1 /nobreak >NUL
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
ECHO.
ECHO ログファイルの場所:
ECHO   - サーバー出力: logs\server-error.log
ECHO   - アプリケーションログ: logs\jicoo-bot.log
ECHO.
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
