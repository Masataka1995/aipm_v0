# Jicoo Reservation Bot - Web Server 起動スクリプト
# PowerShell版

$ErrorActionPreference = "Continue"
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# スクリプトのディレクトリに移動
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# 設定
$jarFile = Join-Path $scriptDir "target\jicoo-reservation-bot-1.0.0.jar"
$logDir = Join-Path $scriptDir "logs"
$logFile = Join-Path $logDir "jicoo-bot.log"
$errorLogFile = Join-Path $logDir "server-error.log"

# ログディレクトリを作成
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}

# JARファイルの存在確認
if (-not (Test-Path $jarFile)) {
    Write-Host ""
    Write-Host "[エラー] JARファイルが見つかりません: $jarFile" -ForegroundColor Red
    Write-Host "ビルドを実行してください: build.bat" -ForegroundColor Yellow
    Read-Host "Enterキーを押して終了"
    exit 1
}

# Javaの存在確認
try {
    $javaVersion = java -version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Javaが見つかりません"
    }
} catch {
    Write-Host ""
    Write-Host "[エラー] Javaがインストールされていないか、PATHに設定されていません。" -ForegroundColor Red
    Write-Host "Javaをインストールしてから再度実行してください。" -ForegroundColor Yellow
    Read-Host "Enterキーを押して終了"
    exit 1
}

# ポート8080の確認
$portInUse = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($portInUse) {
    Write-Host ""
    Write-Host "[警告] ポート8080は既に使用されています。" -ForegroundColor Yellow
    Write-Host "既存のサーバーを停止するか、別のポートを使用してください。" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "ブラウザで http://localhost:8080/index.html にアクセスしてください。" -ForegroundColor Cyan
    Start-Sleep -Seconds 3
    exit 0
}

# サーバー起動
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Jicoo Reservation Bot - Web Server" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "サーバーを起動しています..." -ForegroundColor Yellow
Write-Host "ログファイル: $logFile" -ForegroundColor Yellow
Write-Host "エラーログファイル: $errorLogFile" -ForegroundColor Yellow
Write-Host ""
Write-Host "開始時刻: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Green
Write-Host ""

# エラーログファイルに開始時刻を記録
"開始時刻: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" | Out-File -FilePath $errorLogFile -Encoding UTF8

# Javaプロセスを起動
try {
    Write-Host "Javaコマンドを実行: java -jar `"$jarFile`"" -ForegroundColor Gray
    Write-Host ""
    
    # Javaプロセスを起動し、出力をリアルタイムで表示しながらログファイルにも記録
    $process = Start-Process -FilePath "java" `
        -ArgumentList "-jar", "`"$jarFile`"" `
        -NoNewWindow `
        -PassThru `
        -RedirectStandardOutput $errorLogFile `
        -RedirectStandardError $errorLogFile
    
    Write-Host "プロセスID: $($process.Id)" -ForegroundColor Green
    Write-Host "ログファイル: $errorLogFile" -ForegroundColor Green
    Write-Host ""
    Write-Host "サーバーはバックグラウンドで実行中です。" -ForegroundColor Green
    Write-Host "ログはリアルタイムで $errorLogFile に記録されます。" -ForegroundColor Green
    Write-Host ""
    Write-Host "停止するには、このウィンドウを閉じるか、Ctrl+Cを押してください。" -ForegroundColor Yellow
    Write-Host ""
    
    # プロセスの終了を待機（バックグラウンドで実行）
    Write-Host "サーバーはバックグラウンドで実行中です。" -ForegroundColor Green
    Write-Host "ログファイルを監視中..." -ForegroundColor Yellow
    Write-Host ""
    
    # ログファイルの内容を定期的に表示（リアルタイム）
    $lastPosition = 0
    while (-not $process.HasExited) {
        Start-Sleep -Milliseconds 1000
        
        # ログファイルの新しい内容を読み取って表示
        if (Test-Path $errorLogFile) {
            try {
                $fileInfo = Get-Item $errorLogFile -ErrorAction SilentlyContinue
                if ($fileInfo -and $fileInfo.Length -gt $lastPosition) {
                    $stream = [System.IO.File]::OpenRead($errorLogFile)
                    $stream.Position = $lastPosition
                    $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
                    
                    while (-not $reader.EndOfStream) {
                        $line = $reader.ReadLine()
                        if ($line) {
                            Write-Host $line
                        }
                    }
                    
                    $lastPosition = $stream.Position
                    $reader.Close()
                    $stream.Close()
                }
            } catch {
                # ファイルがロックされている場合は無視
            }
        }
    }
    
    # プロセスの終了を待機
    $process.WaitForExit()
    
    $endTime = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host ""
    Write-Host "終了時刻: $endTime" -ForegroundColor Green
    Write-Host "終了コード: $($process.ExitCode)" -ForegroundColor $(if ($process.ExitCode -eq 0) { "Green" } else { "Red" })
    
    "終了時刻: $endTime" | Out-File -FilePath $errorLogFile -Encoding UTF8 -Append
    "終了コード: $($process.ExitCode)" | Out-File -FilePath $errorLogFile -Encoding UTF8 -Append
    
    if ($process.ExitCode -ne 0) {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Red
        Write-Host "エラーが発生しました！" -ForegroundColor Red
        Write-Host "========================================" -ForegroundColor Red
        Write-Host ""
        Write-Host "終了コード: $($process.ExitCode)" -ForegroundColor Red
        Write-Host ""
        
        if (Test-Path $errorLogFile) {
            Write-Host "=== エラーログファイルの内容 ===" -ForegroundColor Yellow
            Get-Content $errorLogFile -Encoding UTF8
            Write-Host "=== エラーログファイルの内容（終了） ===" -ForegroundColor Yellow
        }
        Write-Host ""
        Read-Host "Enterキーを押して終了"
    } else {
        Write-Host ""
        Write-Host "サーバーが正常に終了しました。" -ForegroundColor Green
        Start-Sleep -Seconds 2
    }
    
} catch {
    Write-Host ""
    Write-Host "エラー: $_" -ForegroundColor Red
    "$_" | Out-File -FilePath $errorLogFile -Encoding UTF8 -Append
    $_.Exception | Out-File -FilePath $errorLogFile -Encoding UTF8 -Append
    Read-Host "Enterキーを押して終了"
    exit 1
}

