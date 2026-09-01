# Compile TradeRoutePlanner with the game JRE + Eclipse Compiler (no full JDK required),
# then write dist/TradeRoutePlanner-<version>.zip (overwrites same name).
# Usage: powershell -File compile.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ss = (Resolve-Path (Join-Path $root "..\..")).Path
$core = Join-Path $ss "starsector-core"
$java = Join-Path $ss "jre\bin\java.exe"
$ecj = Join-Path $root ".tools\ecj.jar"
$out = Join-Path $root "build\classes"
$jarsDir = Join-Path $root "jars"
$jar = Join-Path $jarsDir "TradeRoutePlanner.jar"

if (-not (Test-Path $java)) { throw "Game JRE not found: $java" }
if (-not (Test-Path $ecj)) { throw "Missing $ecj — download org.eclipse.jdt:ecj and place it there." }

if (Test-Path (Join-Path $root "build")) { Remove-Item (Join-Path $root "build") -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null
New-Item -ItemType Directory -Force -Path $jarsDir | Out-Null

$cpJars = @(Get-ChildItem $core -Filter "*.jar" | ForEach-Object { $_.FullName })
$luna = Join-Path $ss "mods\Lunalib\jars\LunaLib.jar"
if (Test-Path $luna) { $cpJars += $luna }
$cp = $cpJars -join ";"

$sources = Get-ChildItem -Path (Join-Path $root "src") -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
$listFile = Join-Path $root "build\sources.txt"
$sources | Set-Content -Path $listFile -Encoding ASCII

& $java -jar $ecj -17 -encoding UTF-8 -cp $cp -d $out "@$listFile"
if ($LASTEXITCODE -ne 0) { throw "ECJ compile failed: $LASTEXITCODE" }

$meta = Join-Path $out "META-INF"
New-Item -ItemType Directory -Force -Path $meta | Out-Null
"Manifest-Version: 1.0`r`nCreated-By: TradeRoutePlanner`r`n" | Set-Content -Path (Join-Path $meta "MANIFEST.MF") -Encoding ASCII

if (Test-Path $jar) {
    try {
        Remove-Item $jar -Force -ErrorAction Stop
    } catch {
        $jar = Join-Path $jarsDir "TradeRoutePlanner.jar.new"
        Write-Host "Game has the jar locked; writing $jar instead"
    }
}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$fs = [System.IO.File]::Open($jar, [System.IO.FileMode]::Create)
$zip = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Create, $false)
Get-ChildItem $out -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($out.Length).TrimStart('\', '/').Replace('\', '/')
    $entry = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
    $es = $entry.Open()
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    $es.Write($bytes, 0, $bytes.Length)
    $es.Close()
}
$zip.Dispose()
$fs.Dispose()
Write-Host "Wrote $jar ($((Get-Item $jar).Length) bytes)"

$modInfo = Get-Content -Raw -Encoding UTF8 (Join-Path $root "mod_info.json") | ConvertFrom-Json
$version = [string]$modInfo.version
if ([string]::IsNullOrWhiteSpace($version)) { throw "mod_info.json has no version" }

$dist = Join-Path $root "dist"
$packRoot = Join-Path $dist "pack"
$stage = Join-Path $packRoot "TradeRoutePlanner"
$playerZip = Join-Path $dist ("TradeRoutePlanner-" + $version + ".zip")

if (Test-Path $packRoot) { Remove-Item $packRoot -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $stage "jars") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $stage "data\config") | Out-Null
Copy-Item (Join-Path $root "mod_info.json") $stage
Copy-Item (Join-Path $root "LICENSE") $stage
Copy-Item (Join-Path $root "README.md") $stage
Copy-Item $jar (Join-Path $stage "jars\TradeRoutePlanner.jar")
Copy-Item (Join-Path $root "data\config\settings.json") (Join-Path $stage "data\config")
Copy-Item (Join-Path $root "data\config\LunaSettings.csv") (Join-Path $stage "data\config")
Copy-Item (Join-Path $root "data\config\LunaSettingsConfig.json") (Join-Path $stage "data\config")

if (Test-Path $playerZip) { Remove-Item $playerZip -Force }
[System.IO.Compression.ZipFile]::CreateFromDirectory($packRoot, $playerZip)
Write-Host "Wrote $playerZip ($((Get-Item $playerZip).Length) bytes)"
