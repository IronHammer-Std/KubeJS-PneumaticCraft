# ============================================================
# KubeJS PneumaticCraft —— 一键构建（KubeJS 插件 + schema 数据）
#   用法： pwsh -File build.ps1                          （编译 + 打包 + 静态校验）
#          pwsh -File build.ps1 -Install -Instance <目录> （再复制进整合包 mods\，需游戏已关闭）
#          pwsh -File build.ps1 -Clean                   （先清 build\ 再全量构建）
#   产出： dist\kubejs_pneumaticcraft-<version>.jar
#
#   为什么不用 gradle：编译只需要"打补丁的 MC+NeoForge 合并 jar + kubejs + rhino + DFU + gson"
#   （都在 libs\ 里，怎么准备见 README「从零构建」）。于是构建 = javac 一次 + 手工打包，
#   无需联网、无需 gradle 守护进程。
# ============================================================
[CmdletBinding()]
param(
	[switch]$Install,
	[switch]$Clean,
	# 整合包（实例）目录；也可用环境变量 TAAO_INSTANCE。只有 -Install 才需要。
	[string]$Instance = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression   # PS 5.1：ZipArchiveMode 枚举在这个程序集里

$root    = $PSScriptRoot
$libDir  = Join-Path $root 'libs'
$resDir  = Join-Path $root 'src\main\resources'
$srcDir  = Join-Path $root 'src\main\java'
$outDir  = Join-Path $root 'build'
$clsDir  = Join-Path $outDir 'classes'
$stgDir  = Join-Path $outDir 'pack'
$distDir = Join-Path $root 'dist'

if ($Clean -and (Test-Path $outDir)) { Remove-Item $outDir -Recurse -Force }

# ---------- 0) 找 JDK 21 ----------
function Resolve-Javac {
	$candidates = New-Object System.Collections.Generic.List[string]
	if ($env:PNC_JAVAC) { $candidates.Add($env:PNC_JAVAC) }                       # 显式指定优先
	if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME 'bin\javac.exe')) }
	foreach ($glob in @(
			'C:\Program Files\Java\jdk-21*\bin\javac.exe',
			'C:\Program Files\Eclipse Adoptium\jdk-21*\bin\javac.exe',
			'C:\Program Files\Microsoft\jdk-21*\bin\javac.exe',
			'C:\Program Files\Zulu\zulu-21*\bin\javac.exe',
			'C:\Program Files\BellSoft\LibericaJDK-21*\bin\javac.exe'
		)) {
		$hit = Get-ChildItem $glob -ErrorAction SilentlyContinue | Select-Object -First 1
		if ($hit) { $candidates.Add($hit.FullName) }
	}
	foreach ($c in $candidates) {
		if (Test-Path $c) { return $c }
	}
	$cmd = Get-Command javac.exe -ErrorAction SilentlyContinue
	if ($cmd) { return $cmd.Source }
	throw '找不到 javac（试过 $env:PNC_JAVAC、JAVA_HOME、常见 JDK 21 安装位置、PATH）—— 需要 JDK 21+'
}

$javac = Resolve-Javac
$verLine = (& $javac -version 2>&1 | Select-Object -Last 1 | Out-String).Trim()
if ($verLine -notmatch 'javac\s+(\d+)') { throw "无法识别 javac 版本：$verLine" }
$javaMajor = [int]$Matches[1]
$releaseArgs = @()
if ($javaMajor -lt 21) { throw "需要 JDK 21+，当前：$verLine" }
if ($javaMajor -ne 21) {
	$releaseArgs = @('--release', '21')
	Write-Host "   ! 检测到 JDK $javaMajor（推荐直接用 JDK 21）=> 用 --release 21 产出 Java 21 字节码"
}
Write-Host "== KubeJS PneumaticCraft =="
Write-Host "   javac: $javac  ($verLine)"

# ---------- 1) 编译 ----------
$libs = @(Get-ChildItem "$libDir\*.jar" | Sort-Object Name)
if ($libs.Count -eq 0) { throw "libs\ 里没有 jar（需要 neoforge-*-merged.jar / kubejs-*.jar / rhino-*.jar / datafixerupper / gson）" }
$cp = ($libs | ForEach-Object { $_.FullName }) -join ';'
$sources = @(Get-ChildItem -Recurse "$srcDir" -Filter *.java | Sort-Object FullName | ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) { throw "src\main\java 下没有源码" }

