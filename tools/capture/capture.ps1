<#
.SYNOPSIS
  实机录制流水线：自动开真客户端 → 建超平坦世界 → 布景 → 走真实施法路径 → 逐 tick 抓帧 → 合成视频/动图并判定。

.DESCRIPTION
  一条命令拿到「游戏里实际长什么样」：

      pwsh tools/capture/capture.ps1                  # 三个场景全录
      pwsh tools/capture/capture.ps1 -Scene black_hole # 只录一个（逗号分隔可多个）
      pwsh tools/capture/capture.ps1 -SkipBuild        # 跳过 gradlew build
      pwsh tools/capture/capture.ps1 -NoVideo          # 只判定 + 拼图，不调 ffmpeg

  产物（`build/capture/out`，`build/` 不入库）：
      index.html                画廊：三个场景并排（视频 + 动图 + 拼图 + 判定）
      report.txt                判定结果（PASS/FAIL）
      <scene>/<scene>.mp4       H.264 20fps
      <scene>/<scene>.gif       640 宽动图
      <scene>/contact.png       12 帧拼图
      <scene>/frames/           逐 tick PNG（每 tick 一帧）

  驱动代码：src/main/kotlin/work/nekow/primalspells/client/capture/（只在开发环境 + 系统属性下激活）
  原理与判定口径：docs/build-and-run.md 第 9 节
#>
[CmdletBinding()]
param(
    [string]$Scene = "all",
    [switch]$SkipBuild,
    [switch]$NoVideo,
    # 客户端录制的最长等待秒数：超过就杀掉游戏进程并以失败退出。
    # 为什么必须有：客户端一旦崩溃/卡死就不会自己退出，流水线会一直挂着（第 29 轮实测）。
    [int]$TimeoutSec = 300
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "diagnostics.ps1")
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $root
Write-Host "[capture] 仓库根目录：$root"

# 1) 先过一遍完整构建（含 0 warning 口径）：录制跑的是刚构建出来的类
if (-not $SkipBuild) {
    Write-Host "[capture] ./gradlew build ..."
    & .\gradlew.bat build --console=plain
    if ($LASTEXITCODE -ne 0) { Write-CaptureFailure $root @{ Reason="构建失败（退出码$LASTEXITCODE）"; CrashReport=$null }; exit 1 }
}

# 2) 清场：录制世界每次重建成固定种子，输出目录清空（避免新旧帧混在一起）
foreach ($relative in @('run/capture','build/capture/out')) {
    $target = [IO.Path]::GetFullPath((Join-Path $root $relative))
    if (-not $target.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw "清理路径越界：$target" }
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
    New-Item -ItemType Directory -Path $target -Force | Out-Null
}
Remove-Item -LiteralPath (Join-Path $root 'build/capture/failure.json') -ErrorAction SilentlyContinue

# 3) 真客户端录制（驱动会在录完后自己退出）
#    ⚠️ 超时护栏：客户端崩溃/卡死时不会自己退出，必须由这里兜底杀掉（否则流水线会一直挂着）。
Write-Host "[capture] ./gradlew runCapture -Pcapture=$Scene ...（超时 $TimeoutSec 秒）"
$gradle = Start-Process -FilePath ".\gradlew.bat" `
    -ArgumentList @("runCapture", "--console=plain", "-Pcapture=$Scene") `
    -NoNewWindow -PassThru -RedirectStandardOutput "build/capture/gradle.log" -RedirectStandardError "build/capture/gradle.stderr.log"
$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSec)
while (-not $gradle.WaitForExit(1000)) {
    $failure = Get-CaptureFailure $root
    if (-not $failure -and [DateTime]::UtcNow -ge $deadline) { $failure = @{ Reason="录制超过$TimeoutSec 秒"; CrashReport=$null } }
    if ($failure) {
        Write-CaptureFailure $root $failure
        # 只结束本次启动的Gradle及其子进程，不扫描/终止其它MC实例。
        try { $gradle.Kill($true) } catch { Write-Host "[capture] 启动器已结束：$($_.Exception.Message)" }
        exit 1
    }
}
$gradle.Refresh()
$failure = Get-CaptureFailure $root
if (-not $failure -and $gradle.ExitCode -ne 0) { $failure = @{ Reason="runCapture退出码$($gradle.ExitCode)"; CrashReport=$null } }
if ($failure) { Write-CaptureFailure $root $failure; exit 1 }

# 4) 客户端日志（装配脚本用它查异常/ERROR）
$log = "run/capture/logs/latest.log"
if (Test-Path $log) { Copy-Item $log "build/capture/capture.log" -Force }

# 5) 合成 + 判定
$python = @("python", "python3") |
    Where-Object { Get-Command $_ -ErrorAction SilentlyContinue } |
    Select-Object -First 1
if (-not $python) { throw "[capture] 找不到 python（装配脚本需要 numpy + pillow）" }

$pyArgs = @("tools/capture/assemble.py")
if ($NoVideo) { $pyArgs += "--no-video" }
& $python @pyArgs
$code = $LASTEXITCODE
Write-Host ""
Write-Host "[capture] 画廊：$root\build\capture\out\index.html"
exit $code
