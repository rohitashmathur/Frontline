param([switch]$BuildOnly, [switch]$RestoreOnly)
$ErrorActionPreference = 'Stop'
if ($BuildOnly -and $RestoreOnly) { throw 'Choose only one mode.' }
$root = Split-Path -Parent $PSScriptRoot
if (-not $RestoreOnly) { & (Join-Path $PSScriptRoot 'test-android-v11.ps1') -BuildOnly }
if ($BuildOnly) { return }
$adb = Join-Path $root '.toolchain\android-sdk\platform-tools\adb.exe'
$device = 'emulator-5580'
$component = 'com.frontline.offline.tests/com.frontline.offline.tests.NativeV11Test'
$shots = Join-Path $root 'build\device\v12-native'
function Adb([string[]]$Arguments) {
    $result = & $adb -s $device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed on ${device}: $Arguments" }
    return $result
}
function Instrument([string]$Mode,[string]$Shot) {
    $result = Adb @('shell','am','instrument','-r','-w','-e','mode',$Mode,'-e','shot',$Shot,$component)
    $result | Write-Output
    $text = $result -join "`n"
    if ($text -notmatch 'INSTRUMENTATION_CODE: -1' -or $text -notmatch "PASS: native V11 $Mode;" -or $text -match 'FAIL:') { throw "V12 $Mode failed." }
}
if ((Adb @('get-state')) -ne 'device' -or (Adb @('shell','getprop','ro.kernel.qemu')) -ne '1') { throw 'Only emulator-5580 is supported; no physical-device fixtures.' }
if ($RestoreOnly) { Instrument 'restore' 'v12-recovery'; return }
if ((Adb @('shell','pm','path','com.frontline.offline')) -notmatch '^package:') { throw 'Install the production V12 APK separately first.' }
$size = (Adb @('shell','wm','size')) -join "`n"
$density = (Adb @('shell','wm','density')) -join "`n"
$oldSize = if ($size -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
$oldDensity = if ($density -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
New-Item -ItemType Directory -Force -Path $shots | Out-Null
Adb @('install','-r','-t',(Join-Path $root 'build\android-v11-native\v11-native-tests.apk')) | Out-Null
$failure = $null
$recovered = $false
try {
    foreach ($viewport in @(@('480x800','240','v12-small'),@('720x1600','320','v12-tall'),@('1200x1920','320','v12-tablet'),@('1200x800','320','v12-landscape'))) {
        Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
        Adb @('shell','wm','size',$viewport[0]) | Out-Null
        Adb @('shell','wm','density',$viewport[1]) | Out-Null
        Start-Sleep -Milliseconds 700
        try { Instrument 'v12screens' $viewport[2] }
        finally { Adb @('pull',"/sdcard/Android/data/com.frontline.offline/files/v11-native/$($viewport[2])",$shots) | Out-Null }
    }
} catch { $failure = $_ }
finally {
    try { Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null; Instrument 'restore' 'v12-recovery'; $recovered = $true } catch { Write-Warning "Preference recovery: $_"; if (-not $failure) { $failure = $_ } }
    try { Adb @('shell','wm','size',$oldSize) | Out-Null } catch { Write-Warning "Display recovery: $_"; if (-not $failure) { $failure = $_ } }
    try { Adb @('shell','wm','density',$oldDensity) | Out-Null } catch { Write-Warning "Density recovery: $_"; if (-not $failure) { $failure = $_ } }
    if ($recovered) { Adb @('uninstall','com.frontline.offline.tests') | Out-Null }
}
if ($failure) { throw $failure }
Write-Host "PASS: V12 Command Deck/campaign/settings in three offline languages at four native viewports. Artifacts: $shots"
