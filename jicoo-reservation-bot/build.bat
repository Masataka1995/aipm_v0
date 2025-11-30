@echo off
chcp 65001 >nul
setlocal

REM スクリプトのディレクトリに移動
cd /d "%~dp0"

echo ========================================
echo Jicoo 自動予約 BOT をビルドします...
echo ========================================
echo.

REM 実行中のJavaプロセスを停止（JARファイルが使用中の場合があるため）
echo 実行中のJavaプロセスを確認中...
tasklist /FI "IMAGENAME eq java.exe" 2>NUL | find /I /N "java.exe">NUL
if "%ERRORLEVEL%"=="0" (
    echo 実行中のJavaプロセスを停止します...
    taskkill /F /IM java.exe >NUL 2>&1
    timeout /t 2 /nobreak >NUL
)

echo.
echo Mavenでビルドを開始します...
echo.
call mvn clean package -DskipTests

if errorlevel 1 (
    echo.
    echo ========================================
    echo ビルドに失敗しました。
    echo ========================================
    pause
    exit /b 1
) else (
    echo.
    echo ========================================
    echo ビルドが完了しました！
    echo ========================================
    echo.
    echo 実行するには以下のいずれかを実行してください:
    echo   - run.bat          (GUI版)
    echo   - run-web.bat      (Web版)
    echo.
)

pause
endlocal

