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
    [switch]$NoVideo
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $root
Write-Host "[capture] 仓库根目录：$root"

# 1) 先过一遍完整构建（含 0 warning 口径）：录制跑的是刚构建出来的类
if (-not $SkipBuild) {
    Write-Host "[capture] ./gradlew build ..."
    & .\gradlew.bat build --console=plain
    if ($LASTEXITCODE -ne 0) { throw "[capture] 构建失败，先修构建再看画面" }
}

# 2) 清场：录制世界每次重建成固定种子，输出目录清空（避免新旧帧混在一起）
Remove-Item -Recurse -Force "run/capture" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "run/capture" | Out-Null
Remove-Item -Recurse -Force "build/capture/out" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "build/capture/out" | Out-Null

# 3) 真客户端录制（驱动会在录完后自己退出）
Write-Host "[capture] ./gradlew runCapture -Pcapture=$Scene ..."
& .\gradlew.bat runCapture --console=plain "-Pcapture=$Scene" 2>&1 |
    Tee-Object -FilePath "build/capture/gradle.log"
if ($LASTEXITCODE -ne 0) { throw "[capture] runCapture 失败，见 build/capture/gradle.log" }

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
