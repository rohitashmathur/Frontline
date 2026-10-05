$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.toolchain'
$jdkHome = Get-ChildItem -LiteralPath $toolRoot -Directory -Filter 'jdk-*' | Select-Object -First 1
$env:JAVA_HOME = $jdkHome.FullName
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$sdkRoot = Join-Path $toolRoot 'android-sdk'
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user'
$env:ANDROID_AVD_HOME = Join-Path $toolRoot 'avd'
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME,$env:ANDROID_AVD_HOME | Out-Null
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
Write-Host 'Downloading the Android emulator and an Android 15 test image...'
& $sdkManager "--sdk_root=$sdkRoot" 'emulator' 'system-images;android-35;default;x86_64'
if ($LASTEXITCODE -ne 0) { throw 'Emulator installation failed.' }
$avdManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\avdmanager.bat'
'no' | & $avdManager create avd --force --name frontline-test --package 'system-images;android-35;default;x86_64' --path (Join-Path $env:ANDROID_AVD_HOME 'frontline-test.avd')
if ($LASTEXITCODE -ne 0) { throw 'Test device creation failed.' }
& (Join-Path $sdkRoot 'emulator\emulator.exe') -accel-check
Write-Host 'Emulator setup complete.'