New-Item -ItemType Directory -Force -Path $clsDir | Out-Null
Get-ChildItem -Recurse $clsDir -File -ErrorAction SilentlyContinue | Remove-Item -Force
Write-Host ("   [1/4] javac {0} 个源文件, classpath {1} 个 jar" -f $sources.Count, $libs.Count)
& $javac @releaseArgs -encoding UTF-8 -nowarn -cp $cp -d $clsDir @sources
if ($LASTEXITCODE -ne 0) { throw "javac 失败（exit $LASTEXITCODE）" }
$classFiles = @(Get-ChildItem -Recurse $clsDir -Filter *.class)
Write-Host ("         编译通过：{0} 个 class" -f $classFiles.Count)

# ---------- 2) 组装 staging（resources + classes） ----------
if (Test-Path $stgDir) { Remove-Item $stgDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stgDir | Out-Null
Copy-Item "$resDir\*" $stgDir -Recurse -Force
foreach ($f in $classFiles) {
	$rel = $f.FullName.Substring($clsDir.Length + 1)
	$target = Join-Path $stgDir $rel
	New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
	Copy-Item $f.FullName $target -Force
}

# 版本号从 mods.toml 读，避免两处不一致
$toml = Get-Content (Join-Path $resDir 'META-INF\neoforge.mods.toml') -Encoding UTF8 -Raw
if ($toml -notmatch '(?m)^\s*version\s*=\s*"([^"]+)"') { throw 'mods.toml 里读不到 version' }
$version = $Matches[1]
$jar = Join-Path $distDir "kubejs_pneumaticcraft-$version.jar"

# ---------- 3) 打包（可复现） ----------
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
if (Test-Path $jar) { Remove-Item $jar -Force }
# ⚠ 必须手工写条目：.NET Framework 的 CreateFromDirectory 会把路径写成反斜杠，
#   而 jar 内路径必须是正斜杠（否则 Java 侧看不到文件）
$zip = [System.IO.Compression.ZipFile]::Open($jar, [System.IO.Compression.ZipArchiveMode]::Create)
try {
	$base = (Resolve-Path $stgDir).Path.TrimEnd('\')
	# 固定条目时间戳 ⇒ 可复现构建（否则每次重建 sha256 都不同）
	$fixedTime = [System.DateTimeOffset]::new([System.DateTime]::new(2026, 1, 1, 0, 0, 0, [System.DateTimeKind]::Unspecified))
	foreach ($f in (Get-ChildItem $stgDir -Recurse -File | Sort-Object FullName)) {
		$rel = $f.FullName.Substring($base.Length + 1).Replace('\', '/')
		$entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
		$entry.LastWriteTime = $fixedTime
		$es = $entry.Open()
		$bytes = [System.IO.File]::ReadAllBytes($f.FullName)
		$es.Write($bytes, 0, $bytes.Length)
		$es.Close()
	}
} finally { $zip.Dispose() }

# ---------- 4) 校验产物 ----------
Write-Host '   [2/4] 打包完成，校验 jar 内容'
$z = [System.IO.Compression.ZipFile]::OpenRead($jar)
try {
	$names = @($z.Entries | Where-Object { $_.FullName -notmatch '/$' } | ForEach-Object { $_.FullName })
	$json  = @($names | Where-Object { $_ -match 'recipe_schema/.*\.json$' })
	$cls   = @($names | Where-Object { $_ -match '\.class$' })
	Write-Host ("         产物: {0}  ({1:N0} B)" -f (Split-Path $jar -Leaf), (Get-Item $jar).Length)
	Write-Host ("         条目: {0} 个（schema {1} / class {2}）" -f $names.Count, $json.Count, $cls.Count)
	foreach ($required in @('META-INF/neoforge.mods.toml', 'kubejs.plugins.txt', 'META-INF/MANIFEST.MF')) {
		if ($names -notcontains $required) { throw "产物里缺少 $required" }
	}
	if ($cls.Count -eq 0) { throw '产物里没有 class（插件不会被加载）' }
	if (-not ($cls | Where-Object { $_ -match 'KubeJSPneumaticCraftPlugin\.class$' })) { throw '产物里缺少插件主类' }

	# 图标：mods.toml 里声明的 logoFile 必须真的在 jar 里，且是合法 PNG（否则模组列表里是一片空白/报错）
	$tomlEntry = $z.GetEntry('META-INF/neoforge.mods.toml')
	$srT = New-Object System.IO.StreamReader($tomlEntry.Open()); $tomlTxt = $srT.ReadToEnd(); $srT.Close()
	$logoMatch = [regex]::Match($tomlTxt, '(?m)^\s*logoFile\s*=\s*"([^"]+)"')
	if (-not $logoMatch.Success) {
		Write-Host '         （mods.toml 未声明 logoFile，跳过图标校验）'
	} else {
		$logo = $logoMatch.Groups[1].Value
		$logoEntry = $z.GetEntry($logo)
		if (-not $logoEntry) { throw "mods.toml 声明了 logoFile=`"$logo`"，但 jar 里没有这个条目" }
		$ls = $logoEntry.Open()
		$hdr = New-Object byte[] 8
		$null = $ls.Read($hdr, 0, 8)
		$ls.Close()
		$isPng = ($hdr[0] -eq 0x89 -and $hdr[1] -eq 0x50 -and $hdr[2] -eq 0x4E -and $hdr[3] -eq 0x47)
		if (-not $isPng) { throw "logoFile `"$logo`" 不是 PNG（魔数不对）" }
		Write-Host ("         图标: {0} ({1:N0} B, PNG 魔数 OK)" -f $logo, $logoEntry.Length)
	}

	# 逐份校验 jar 内 JSON 能被解析，并做 schema→组件 id 的静态交叉检查
	$declared = @(Select-String -Path (Join-Path $srcDir 'dev\taao\kubejspnc\PncComponents.java') -Pattern 'id\("([a-z_]+)"\)' -AllMatches |
		ForEach-Object { $_.Matches } | ForEach-Object { $_.Groups[1].Value })
	Write-Host ("         已注册组件 id: {0}" -f (($declared | Sort-Object) -join ', '))
	# KubeJS 内置组件 id（已逐个核对过 jar 内注册代码）+ 本件的函数类型 id
	$builtin = @('int', 'long', 'float', 'double', 'non_negative_int', 'positive_int', 'non_negative_long',
		'positive_long', 'non_negative_float', 'positive_float', 'non_negative_double', 'positive_double',
		'string', 'optional_string', 'id', 'boolean', 'ticks', 'item_stack', 'optional_item_stack',
		'filtered_item_stack', 'fluid_stack', 'optional_fluid_stack', 'ingredient', 'optional_ingredient',
		'flat_sized_ingredient', 'sized_ingredient', 'optional_sized_ingredient', 'optional_flat_sized_ingredient',
		'flat_sized_fluid_ingredient', 'nested_sized_fluid_ingredient', 'list', 'map', 'either', 'custom_object',
		'block', 'optional_block', 'ignore', 'pnc_set_field')
	$known = $declared + $builtin
	$usedMine = New-Object System.Collections.Generic.HashSet[string]
	$usedBuiltin = New-Object System.Collections.Generic.HashSet[string]
	$unknown = New-Object System.Collections.Generic.HashSet[string]
	foreach ($e in ($z.Entries | Where-Object { $_.FullName -match 'recipe_schema/.*\.json$' })) {
		$sr = New-Object System.IO.StreamReader($e.Open()); $txt = $sr.ReadToEnd(); $sr.Close()
		$null = $txt | ConvertFrom-Json
		# 组件引用出现在 type / component / left / right 四个位置
		foreach ($m in [regex]::Matches($txt, '"(?:type|component|left|right)"\s*:\s*"([^"]+)"')) {
			$v = $m.Groups[1].Value
			if ($v -match '^[a-z_]+$') {
				if ($builtin -contains $v) { $null = $usedBuiltin.Add($v) } else { $null = $unknown.Add($v) }
			} elseif ($v -match '^pneumaticcraft:([a-z_]+)$') {
				if ($declared -contains $Matches[1]) { $null = $usedMine.Add($Matches[1]) } else { $null = $unknown.Add($v) }
			} else {
				$null = $unknown.Add($v)
			}
		}
	}
	if ($unknown.Count -gt 0) { throw "schema 引用了未知组件 id: $($unknown -join ', ')" }
	Write-Host ("         schema 引用组件: {0} 个（本件 {1} + 内置 {2}，全部已核对）" -f ($usedMine.Count + $usedBuiltin.Count), $usedMine.Count, $usedBuiltin.Count)
	Write-Host ("         本件被引用: {0}" -f (($usedMine | Sort-Object) -join ', '))
	$unused = @($declared | Where-Object { -not $usedMine.Contains($_) })
	if ($unused.Count -gt 0) { Write-Host ("         已注册但本批 schema 未用: {0}" -f (($unused | Sort-Object) -join ', ')) }

	# 第三层补充：禁用 KubeJS 内置的 sized-ingredient 组件
	#   实测（2026-09-20）：SizedIngredientWrapper.wrapResult() 收到 JS 对象时走
	#   IngredientWrapper.wrapResult(...).map(IngredientKJS::kjs$asStack)，而 kjs$asStack()
	#   把数量硬编码为 1 ⇒ {count:4, tag:...} 在位置参数/键函数路径下被静默压成 1 个
	#   （对象形态走 codec 不受影响，于是同一包里两种写法消耗量还不一样）。
	#   本件改用自注册的 pneumaticcraft:item_ingredient；这条检查防止以后写回去。
	$banned = @('flat_sized_ingredient', 'sized_ingredient', 'optional_flat_sized_ingredient', 'optional_sized_ingredient')
	$bannedHits = New-Object System.Collections.Generic.List[string]
	foreach ($e in ($z.Entries | Where-Object { $_.FullName -match 'recipe_schema/.*\.json$' })) {
		$sr = New-Object System.IO.StreamReader($e.Open()); $txt = $sr.ReadToEnd(); $sr.Close()
		foreach ($b in $banned) {
			if ($txt.Contains('"' + $b + '"')) { $bannedHits.Add((Split-Path $e.FullName -Leaf) + ' : ' + $b) }
		}
	}
	if ($bannedHits.Count -gt 0) {
		$bannedHits | ForEach-Object { Write-Host ("         X " + $_) }
		throw 'schema 用了 KubeJS 内置的 sized-ingredient 组件（会静默吞掉 count）—— 请改用 pneumaticcraft:item_ingredient'
	}
	Write-Host '         未使用 KubeJS 内置 sized-ingredient 组件（count 不会被吞）'

	# 第四层：schema 的键名必须是 PnC 真的字段名、且 PnC 的必填字段都已声明
	#   （首轮实机踩坑后加的：thermo_plant 把 inputs 写成 input ⇒ KubeJS 写出 "input" ⇒ PnC 报 "No key inputs"）
	$pncKeys = @{
		'pressure_chamber'   = @('inputs', 'pressure', 'results')
		'explosion_crafting' = @('input', 'loss_rate', 'results')
		'assembly_drill'     = @('input', 'program', 'result')
		'assembly_laser'     = @('input', 'program', 'result')
		'fluid_mixer'        = @('input1', 'input2', 'fluid_output', 'item_output', 'pressure', 'time')
		'refinery'           = @('input', 'outputs', 'temperature')
		'thermo_plant'       = @('inputs', 'outputs', 'temperature', 'pressure', 'speed', 'air_use_multiplier', 'exothermic')
		'heat_frame_cooling' = @('input', 'temperature', 'output', 'bonusMultiplier', 'bonusLimit')
		'amadron'            = @('offer_id', 'input', 'output', 'static', 'villager_trade', 'level', 'maxStock', 'inStock')
		'fuel_quality'       = @('fluid', 'air_per_bucket', 'burn_rate')
		'heat_properties'    = @('block', 'transforms', 'heatCapacity', 'temperature', 'thermalResistance', 'predicates', 'description')
	}
	$pncRequired = @{
		'pressure_chamber'   = @('inputs', 'pressure', 'results')
		'explosion_crafting' = @('input', 'loss_rate', 'results')
		'assembly_drill'     = @('input', 'program', 'result')
		'assembly_laser'     = @('input', 'program', 'result')
		'fluid_mixer'        = @('input1', 'input2', 'fluid_output', 'item_output', 'pressure', 'time')
		'refinery'           = @('input', 'outputs')
		'thermo_plant'       = @('inputs', 'outputs')
		'heat_frame_cooling' = @('input', 'temperature', 'output')
		'amadron'            = @('offer_id', 'input', 'output')
		'fuel_quality'       = @('fluid', 'air_per_bucket')
		'heat_properties'    = @('block', 'temperature')
	}
	$keyProblems = New-Object System.Collections.Generic.List[string]
	foreach ($e in ($z.Entries | Where-Object { $_.FullName -match 'recipe_schema/[^/]+\.json$' })) {
		$short = Split-Path $e.FullName -Leaf
		$t = $short -replace '\.json$', ''
		$sr = New-Object System.IO.StreamReader($e.Open()); $txt = $sr.ReadToEnd(); $sr.Close()
		$obj = $txt | ConvertFrom-Json
		$names = @($obj.keys | ForEach-Object { $_.name })
		if (-not $pncKeys.ContainsKey($t)) { $keyProblems.Add("$short : 未登记 PnC 字段白名单（新增类型要同时补 build.ps1 的两张表）"); continue }
		foreach ($n in $names) {
			if ($pncKeys[$t] -notcontains $n) { $keyProblems.Add("$short : 键 '$n' 不是 PnC 字段名（合法：$($pncKeys[$t] -join ', ')）") }
		}
		foreach ($req in $pncRequired[$t]) {
			if ($names -notcontains $req) { $keyProblems.Add("$short : 缺少 PnC 必填字段 '$req'（不声明 ⇒ 写出的 JSON 里没有它 ⇒ PnC 解析失败）") }
		}
	}
	if ($keyProblems.Count -gt 0) {
		$keyProblems | ForEach-Object { Write-Host ("         X " + $_) }
		throw 'schema 键名/必填字段检查未通过'
	}
	Write-Host ("         schema 键名 = PnC 字段名、必填字段齐全（{0} 类逐类核对）" -f $pncKeys.Count)

	# 第五层：字节码必须是 Java 21（major 65）—— MC 1.21.1 / NeoForge 21.1 只跑 Java 21，
	# 用高版本 JDK 编译出 major 68/69 的 jar 会在启动时直接崩，所以在打包阶段就拦下来
	$probeClass = $cls | Where-Object { $_ -match 'KubeJSPneumaticCraftPlugin\.class$' } | Select-Object -First 1
	$pc = $z.GetEntry($probeClass).Open()
	$hdr = New-Object byte[] 8
	$null = $pc.Read($hdr, 0, 8)
	$pc.Close()
	$major = [int]$hdr[6] * 256 + [int]$hdr[7]
	if ($major -ne 65) { throw "class 文件版本是 major $major（Java $($major - 44)），必须是 65（Java 21）—— 请用 JDK 21，或让脚本加 --release 21" }
	Write-Host '         class 版本 = major 65（Java 21）OK'
	Write-Host '   [3/4] ✔ jar 内 JSON 全部合法、插件与元数据就位'
	Write-Host ("         schema 清单: {0}" -f (($json | ForEach-Object { Split-Path $_ -Leaf }) -join ', '))
} finally { $z.Dispose() }
Write-Host ("   sha256 = {0}" -f (Get-FileHash $jar -Algorithm SHA256).Hash.ToLower())

# ---------- 5) 可选：装进实例 ----------
if ($Install) {
	if (-not $Instance) { $Instance = $env:TAAO_INSTANCE }
	if (-not $Instance) { throw '请用 -Instance <整合包目录> 指定装机目标（或设环境变量 TAAO_INSTANCE）' }
	if (-not (Test-Path (Join-Path $Instance 'mods'))) { throw "不是有效的整合包目录（找不到 mods\）：$Instance" }
	$log = Join-Path $Instance 'logs\latest.log'
	$locked = $false
	if (Test-Path $log) {
		try { $fs = [System.IO.File]::Open($log, 'Open', 'Read', 'None'); $fs.Close() } catch { $locked = $true }
	}
	if ($locked) { throw 'latest.log 仍被占用 ⇒ 游戏在运行，先关闭再 -Install' }
	$mods = Join-Path $Instance 'mods'
	Get-ChildItem "$mods\kubejs_pneumaticcraft-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
	Copy-Item $jar $mods -Force
	Write-Host ("   [4/4] ✔ 已装入 {0}" -f (Join-Path $mods (Split-Path $jar -Leaf)))
	$loose = Join-Path $Instance 'kubejs\data\pneumaticcraft'
	if (Test-Path $loose) {
		Write-Host "   ⚠ 实例里还有松散 schema：$loose（会与 jar 内 schema 冲突，应删掉）"
	}
} else {
	Write-Host '   [4/4] （未安装；加 -Install -Instance <目录> 可复制进 mods\，需游戏关闭）'
}
