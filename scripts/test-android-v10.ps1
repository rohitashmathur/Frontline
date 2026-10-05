param([switch]$BuildOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.toolchain'
$jdk = Get-ChildItem -LiteralPath $toolRoot -Directory -Filter 'jdk-*' | Select-Object -First 1
if (-not $jdk) { throw 'Bundled Java 17 is required.' }
$env:JAVA_HOME = $jdk.FullName
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$sdk = Join-Path $toolRoot 'android-sdk'
$buildTools = Join-Path $sdk 'build-tools\35.0.0'
$androidJar = Join-Path $sdk 'platforms\android-35\android.jar'
$output = Join-Path $projectRoot 'build\android-v10-native'
$classes = Join-Path $output 'classes'
$dex = Join-Path $output 'dex'
$screenshots = Join-Path $projectRoot 'build\device\v10-native'
New-Item -ItemType Directory -Force -Path $classes,$dex,$screenshots | Out-Null
function Checked([string]$Program,[string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed: $LASTEXITCODE" }
}
$unsigned = Join-Path $output 'unsigned.apk'
Checked (Join-Path $buildTools 'aapt.exe') @('package','-f','-M',(Join-Path $projectRoot 'android-tests\AndroidManifest.xml'),'-I',$androidJar,'-F',$unsigned)
$sources = @('NativeGestureTest.java','NativeV10Test.java') | ForEach-Object { Join-Path $projectRoot "android-tests\com\frontline\offline\tests\$_" }
Checked (Join-Path $jdk.FullName 'bin\javac.exe') (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$androidJar,'-d',$classes) + $sources)
$jar = Join-Path $output 'classes.jar'
Checked (Join-Path $jdk.FullName 'bin\jar.exe') @('cf',$jar,'-C',$classes,'.')
Checked (Join-Path $buildTools 'd8.bat') @('--lib',$androidJar,'--min-api','24','--output',$dex,$jar)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open($unsigned,[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $dex 'classes.dex'),'classes.dex') | Out-Null }
finally { $archive.Dispose() }
$aligned = Join-Path $output 'aligned.apk'
$apk = Join-Path $output 'v10-native-tests.apk'
Checked (Join-Path $buildTools 'zipalign.exe') @('-f','4',$unsigned,$aligned)
Checked (Join-Path $buildTools 'apksigner.bat') @('sign','--ks',(Join-Path $toolRoot 'debug.keystore'),'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,$aligned)
Checked (Join-Path $buildTools 'apksigner.bat') @('verify',$apk)
if ($BuildOnly) { Write-Host "Harness built without contacting any device: $apk"; return }

# This harness neither rebuilds nor reinstalls the production APK or the V6 migration fixture.
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$device = 'emulator-5580'
$component = 'com.frontline.offline.tests/com.frontline.offline.tests.NativeV10Test'
function Adb([string[]]$Arguments) {
    $result = & $adb -s $device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $Arguments" }
    return $result
}
function Instrument([string[]]$Arguments) {
    $result = Adb (@('shell','am','instrument','-r','-w') + $Arguments + @($component))
    $result | Write-Output
    if (($result -join "`n") -notmatch 'INSTRUMENTATION_CODE: -1' -or ($result -join "`n") -notmatch 'PASS: native V10') { throw 'Native V10 instrumentation failed.' }
}
if ((Adb @('get-state')) -ne 'device') { throw 'Only the running named emulator-5580 is supported.' }
if ((Adb @('shell','getprop','ro.kernel.qemu')) -ne '1') { throw 'Refusing to install a fixture on a physical device.' }
if ([int](Adb @('shell','getprop','ro.build.version.sdk')) -lt 26) { throw 'The export ActivityMonitor requires emulator API 26 or newer.' }
if ((Adb @('shell','pm','path','com.frontline.offline')) -notmatch '^package:') { throw 'Install the current V10 production APK separately first.' }
$size = (Adb @('shell','wm','size')) -join "`n"
$density = (Adb @('shell','wm','density')) -join "`n"
$oldSize = if ($size -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
$oldDensity = if ($density -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
Adb @('install','-r','-t',$apk) | Out-Null
$restored = $false
try {
    foreach ($viewport in @(@('480x800','240','v10-small'),@('720x1600','320','v10-tall'),@('1200x1920','320','v10-tablet'))) {
        Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
        Adb @('shell','wm','size',$viewport[0]) | Out-Null
        Adb @('shell','wm','density',$viewport[1]) | Out-Null
        Start-Sleep -Milliseconds 700
        try { Instrument @('-e','shot',$viewport[2]) }
        finally { Adb @('pull',"/sdcard/Android/data/com.frontline.offline/files/v10-native/$($viewport[2])",$screenshots) | Out-Null }
    }
} finally {
    Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
    try { Instrument @('-e','restore','true'); $restored = $true }
    finally {
        Adb @('shell','wm','size',$oldSize) | Out-Null
        Adb @('shell','wm','density',$oldDensity) | Out-Null
        Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
        if ($restored) { Adb @('uninstall','com.frontline.offline.tests') | Out-Null }
        else { Write-Warning 'Harness retained for recovery: run its NativeV10Test instrumentation with -e restore true on emulator-5580.' }
    }
}
Write-Host "PASS: native V10 fixture, lifecycle, storage, export and screenshots at small/tall/tablet sizes. Artifacts: $screenshots"
