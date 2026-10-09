param([switch]$BuildOnly, [switch]$RunDevice, [switch]$RestoreOnly)
$ErrorActionPreference = 'Stop'
if (($BuildOnly -and ($RunDevice -or $RestoreOnly)) -or ($RunDevice -and $RestoreOnly)) { throw 'Choose only one mode.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.toolchain'
$sdk = Join-Path $toolRoot 'android-sdk'
$output = Join-Path $projectRoot 'build\android-v11-logistics-native'
$screenshots = Join-Path $projectRoot 'build\device\v11-logistics-native'
$apk = Join-Path $output 'v11-logistics-native-tests.apk'
$displayBackup = Join-Path $output 'display-original.json'
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
    $manifest = Join-Path $projectRoot 'android-tests\AndroidManifest.xml'
    [xml]$manifestXml = Get-Content -LiteralPath $manifest -Raw
    $registered = @($manifestXml.manifest.instrumentation | Where-Object {
        $_.GetAttribute('name', 'http://schemas.android.com/apk/res/android') -eq 'com.frontline.offline.tests.NativeV11LogisticsTest'
    }).Count -eq 1
    if ($RunDevice -and -not $registered) { throw 'Register NativeV11LogisticsTest in the shared test manifest before a device run.' }
    $unsigned = Join-Path $output 'unsigned.apk'
    Checked (Join-Path $buildTools 'aapt.exe') @('package', '-f', '-M', $manifest, '-I', $androidJar, '-F', $unsigned)
    # Reflection-only: compile this sidecar against Android, never against production app classes.
    Checked (Join-Path $jdk.FullName 'bin\javac.exe') @('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', $androidJar, '-d', $classes,
        (Join-Path $projectRoot 'android-tests\com\frontline\offline\tests\NativeV11LogisticsTest.java'))
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
    if (-not $RunDevice) {
        if (-not $registered) { Write-Warning 'Build succeeded; register NativeV11LogisticsTest in the shared test manifest before a device run.' }
        Write-Host "PASS: Logistics sidecar built and signed; no device contacted. APK: $apk"
        return
    }
}

# Device execution is opt-in; do not run another harness on this emulator concurrently.
# Never rebuild/install production, clear its data, or target another serial.
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$device = 'emulator-5580'
$component = 'com.frontline.offline.tests/com.frontline.offline.tests.NativeV11LogisticsTest'
function Adb([string[]]$Arguments) {
    $result = & $adb -s $device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed on ${device}: $Arguments" }
    return $result
}
function Instrument([string[]]$Arguments, [string]$Mode) {
    $result = Adb (@('shell', 'am', 'instrument', '-r', '-w') + $Arguments + @($component))
    $result | Write-Output
    $text = $result -join "`n"
    if ($text -notmatch 'INSTRUMENTATION_CODE: -1' -or $text -notmatch "PASS: native V11 Logistics $Mode;" -or $text -match 'FAIL:') { throw "Native Logistics $Mode failed." }
}
function DisplayOverride([string]$Kind) {
    $text = (Adb @('shell', 'wm', $Kind)) -join "`n"
    $pattern = if ($Kind -eq 'size') { 'Override size: (\d+x\d+)' } else { 'Override density: (\d+)' }
    if ($text -match $pattern) { return $Matches[1] }
    return 'reset'
}
if ((Adb @('get-state')) -ne 'device') { throw 'Only the running named emulator-5580 is supported.' }
if ((Adb @('shell', 'getprop', 'ro.kernel.qemu')) -ne '1') { throw 'Refusing fixtures on a physical device.' }
if ([int](Adb @('shell', 'getprop', 'ro.build.version.sdk')) -lt 24) { throw 'Native harness requires emulator API 24 or newer.' }
if ((Adb @('shell', 'pm', 'path', 'com.frontline.offline')) -notmatch '^package:') { throw 'Install the final production APK separately first.' }
if (-not $RestoreOnly -and (Test-Path -LiteralPath $displayBackup)) { throw 'Interrupted display backup exists. Run this script with -RestoreOnly first.' }

