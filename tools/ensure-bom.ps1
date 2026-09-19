# ============================================================
# 给本工程所有 .ps1 补 UTF-8 BOM（Windows PowerShell 5.1 没有 BOM 会把中文注释按 ANSI 解码 ⇒ 语法错误）
#   用法： pwsh -File tools\ensure-bom.ps1       （可重复执行；已是 BOM 的跳过）
#   注意：用任何编辑器/工具改写 .ps1 之后都应再跑一次本脚本。
# ============================================================
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$utf8Bom = New-Object System.Text.UTF8Encoding($true)
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$fixed = 0

foreach ($f in (Get-ChildItem $root -Recurse -Filter *.ps1 -File)) {
	$bytes = [System.IO.File]::ReadAllBytes($f.FullName)
	$hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
	if ($hasBom) { continue }
	$text = [System.IO.File]::ReadAllText($f.FullName, $utf8NoBom)
	[System.IO.File]::WriteAllText($f.FullName, $text, $utf8Bom)
	$fixed++
	Write-Host ("  补 BOM: {0}" -f $f.FullName.Replace($root, '.'))
}
Write-Host ("== ensure-bom: 补齐 {0} 个文件 ==" -f $fixed)
