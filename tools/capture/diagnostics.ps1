# 录制故障诊断：保存当前报告/日志；必要时仅对本次客户端执行有界的只读线程诊断。
function Get-CaptureFinishPhase {
    param([Parameter(Mandatory)][string]$Workspace)
    $path = Join-Path $Workspace 'run/capture/logs/latest.log'
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    if (Select-String -LiteralPath $path -Quiet -Pattern '\[Capture\] stopping client \(all frames flushed\)') { return '等待游戏保存并退出' }
    if (Select-String -LiteralPath $path -Quiet -Pattern '\[Capture\] ALL SCENES DONE:') { return '等待PNG回读与落盘' }
    return $null
}

# 只诊断本次新日志声明、且启动时间匹配的Java进程；jcmd自身也有5秒上限。
function Save-CaptureThreadDump {
    param([Parameter(Mandatory)][string]$Workspace, [Parameter(Mandatory)][DateTime]$Started)
    try {
        $line = Select-String -LiteralPath (Join-Path $Workspace 'run/capture/logs/latest.log') -Pattern '\[Capture\] client pid=(\d+)' | Select-Object -First 1
        if (-not $line) { return }
        $clientId = [int]$line.Matches[0].Groups[1].Value
        $client = Get-Process -Id $clientId -ErrorAction Stop
        if ($client.ProcessName -ne 'java' -or $client.StartTime.ToUniversalTime() -lt $Started.AddSeconds(-2)) { return }
        $jcmd = Join-Path (Split-Path $client.Path) 'jcmd.exe'
        if (-not (Test-Path -LiteralPath $jcmd)) { return }
        $probe = Start-Process -FilePath $jcmd -ArgumentList @($clientId,'Thread.print') -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $Workspace 'build/capture/threads.txt') `
            -RedirectStandardError (Join-Path $Workspace 'build/capture/threads.stderr.txt')
        if (-not $probe.WaitForExit(5000)) { $probe.Kill() }
    } catch { Write-Host "[capture] 线程诊断未完成：$($_.Exception.Message)" }
}

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
    $logNames = if ($Failure.ContainsKey('Logs')) { $Failure.Logs } else { @('run/capture/logs/latest.log','build/capture/gradle.log','build/capture/gradle.stderr.log') }
    $logs = $logNames |
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