$failure = $null
$cleanupFailures = [System.Collections.Generic.List[string]]::new()
$preferencesRestored = $false
if ($RestoreOnly) {
    if ((Adb @('shell', 'pm', 'path', 'com.frontline.offline.tests')) -notmatch '^package:') { throw 'Recovery harness must remain installed to restore original preferences.' }
} else {
    $display = @{ device = $device; size = (DisplayOverride 'size'); density = (DisplayOverride 'density') }
    [IO.File]::WriteAllText($displayBackup, ($display | ConvertTo-Json))
}

try {
    if (-not $RestoreOnly) {
        Adb @('install', '-r', '-t', $apk) | Out-Null
        foreach ($viewport in @(@('480x800', '240', 'logistics-small', 'false'), @('720x1600', '320', 'logistics-tall', 'true'), @('1200x1920', '320', 'logistics-tablet', 'false'))) {
            Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null
            Adb @('shell', 'wm', 'size', $viewport[0]) | Out-Null
            Adb @('shell', 'wm', 'density', $viewport[1]) | Out-Null
            Start-Sleep -Milliseconds 700
            try {
                Instrument @('-e', 'mode', 'suite', '-e', 'shot', $viewport[2], '-e', 'checkpoint', $viewport[3]) 'suite'
                if ($viewport[3] -eq 'true') {
                    # Exactly one retained checkpoint is verified in a separate instrumentation process.
                    Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null
                    Instrument @('-e', 'mode', 'relaunch', '-e', 'shot', $viewport[2]) 'relaunch'
                }
            } finally {
                try { Adb @('pull', "/sdcard/Android/data/com.frontline.offline/files/v11-logistics-native/$($viewport[2])", $screenshots) | Out-Null }
                catch { Write-Warning "Fixture artifact pull failed: $_" }
            }
        }
    }
} catch { $failure = $_ }
finally {
    try {
        Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null
        Instrument @('-e', 'mode', 'restore') 'restore'
        $preferencesRestored = $true
    } catch { $cleanupFailures.Add("Preference recovery: $_") }
    if (Test-Path -LiteralPath $displayBackup) {
        try {
            $original = Get-Content -LiteralPath $displayBackup -Raw | ConvertFrom-Json
            if ($original.device -ne $device -or $original.size -notmatch '^(reset|\d+x\d+)$' -or $original.density -notmatch '^(reset|\d+)$') { throw 'Invalid display recovery backup.' }
            $displayFailures = 0
            try {
                Adb @('shell', 'wm', 'size', $original.size) | Out-Null
                if ((DisplayOverride 'size') -ne $original.size) { throw 'Size override did not restore exactly.' }
            } catch { $cleanupFailures.Add("Display size recovery: $_"); $displayFailures++ }
            try {
                Adb @('shell', 'wm', 'density', $original.density) | Out-Null
                if ((DisplayOverride 'density') -ne $original.density) { throw 'Density override did not restore exactly.' }
            } catch { $cleanupFailures.Add("Display density recovery: $_"); $displayFailures++ }
            if ($displayFailures -eq 0) { Remove-Item -LiteralPath $displayBackup }
        } catch { $cleanupFailures.Add("Display backup recovery: $_") }
    }
    try { Adb @('shell', 'am', 'force-stop', 'com.frontline.offline') | Out-Null } catch { $cleanupFailures.Add("Final force-stop: $_") }
    # Keep this test package installed: the shared package may belong to another native sidecar.
}
foreach ($cleanupFailure in $cleanupFailures) { Write-Warning $cleanupFailure }
if (-not $preferencesRestored -or $cleanupFailures.Count) { Write-Warning 'Recovery backup retained where needed. Run scripts/test-android-v11-logistics.ps1 -RestoreOnly on emulator-5580.' }
if ($failure) { throw $failure }
if ($cleanupFailures.Count) { throw 'Native Logistics cleanup was incomplete; see recovery warnings.' }
if ($RestoreOnly) { Write-Host 'PASS: original preferences and saved display overrides restored.'; return }
Write-Host "PASS: native Logistics en/id/hi at small/tall/tablet; one exact mid-route process checkpoint; isolated records/route corruption. Artifacts: $screenshots"
