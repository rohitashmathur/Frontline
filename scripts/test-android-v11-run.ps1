param([switch]$BuildOnly, [switch]$RestoreOnly, [ValidateSet('small', 'tall', 'tablet')][string[]]$Viewport = @('small', 'tall', 'tablet'))
$ErrorActionPreference = 'Stop'
if ($BuildOnly -and $RestoreOnly) { throw 'Choose BuildOnly or RestoreOnly, not both.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.toolchain'
$sdk = Join-Path $toolRoot 'android-sdk'
$output = Join-Path $projectRoot 'build\android-v11-run-native'
$screenshots = Join-Path $projectRoot 'build\device\v11-run-native'
$apk = Join-Path $output 'v11-run-native-tests.apk'
function Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed: $LASTEXITCODE" }
}
if (-not $RestoreOnly) {
    $jdk = Get-ChildItem -LiteralPath $toolRoot -Directory -Filter 'jdk-*' | Select-Object -First 1
    if (-not $jdk) { throw 'Bundled Java 17 is required.' }
    $env:JAVA_HOME = $jdk.FullName
    $buildTools = Join-Path $sdk 'build-tools\35.0.0'
    $androidJar = Join-Path $sdk 'platforms\android-35\android.jar'
    $classes = Join-Path $output 'classes'
    $dex = Join-Path $output 'dex'
    New-Item -ItemType Directory -Force -Path $classes, $dex, $screenshots | Out-Null
    $unsigned = Join-Path $output 'unsigned.apk'
    Checked (Join-Path $buildTools 'aapt.exe') @('package', '-f', '-M', (Join-Path $projectRoot 'android-tests\AndroidManifest.xml'), '-I', $androidJar, '-F', $unsigned)
    # Reflection-only harness: no production Java source or model classes on this classpath.
    Checked (Join-Path $jdk.FullName 'bin\javac.exe') @('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', $androidJar, '-d', $classes,
        (Join-Path $projectRoot 'android-tests\com\frontline\offline\tests\NativeV11RunTest.java'))
    $jar = Join-Path $output 'classes.jar'
    Checked (Join-Path $jdk.FullName 'bin\jar.exe') @('cf', $jar, '-C', $classes, '.')
    Checked (Join-Path $buildTools 'd8.bat') @('--lib', $androidJar, '--min-api', '24', '--output', $dex, $jar)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    $archive = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
    try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $dex 'classes.dex'), 'classes.dex') | Out-Null }
    finally { $archive.Dispose() }
    $aligned = Join-Path $output 'aligned.apk'
    Checked (Join-Path $buildTools 'zipalign.exe') @('-f', '4', $unsigned, $aligned)
    Checked (Join-Path $buildTools 'apksigner.bat') @('sign', '--ks', (Join-Path $toolRoot 'debug.keystore'), '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', $apk, $aligned)
    Checked (Join-Path $buildTools 'apksigner.bat') @('verify', $apk)
    if ($BuildOnly) { Write-Host "Run harness built without device access: $apk"; return }
}

# The production APK is never built, installed, or cleared here. All ADB calls target this emulator only.
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$device = 'emulator-5580'
$component = 'com.frontline.offline.tests/com.frontline.offline.tests.NativeV11RunTest'
function Adb([string[]]$Arguments) {
    $result = & $adb -s $device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed on ${device}: $Arguments" }
    return $result
}
function Instrument([string]$Mode, [string]$Shot) {
    $result = Adb @('shell', 'am', 'instrument', '-r', '-w', '-e', 'mode', $Mode, '-e', 'shot', $Shot, $component)
    $result | Write-Output
    New-Item -ItemType Directory -Force -Path $screenshots | Out-Null
    $result | Set-Content -LiteralPath (Join-Path $screenshots "$Shot-$Mode.txt") -Encoding UTF8
    $text = $result -join "`n"
    if ($text -notmatch 'INSTRUMENTATION_CODE: -1' -or $text -notmatch "PASS: native V11 Run $Mode;" -or $text -match 'FAIL:') {
        throw "Native V11 Run $Mode failed at $Shot."
    }
}
if ((Adb @('get-state')) -ne 'device' -or (Adb @('shell', 'getprop', 'ro.kernel.qemu')) -ne '1') { throw 'Only emulator-5580 is supported.' }
if ((Adb @('shell', 'pm', 'path', 'com.frontline.offline')) -notmatch '^package:') { throw 'Parent must install the coherent Phase 3 production APK first.' }
if ($RestoreOnly) {
    Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null
    Instrument 'restore' 'run-recovery'
    return
}
$size = (Adb @('shell', 'wm', 'size')) -join "`n"
$density = (Adb @('shell', 'wm', 'density')) -join "`n"
$oldSize = if ($size -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
$oldDensity = if ($density -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
Adb @('install', '-r', '-t', $apk) | Out-Null
$failure = $null
$cleanup = [System.Collections.Generic.List[string]]::new()
$restored = $false
$completed = [System.Collections.Generic.List[string]]::new()
try {
    foreach ($name in $Viewport) {
        $dimensions = switch ($name) { 'small' { @('480x800', '240') } 'tall' { @('720x1600', '320') } 'tablet' { @('1200x1920', '320') } }
        $shot = "run-$name"
        Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null
        Adb @('shell', 'wm', 'size', $dimensions[0]) | Out-Null
        Adb @('shell', 'wm', 'density', $dimensions[1]) | Out-Null
        Start-Sleep -Milliseconds 700
        try {
            Instrument 'suite' $shot
            # A distinct app/instrumentation process verifies the saved council, not Activity-only recreation.
            Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null
            Instrument 'relaunch' $shot
            $completed.Add("${name}: $($dimensions[0]) density $($dimensions[1]), en/id/hi, 1 separate-process council checkpoint")
        } finally {
            try { Adb @('pull', "/sdcard/Android/data/com.frontline.offline/files/v11-run-native/$shot", $screenshots) | Out-Null }
            catch { Write-Warning "Artifact pull failed: $_" }
        }
    }
} catch { $failure = $_ }
finally {
    try {
        Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null
        Instrument 'restore' 'run-recovery'
        $restored = $true
    } catch { $cleanup.Add("Preference recovery: $_") }
    try { Adb @('shell', 'wm', 'size', $oldSize) | Out-Null } catch { $cleanup.Add("Display size recovery: $_") }
    try { Adb @('shell', 'wm', 'density', $oldDensity) | Out-Null } catch { $cleanup.Add("Density recovery: $_") }
    try { Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null } catch { $cleanup.Add("Final force-stop: $_") }
    if ($restored) {
        try { Adb @('uninstall', 'com.frontline.offline.tests') | Out-Null } catch { $cleanup.Add("Harness removal: $_") }
    } else { Write-Warning 'Backup and harness retained. Recover with .\scripts\test-android-v11-run.ps1 -RestoreOnly on emulator-5580.' }
}
$completed | Write-Output
foreach ($error in $cleanup) { Write-Warning $error }
if ($failure) { throw $failure }
if ($cleanup.Count) { throw 'Run harness cleanup was incomplete.' }
Write-Host "PASS: native Phase 3 Run-only fixtures; separate-process checkpoints=$($completed.Count); artifacts=$screenshots"
