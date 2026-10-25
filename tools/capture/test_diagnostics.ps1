# 无需启动游戏的收尾阶段/失败检测回归，不调用进程诊断或终止任何进程。
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'diagnostics.ps1')
$verifyRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../build/verify'))
$fixture = Join-Path $verifyRoot ('capture-diagnostics-' + [Guid]::NewGuid().ToString('N'))
if (-not $fixture.StartsWith($verifyRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Fixture path outside verification root' }
$logs = Join-Path $fixture 'run/capture/logs'
New-Item -ItemType Directory -Path $logs -Force | Out-Null
$log = Join-Path $logs 'latest.log'
try {
    if ($null -ne (Get-CaptureFinishPhase $fixture)) { throw 'Fresh capture must have no finish phase' }
    Set-Content -LiteralPath $log -Value '[Capture] ALL SCENES DONE: scenes=2' -Encoding utf8
    if ((Get-CaptureFinishPhase $fixture) -ne '等待PNG回读与落盘') { throw 'Missing PNG drain phase' }
    Add-Content -LiteralPath $log -Value '[Capture] stopping client (all frames flushed): written=440 failed=0'
    if ((Get-CaptureFinishPhase $fixture) -ne '等待游戏保存并退出') { throw 'Missing normal save/exit phase' }
    if ($null -ne (Get-CaptureFailure $fixture)) { throw 'Normal completion must not fail' }
    Add-Content -LiteralPath $log -Value '[Capture] scene capture flush FAILED: PNG数量不一致'
    if ($null -eq (Get-CaptureFailure $fixture)) { throw 'Flush failure was ignored' }
    Set-Content -LiteralPath $log -Value '[Capture] write frame 2 FAILED: disk full'
    if ($null -eq (Get-CaptureFailure $fixture)) { throw 'Writer failure was ignored' }
    Set-Content -LiteralPath $log -Value 'Client disconnected with reason: Invalid move player packet received'
    if ($null -eq (Get-CaptureFailure $fixture)) { throw 'Early disconnect was ignored' }
    Set-Content -LiteralPath $log -Value 'Capturer lost connection: Disconnected'
    if ($null -eq (Get-CaptureFailure $fixture)) { throw 'Server-side disconnect was ignored' }
    Add-Content -LiteralPath $log -Value '[Capture] ALL SCENES DONE: scenes=1'
    if ($null -ne (Get-CaptureFailure $fixture)) { throw 'Normal completed shutdown must not fail' }
    Write-Host 'capture diagnostics: 9 checks passed'
} finally {
    if ($fixture.StartsWith($verifyRoot + [IO.Path]::DirectorySeparatorChar)) { Remove-Item -LiteralPath $fixture -Recurse -Force }
}
