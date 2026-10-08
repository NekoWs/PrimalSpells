#Requires -RunAsAdministrator
#Requires -Version 5.1
param([string]$RestoreFrom, [switch]$RestartServices)
$ErrorActionPreference = 'Stop'
$repairRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../build/verify/oshi'))
$backupDir = Join-Path $repairRoot ('backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
Start-Transcript -Path (Join-Path $backupDir 'repair.log')
try {
    $systemDir = Join-Path $env:SystemRoot 'System32'
    $regExe = Join-Path $systemDir 'reg.exe'
    $lodctrExe = Join-Path $systemDir 'lodctr.exe'
    Copy-Item -LiteralPath (Join-Path $systemDir 'PerfStringBackup.INI') -Destination $backupDir
    & $regExe export 'HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Perflib' (Join-Path $backupDir 'perflib.reg') /y
    if ($LASTEXITCODE -ne 0) { throw 'Failed to back up Perflib.' }
    & $regExe export 'HKLM\SYSTEM\CurrentControlSet\Services\PerfOS\Performance' (Join-Path $backupDir 'perfos.reg') /y
    if ($LASTEXITCODE -ne 0) { throw 'Failed to back up PerfOS.' }
    $perfOs = Get-Item 'HKLM:\SYSTEM\CurrentControlSet\Services\PerfOS\Performance'
    $disableValue = 'Disable Performance Counters'
    if ($perfOs.GetValueNames() -contains $disableValue -and
        $perfOs.GetValueKind($disableValue) -ne 'DWord') {
        # Only normalize a numeric zero: preserve intentionally disabled counters.
        if ([string]$perfOs.GetValue($disableValue) -ne '0') {
            throw 'Unexpected PerfOS disable value; refusing to change its meaning.'
        }
        New-ItemProperty -LiteralPath $perfOs.PSPath -Name $disableValue -PropertyType DWord -Value 0 -Force | Out-Null
    }
    # Microsoft-supported rebuild; never disable counters or suppress OSHI logging.
    if ($RestoreFrom) {
        $restoreFile = (Resolve-Path -LiteralPath $RestoreFrom).Path
        & $lodctrExe "/R:$restoreFile"
    } else {
        & $lodctrExe /R
    }
    if ($LASTEXITCODE -ne 0) { throw "lodctr restore failed: $LASTEXITCODE" }
    if ([Environment]::Is64BitOperatingSystem) {
        $wowDir = Join-Path $env:SystemRoot 'SysWOW64'
        $wowBackup = Join-Path $wowDir 'PerfStringBackup.INI'
        if (Test-Path -LiteralPath $wowBackup) {
            Copy-Item -LiteralPath $wowBackup -Destination (Join-Path $backupDir 'PerfStringBackup-32.INI')
        }
        if ($RestoreFrom) {
            & (Join-Path $wowDir 'lodctr.exe') "/R:$restoreFile"
        } else {
            & (Join-Path $wowDir 'lodctr.exe') /R
        }
        if ($LASTEXITCODE -ne 0) { throw "32-bit counter restore failed: $LASTEXITCODE" }
    }
    & (Join-Path $systemDir 'wbem/winmgmt.exe') /resyncperf
    if ($LASTEXITCODE -ne 0) { throw "WMI counter resync failed: $LASTEXITCODE" }
    if ($RestartServices) {
        Restart-Service -Name pla -ErrorAction Stop
        $runningDependents = (Get-Service winmgmt).DependentServices | Where-Object Status -eq Running
        $runningDependents.Name | Set-Content (Join-Path $backupDir 'running-wmi-dependents.txt')
        try {
            Restart-Service -Name winmgmt -Force -ErrorAction Stop
        } finally {
            foreach ($dependent in $runningDependents) { Start-Service -Name $dependent.Name }
        }
    }
    # Perflib language keys are virtual; PowerShell's registry provider cannot read them.
    & $regExe query 'HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\Perflib\009' /v Counter /reg:64 | Out-File (Join-Path $backupDir 'english-counters.txt')
    if ($LASTEXITCODE -ne 0) { throw 'English counter names are still unavailable.' }
    'OK' | Set-Content -Path (Join-Path $backupDir 'result.txt')
} catch {
    $_ | Out-String | Set-Content -Path (Join-Path $backupDir 'result.txt')
    throw
} finally {
    Stop-Transcript
}
