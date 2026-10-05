$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.toolchain'
$jdkHome = Get-ChildItem -LiteralPath $toolRoot -Directory -Filter 'jdk-*' | Select-Object -First 1
$env:JAVA_HOME = $jdkHome.FullName
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$sdk = Join-Path $toolRoot 'android-sdk'
$buildTools = Join-Path $sdk 'build-tools\35.0.0'
$androidJar = Join-Path $sdk 'platforms\android-35\android.jar'
$output = Join-Path $projectRoot 'build\android-gestures'
$classes = Join-Path $output 'classes'
$dex = Join-Path $output 'dex'
New-Item -ItemType Directory -Force -Path $classes,$dex | Out-Null
function Checked([string]$Program,[string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed: $LASTEXITCODE" }
}
$unsigned = Join-Path $output 'unsigned.apk'
Checked (Join-Path $buildTools 'aapt.exe') @('package','-f','-M',(Join-Path $projectRoot 'android-tests\AndroidManifest.xml'),'-I',$androidJar,'-F',$unsigned)
Checked 'javac.exe' @('-encoding','UTF-8','-source','8','-target','8','-classpath',$androidJar,'-d',$classes,(Join-Path $projectRoot 'android-tests\com\frontline\offline\tests\NativeGestureTest.java'))
$jar = Join-Path $output 'classes.jar'
Checked 'jar.exe' @('cf',$jar,'-C',$classes,'.')
Checked (Join-Path $buildTools 'd8.bat') @('--lib',$androidJar,'--min-api','24','--output',$dex,$jar)
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$archive = [IO.Compression.ZipFile]::Open($unsigned,[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $dex 'classes.dex'),'classes.dex') | Out-Null }
finally { $archive.Dispose() }
$aligned = Join-Path $output 'aligned.apk'
$apk = Join-Path $output 'gesture-tests.apk'
Checked (Join-Path $buildTools 'zipalign.exe') @('-f','4',$unsigned,$aligned)
Checked (Join-Path $buildTools 'apksigner.bat') @('sign','--ks',(Join-Path $toolRoot 'debug.keystore'),'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,$aligned)
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$device = 'emulator-5580'
function Adb([string[]]$Arguments) {
    $result = & $adb -s $device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $Arguments" }
    return $result
}
Adb @('install','-r','-t',$apk) | Out-Null
try {
    foreach ($viewport in @(@('480x800','240','battle-small-phone'),@('720x1280','320','battle-phone'),@('1200x1920','320','battle-tablet'))) {
        Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
        Adb @('shell','wm','size',$viewport[0]) | Out-Null
        Adb @('shell','wm','density',$viewport[1]) | Out-Null
        Start-Sleep -Milliseconds 700
        $result = Adb @('shell','am','instrument','-r','-w','-e','shot',$viewport[2],'com.frontline.offline.tests/com.frontline.offline.tests.NativeGestureTest')
        $result | Write-Output
        if (($result -join "`n") -notmatch 'INSTRUMENTATION_CODE: -1') { throw "Native gestures failed at $($viewport[0])" }
        Adb @('pull',"/sdcard/Android/data/com.frontline.offline/files/$($viewport[2]).png",(Join-Path $projectRoot "build\device\$($viewport[2]).png")) | Out-Null
    }
} finally {
    Adb @('shell','wm','size','720x1280') | Out-Null
    Adb @('shell','wm','density','320') | Out-Null
    Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
    Adb @('uninstall','com.frontline.offline.tests') | Out-Null
}
Write-Host 'PASS: native multi-touch and actual nonblank battle screenshots at all three phone/tablet viewports.'
