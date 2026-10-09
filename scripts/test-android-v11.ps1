param([switch]$BuildOnly, [switch]$RestoreOnly)
$ErrorActionPreference = 'Stop'
if ($BuildOnly -and $RestoreOnly) { throw 'Choose BuildOnly or RestoreOnly, not both.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.toolchain'
$sdk = Join-Path $toolRoot 'android-sdk'
$output = Join-Path $projectRoot 'build\android-v11-native'
$screenshots = Join-Path $projectRoot 'build\device\v11-native'
$apk = Join-Path $output 'v11-native-tests.apk'
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
    # Only this self-contained V11 harness is compiled; there is no app/model classpath dependency.
    Checked (Join-Path $jdk.FullName 'bin\javac.exe') @('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', $androidJar, '-d', $classes,
        (Join-Path $projectRoot 'android-tests\com\frontline\offline\tests\NativeV11Test.java'))
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
    if ($BuildOnly) { Write-Host "V11 harness built without contacting a device: $apk"; return }
}

# Never rebuild, reinstall, or clear production data. Every device command is pinned to this emulator.
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$device = 'emulator-5580'
$component = 'com.frontline.offline.tests/com.frontline.offline.tests.NativeV11Test'
function Adb([string[]]$Arguments) {
    $result = & $adb -s $device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed on ${device}: $Arguments" }
    return $result
}
function Instrument([string[]]$Arguments, [string]$Mode) {
    $result = Adb (@('shell', 'am', 'instrument', '-r', '-w') + $Arguments + @($component))
    $result | Write-Output
    $text = $result -join "`n"
    if ($text -notmatch 'INSTRUMENTATION_CODE: -1' -or $text -notmatch "PASS: native V11 $Mode;" -or $text -match 'FAIL:') {
        throw "Native V11 $Mode failed."
    }
}
if ((Adb @('get-state')) -ne 'device') { throw 'Only the running named emulator-5580 is supported.' }
if ((Adb @('shell', 'getprop', 'ro.kernel.qemu')) -ne '1') { throw 'Refusing to run fixtures on a physical device.' }
if ([int](Adb @('shell', 'getprop', 'ro.build.version.sdk')) -lt 24) { throw 'Native glyph checks require emulator API 24 or newer.' }
if ((Adb @('shell', 'pm', 'path', 'com.frontline.offline')) -notmatch '^package:') { throw 'Install the final V11 production APK separately first.' }
if ($RestoreOnly) {
    Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null
    Instrument @('-e', 'mode', 'restore') 'restore'
    Write-Host 'Original preferences restored. The harness is retained for inspection.'
    return
}

$size = (Adb @('shell', 'wm', 'size')) -join "`n"
$density = (Adb @('shell', 'wm', 'density')) -join "`n"
$oldSize = if ($size -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
$oldDensity = if ($density -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
Adb @('install', '-r', '-t', $apk) | Out-Null
$failure = $null
$cleanupFailures = [System.Collections.Generic.List[string]]::new()
$restored = $false
try {
    foreach ($viewport in @(@('480x800', '240', 'v11-small'), @('720x1600', '320', 'v11-tall'), @('1200x1920', '320', 'v11-tablet'))) {
        Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null
        Adb @('shell', 'wm', 'size', $viewport[0]) | Out-Null
        Adb @('shell', 'wm', 'density', $viewport[1]) | Out-Null
        Start-Sleep -Milliseconds 700
        try {
            Instrument @('-e', 'mode', 'suite', '-e', 'shot', $viewport[2]) 'suite'
            # Separate instrumentation processes, not just Activity recreation, verify all explicit languages.
            foreach ($transition in @(@('en', 'id'), @('id', 'hi'), @('hi', ''))) {
                Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null
                $arguments = @('-e', 'mode', 'relaunch', '-e', 'shot', $viewport[2], '-e', 'expectedLanguage', $transition[0])
                if ($transition[1]) { $arguments += @('-e', 'nextLanguage', $transition[1]) }
                Instrument $arguments 'relaunch'
            }
        } finally {
            # A screenshot-pull failure must not hide the original test failure or skip preference recovery.
            try { Adb @('pull', "/sdcard/Android/data/com.frontline.offline/files/v11-native/$($viewport[2])", $screenshots) | Out-Null }
            catch { Write-Warning "Fixture artifact pull failed: $_" }
        }
    }
} catch { $failure = $_ }
finally {
    try {
        Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null
        Instrument @('-e', 'mode', 'restore') 'restore'
        $restored = $true
    } catch { $cleanupFailures.Add("Preference recovery: $_") }
    # Attempt each restoration independently even if a preceding command fails.
    try { Adb @('shell', 'wm', 'size', $oldSize) | Out-Null } catch { $cleanupFailures.Add("Display size recovery: $_") }
    try { Adb @('shell', 'wm', 'density', $oldDensity) | Out-Null } catch { $cleanupFailures.Add("Display density recovery: $_") }
    try { Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null } catch { $cleanupFailures.Add("Final force-stop: $_") }
    if ($restored) {
        try { Adb @('uninstall', 'com.frontline.offline.tests') | Out-Null } catch { $cleanupFailures.Add("Harness removal: $_") }
    } else {
        Write-Warning 'Harness and original preference backup retained. Recover with .\scripts\test-android-v11.ps1 -RestoreOnly on emulator-5580.'
    }
}
foreach ($cleanupFailure in $cleanupFailures) { Write-Warning $cleanupFailure }
if ($failure) { throw $failure }
if ($cleanupFailures.Count) { throw 'Native V11 cleanup was incomplete; see recovery warnings.' }
Write-Host "PASS: native V11 languages, navigation, accessibility, process persistence and fixture glyph/layout checks at small/tall/tablet sizes. Artifacts: $screenshots"
