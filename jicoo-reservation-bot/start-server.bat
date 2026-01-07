@echo off
REM シンプルな起動スクリプト
REM PowerShellスクリプトを実行する

cd /d "%~dp0"

REM PowerShellスクリプトを実行
powershell -ExecutionPolicy Bypass -File "%~dp0run-server.ps1"

pause

