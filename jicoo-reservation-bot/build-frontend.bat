@echo off
chcp 65001 > nul
setlocal

SET SCRIPT_DIR=%~dp0
CD /D "%SCRIPT_DIR%"

ECHO Reactフロントエンドをビルドします...

IF NOT EXIST "node_modules" (
    ECHO npmパッケージをインストールします...
    CALL npm install
    IF ERRORLEVEL 1 (
        ECHO.
        ECHO npmインストールに失敗しました。
        PAUSE
        EXIT /B 1
    )
)

ECHO.
ECHO Webpackでビルドを開始します...
CALL npm run build

IF ERRORLEVEL 1 (
    ECHO.
    ECHO ビルドに失敗しました。
    IF "%1"=="" (
        PAUSE
    )
    EXIT /B 1
) ELSE (
    ECHO.
    ECHO ビルドが完了しました。
    ECHO bundle.jsが src/main/webapp に生成されました。
)

IF "%1"=="" (
    PAUSE
)
endlocal

