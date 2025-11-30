@echo off
chcp 65001 >nul
setlocal

REM スクリプトのディレクトリに移動
cd /d "%~dp0"

REM JARファイルの存在確認
if not exist "target\jicoo-reservation-bot-1.0.0.jar" (
    echo JARファイルが見つかりません。ビルドを実行します...
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo ビルドに失敗しました。
        pause
        exit /b 1
    )
)

REM アプリケーションを起動
echo Jicoo 自動予約 BOT を起動します...
java -jar "target\jicoo-reservation-bot-1.0.0.jar"

if errorlevel 1 (
    echo.
    echo アプリケーションの起動に失敗しました。
    echo Javaがインストールされているか確認してください。
    pause
    exit /b 1
)

endlocal

