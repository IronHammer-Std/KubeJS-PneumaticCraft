# ============================================================
# 离线 codec 冒烟测试：不开游戏，先验证"组件 codec 写出的 JSON = PnC 要的形状"
#   用法： pwsh -File tools\smoke.ps1
#   说明：源码 tools\smoke\CodecSmokeTest.java 不进产物；报告落 build\smoke-report.txt（UTF-8）
# ============================================================
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

# JDK 21+ 定位（与 build.ps1 同一套候选顺序）
function Resolve-JdkBin {
	$candidates = New-Object System.Collections.Generic.List[string]
	if ($env:PNC_JAVAC) { $candidates.Add((Split-Path $env:PNC_JAVAC -Parent)) }
	if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME 'bin')) }
	foreach ($glob in @(
			'C:\Program Files\Java\jdk-21*\bin',
			'C:\Program Files\Eclipse Adoptium\jdk-21*\bin',
			'C:\Program Files\Microsoft\jdk-21*\bin',
			'C:\Program Files\Zulu\zulu-21*\bin',
			'C:\Program Files\BellSoft\LibericaJDK-21*\bin'
		)) {
		$hit = Get-ChildItem $glob -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
		if ($hit) { $candidates.Add($hit.FullName) }
	}
	foreach ($c in $candidates) {
		if (Test-Path (Join-Path $c 'javac.exe')) { return $c }
	}
	$cmd = Get-Command javac.exe -ErrorAction SilentlyContinue
	if ($cmd) { return (Split-Path $cmd.Source -Parent) }
	throw '找不到 JDK 21+（试过 $env:PNC_JAVAC、JAVA_HOME、常见安装位置、PATH）'
}
$jdkBin = Resolve-JdkBin
$javac = Join-Path $jdkBin 'javac.exe'
$java = Join-Path $jdkBin 'java.exe'
$verLine = (& $javac -version 2>&1 | Select-Object -Last 1 | Out-String).Trim()
$major = if ($verLine -match 'javac\s+(\d+)') { [int]$Matches[1] } else { 0 }
Write-Host ("   JDK: {0}  ({1})" -f $jdkBin, $verLine)
if ($major -ne 21) {
	Write-Host "   ! 警告：离线 Bootstrap 只在 JDK 21 上验证过（本机是 JDK $major）—— 结果可能不准，建议设 `$env:PNC_JAVAC 指向 JDK 21 的 javac"
}

$cp = ((Get-ChildItem "$root\libs\*.jar" | ForEach-Object { $_.FullName }) + "$root\build\classes") -join ';'
# 运行时还需要 MC/NeoForge 的一堆依赖（brigadier / guava / log4j …）：放在 libs-run\
$runCp = (Get-ChildItem "$root\libs-run\*.jar" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }) -join ';'
$out = "$root\build\smoke"
New-Item -ItemType Directory -Force -Path $out | Out-Null
Get-ChildItem $out -Recurse -File -ErrorAction SilentlyContinue | Remove-Item -Force

& $javac -encoding UTF-8 -nowarn -cp $cp -d $out "$root\tools\smoke\CodecSmokeTest.java"
if ($LASTEXITCODE -ne 0) { throw 'smoke 测试编译失败' }
# ⚠ 必须换 cwd：MC 的 log4j 配置会往"当前目录\logs\latest.log"写日志，
#   在实例目录下跑会覆盖实例的 latest.log（第一次踩过，已把旧日志从 logs\*.gz 里捞回来）。
$runDir = "$root\build\smoke-run"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Push-Location $runDir
try {
	& $java "-Dfile.encoding=UTF-8" -cp "$out;$cp;$runCp" dev.taao.kubejspnc.smoke.CodecSmokeTest "$root\build\smoke-report.txt" | Out-Null
	$code = $LASTEXITCODE
} finally {
	Pop-Location
}
# 报告是 Java 自己写的 UTF-8 文件，这里按 UTF-8 读回即可（控制台中文在这台机器上会乱码）
Get-Content "$root\build\smoke-report.txt" -Encoding UTF8 | ForEach-Object { $_ }
exit $code