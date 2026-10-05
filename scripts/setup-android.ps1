param([switch]$AcceptSdkLicense)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.toolchain'
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$jdkHome = Get-ChildItem -LiteralPath $toolRoot -Directory -Filter 'jdk-*' | Select-Object -First 1
if (-not $jdkHome) {
    Write-Host 'Downloading Eclipse Temurin Java 17...'
    $assets = Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = $assets[0].binary.package
    $jdkArchive = Join-Path $toolRoot 'java17.zip'
    Invoke-WebRequest -UseBasicParsing -Uri $package.link -OutFile $jdkArchive
    if ((Get-FileHash -LiteralPath $jdkArchive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $package.checksum) {
        throw 'Java archive checksum mismatch.'
    }
    Expand-Archive -LiteralPath $jdkArchive -DestinationPath $toolRoot -Force
    $jdkHome = Get-ChildItem -LiteralPath $toolRoot -Directory -Filter 'jdk-*' | Select-Object -First 1
}
$env:JAVA_HOME = $jdkHome.FullName
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$sdkRoot = Join-Path $toolRoot 'android-sdk'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $sdkManager)) {
    Write-Host 'Downloading Android command-line tools...'
    $sdkArchive = Join-Path $toolRoot 'android-tools.zip'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' -OutFile $sdkArchive
    $sdkUnpack = Join-Path $toolRoot 'android-tools-unpacked'
    Expand-Archive -LiteralPath $sdkArchive -DestinationPath $sdkUnpack -Force
    $cmdlineParent = Join-Path $sdkRoot 'cmdline-tools'
    New-Item -ItemType Directory -Force -Path $cmdlineParent | Out-Null
    Move-Item -LiteralPath (Join-Path $sdkUnpack 'cmdline-tools') -Destination (Join-Path $cmdlineParent 'latest')
}
if (-not $AcceptSdkLicense) {
    throw 'Run with -AcceptSdkLicense after reviewing https://developer.android.com/studio#terms.'
}
Write-Host 'Accepting the Android SDK licenses approved for this setup...'
1..100 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdkRoot" --licenses
if ($LASTEXITCODE -ne 0) { throw 'Android license setup failed.' }
Write-Host 'Installing Android 35 build tools and platform tools...'
& $sdkManager "--sdk_root=$sdkRoot" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw 'Android package installation failed.' }
Write-Host 'Android build tools are ready.'
