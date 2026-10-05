$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.toolchain'
$jdkHome = Get-ChildItem -LiteralPath $toolRoot -Directory -Filter 'jdk-*' | Select-Object -First 1
if (-not $jdkHome) { throw 'First run scripts/setup-android.ps1 -AcceptSdkLicense.' }
$env:JAVA_HOME = $jdkHome.FullName
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$sdkRoot = Join-Path $toolRoot 'android-sdk'
$buildTools = Join-Path $sdkRoot 'build-tools\35.0.0'
$androidJar = Join-Path $sdkRoot 'platforms\android-35\android.jar'
$buildRoot = Join-Path $projectRoot 'build\android'
$generated = Join-Path $buildRoot 'generated'
$classes = Join-Path $buildRoot 'classes'
$dex = Join-Path $buildRoot 'dex'
New-Item -ItemType Directory -Force -Path $generated,$classes,$dex | Out-Null

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
}

$unsignedApk = Join-Path $buildRoot 'unsigned.apk'
$musicClasses = Join-Path $projectRoot 'build\music'
New-Item -ItemType Directory -Force -Path $musicClasses | Out-Null
Invoke-Checked 'javac.exe' @('-d',$musicClasses,(Join-Path $projectRoot 'tools\com\frontline\offline\MusicGenerator.java'))
Invoke-Checked 'java.exe' @('-cp',$musicClasses,'com.frontline.offline.MusicGenerator',(Join-Path $projectRoot 'app\src\main\res\raw\frontier_theme.wav'))
Invoke-Checked (Join-Path $buildTools 'aapt.exe') @('package','-f','-m','--debug-mode','-J',$generated,'-M',(Join-Path $projectRoot 'app\src\main\AndroidManifest.xml'),'-S',(Join-Path $projectRoot 'app\src\main\res'),'-I',$androidJar,'-F',$unsignedApk,'--version-code','6','--version-name','0.6.0')
$sourceFiles = @((Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\src\main\java'),$generated -Recurse -Filter '*.java').FullName)
Invoke-Checked 'javac.exe' (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$androidJar,'-d',$classes) + $sourceFiles)
$classesJar = Join-Path $buildRoot 'classes.jar'
Invoke-Checked 'jar.exe' @('cf',$classesJar,'-C',$classes,'.')
Invoke-Checked (Join-Path $buildTools 'd8.bat') @('--lib',$androidJar,'--min-api','24','--output',$dex,$classesJar)
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$apkArchive = [IO.Compression.ZipFile]::Open($unsignedApk, [IO.Compression.ZipArchiveMode]::Update)
try {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apkArchive,(Join-Path $dex 'classes.dex'),'classes.dex') | Out-Null
} finally { $apkArchive.Dispose() }
$alignedApk = Join-Path $buildRoot 'aligned.apk'
Invoke-Checked (Join-Path $buildTools 'zipalign.exe') @('-f','-p','4',$unsignedApk,$alignedApk)
$keyStore = Join-Path $toolRoot 'debug.keystore'
if (-not (Test-Path -LiteralPath $keyStore)) {
    Invoke-Checked 'keytool.exe' @('-genkeypair','-keystore',$keyStore,'-storepass','android','-alias','androiddebugkey','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Android Debug,O=Android,C=US')
}
$apkPath = Join-Path $projectRoot 'build\Frontline-debug.apk'
Invoke-Checked (Join-Path $buildTools 'apksigner.bat') @('sign','--ks',$keyStore,'--ks-pass','pass:android','--key-pass','pass:android','--out',$apkPath,$alignedApk)
Invoke-Checked (Join-Path $buildTools 'apksigner.bat') @('verify','--verbose',$apkPath)
Write-Host "APK ready: $apkPath"
