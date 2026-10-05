$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolRoot = Join-Path $projectRoot '.toolchain'
$sdkRoot = Join-Path $toolRoot 'android-sdk'
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user'
$env:ANDROID_AVD_HOME = Join-Path $toolRoot 'avd'
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
$outputRoot = Join-Path $projectRoot 'build\device'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$process = Start-Process -FilePath $emulator -ArgumentList @('-avd','frontline-test','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader','-memory','1536','-cores','2','-port','5580') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $outputRoot 'emulator.log') -RedirectStandardError (Join-Path $outputRoot 'emulator-error.log')
Write-Host "Test emulator process: $($process.Id)"
$device = 'emulator-5580'
$deadline = [DateTime]::UtcNow.AddMinutes(4)
do {
    Start-Sleep -Seconds 2
    $process.Refresh()
    if ($process.HasExited) { throw 'Emulator exited. Check build/device/emulator-error.log.' }
    $ErrorActionPreference = 'Continue'
    $boot = (& $adb -s $device shell getprop sys.boot_completed 2>$null)
    $ErrorActionPreference = 'Stop'
} while ($boot -ne '1' -and [DateTime]::UtcNow -lt $deadline)
if ($boot -ne '1') { & $adb -s $device emu kill; throw 'Android boot timed out.' }
& $adb -s $device shell wm size 720x1280
& $adb -s $device shell wm density 320
& $adb -s $device shell input keyevent 82
& $adb -s $device install -r (Join-Path $projectRoot 'build\Frontline-debug.apk')
if ($LASTEXITCODE -ne 0) { & $adb -s $device emu kill; throw 'APK install failed.' }
& $adb -s $device shell am start -W -n 'com.frontline.offline/.MainActivity'
Start-Sleep -Seconds 1
& $adb -s $device shell screencap -p '/sdcard/frontline-launch.png'
& $adb -s $device pull '/sdcard/frontline-launch.png' (Join-Path $outputRoot 'launch.png')
Write-Host 'Test device is running; screenshots are in build/device.'
