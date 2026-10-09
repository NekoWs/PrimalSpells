# 录制故障诊断：只读报告/日志，保存失败证据；可独立验证，不操作进程。
function Get-CaptureFailure {
    param([Parameter(Mandatory)][string]$Workspace)
    $crashDir = Join-Path $Workspace 'run/capture/crash-reports'
    $crash = Get-ChildItem -LiteralPath $crashDir -Filter '*.txt' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($crash) { return @{ Reason='客户端生成崩溃报告'; CrashReport=$crash.FullName } }
    $logPath = Join-Path $Workspace 'run/capture/logs/latest.log'
    if (Test-Path -LiteralPath $logPath) {
        $fatal = Select-String -LiteralPath $logPath -Pattern '/FATAL\]|CaptureDriver.*(failed|失败)|\[Capture\] (scene .+|screenshot|write frame).*FAILED(?: at tick \d+)?:' | Select-Object -Last 1
        if ($fatal) { return @{ Reason=$fatal.Line; CrashReport=$null } }
    }
    return $null
}

function Write-CaptureFailure {
    param([Parameter(Mandatory)][string]$Workspace, [Parameter(Mandatory)][hashtable]$Failure)
    $output = Join-Path $Workspace 'build/capture'
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $logs = @('run/capture/logs/latest.log','build/capture/gradle.log','build/capture/gradle.stderr.log') |
        ForEach-Object { Join-Path $Workspace $_ } | Where-Object { Test-Path -LiteralPath $_ }
    $record = [ordered]@{ status='failed'; reason=$Failure.Reason; crashReport=$Failure.CrashReport; logs=@($logs); timeUtc=[DateTime]::UtcNow.ToString('o') }
    $record | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $output 'failure.json') -Encoding utf8
    Write-Host "[capture] 失败：$($Failure.Reason)" -ForegroundColor Red
    if ($Failure.CrashReport) {
        Write-Host "[capture] 崩溃报告：$($Failure.CrashReport)"
        Get-Content -LiteralPath $Failure.CrashReport -TotalCount 50 | ForEach-Object { Write-Host "    $_" }
    } else {
        foreach ($path in $logs) {
            Write-Host "[capture] 日志：$path"
            Get-Content -LiteralPath $path -Tail 45 | ForEach-Object { Write-Host "    $_" }
        }
    }
    Write-Host "[capture] 失败证据：$(Join-Path $output 'failure.json')；保留已录帧，不生成成功画廊。"
}
