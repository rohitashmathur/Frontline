$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $projectRoot '.toolchain\android-sdk\platform-tools\adb.exe'
$jdkHome = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.toolchain') -Directory -Filter 'jdk-*' | Select-Object -First 1
$java = Join-Path $jdkHome.FullName 'bin\java.exe'
$outputRoot = Join-Path $projectRoot 'build\device'
$device = 'emulator-5580'
function Invoke-Adb([string[]]$Arguments) {
    $output = & $adb -s $device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $Arguments" }
    return $output
}
function Screenshot([string]$Name) {
    Start-Sleep -Milliseconds 150
    Invoke-Adb @('shell','screencap','-p',"/sdcard/$Name.png") | Out-Null
    Invoke-Adb @('pull',"/sdcard/$Name.png",(Join-Path $outputRoot "$Name.png")) | Out-Null
}
function Read-Preferences {
    [xml]$preferences = (Invoke-Adb @('shell','run-as','com.frontline.offline','cat','shared_prefs/frontline-v1.xml')) -join "`n"
    return $preferences
}
function Load-Fixture([string]$Mode) {
    Invoke-Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
    $path = Join-Path $outputRoot "fixture$Mode.xml"
    $inputSave = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes((Read-Preferences).OuterXml))
    & $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.VictoryFixture' $inputSave $path $Mode
    if ($LASTEXITCODE -ne 0) { throw "Fixture generation failed: $Mode" }
    Invoke-Adb @('push',$path,'/data/local/tmp/frontline-fixture.xml') | Out-Null
    Invoke-Adb @('shell','run-as','com.frontline.offline','cp','/data/local/tmp/frontline-fixture.xml','shared_prefs/frontline-v1.xml') | Out-Null
}
function Start-App {
    Invoke-Adb @('shell','am','start','-W','-n','com.frontline.offline/.MainActivity') | Out-Null
    Start-Sleep -Milliseconds 1300
}
function Verify-Rule([string]$Rule) {
    $battle = ((Read-Preferences).map.string | Where-Object name -eq 'battle').InnerText
    & $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.SaveProbe' $battle $Rule
    if ($LASTEXITCODE -ne 0) { throw "Native verification failed: $Rule" }
}
function Verify-Music([string]$State) {
    $events = Invoke-Adb @('logcat','-d','-s','FrontlineMusic:D')
    $last = $events | Where-Object { $_ -match 'PLAYING|PAUSED|FAILED' } | Select-Object -Last 1
    if ($last -notmatch "$State$") { throw "Expected music $State, got: $last" }
}
function Pause-To-Menu {
    Invoke-Adb @('shell','input','keyevent','4') | Out-Null
    Tap 'pause-menu'
}
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$controls = (& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.DeviceControls') | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Could not calculate scene controls.' }
function Tap([string]$Control) {
    $point = $controls.$Control
    if (-not $point) { throw "Unknown test control: $Control" }
    Invoke-Adb @('shell','input','tap',"$($point[0])","$($point[1])") | Out-Null
    Start-Sleep -Milliseconds 160
}
# Only the named emulator is cleared; physical phones are never selected.
Invoke-Adb @('logcat','-c') | Out-Null
Invoke-Adb @('shell','pm','clear','com.frontline.offline') | Out-Null
Invoke-Adb @('shell','cmd','connectivity','airplane-mode','enable') | Out-Null
Start-App
Screenshot 'main-menu'
if (((Read-Preferences).map.boolean | Where-Object name -eq 'music').value -ne 'true') { throw 'Music did not default on.' }
Verify-Music 'PLAYING'
Tap 'fresh-play'
Screenshot 'tutorial-1'
Tap 'tutorial-tutorial_next'
Tap 'tutorial-tutorial_prev'
Tap 'tutorial-tutorial_next'
$from = $controls.'practice-from'; $to = $controls.'practice-to'
Invoke-Adb @('shell','input','swipe',"$($from[0])","$($from[1])","$($to[0])","$($to[1])",'220') | Out-Null
Tap 'tutorial-tutorial_next'
foreach ($control in @('tutorial-demo_half','tutorial-demo_all','tutorial-demo_quarter')) { Tap $control }
Screenshot 'tutorial-percentages'
Tap 'tutorial-tutorial_next'
foreach ($i in 1..4) { Tap 'booster-demo_capture' }
Screenshot 'tutorial-boost'
Tap 'booster-demo_capture'
Tap 'booster-demo_lose'
Tap 'tutorial-tutorial_prev'
Tap 'tutorial-tutorial_next'
Tap 'tutorial-tutorial_next'
Screenshot 'tutorial-5'
Tap 'tutorial-tutorial_next'
Tap 'battle-quarter'
$from = $controls.'swipe-from'; $to = $controls.'swipe-to'
Invoke-Adb @('shell','input','swipe',"$($from[0])","$($from[1])","$($to[0])","$($to[1])",'220') | Out-Null
Pause-To-Menu
Verify-Rule '8'
$before = ((Read-Preferences).map.string | Where-Object name -eq 'battle').InnerText
Invoke-Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
Start-App
if ($before -ne ((Read-Preferences).map.string | Where-Object name -eq 'battle').InnerText) { throw 'Paused V6 battle changed during restore.' }
Tap 'menu-settings'
Tap 'settings-music'; Verify-Music 'PAUSED'
Tap 'settings-music'; Verify-Music 'PLAYING'
Screenshot 'settings'
Tap 'settings-back'
Invoke-Adb @('shell','input','keyevent','3') | Out-Null
Start-Sleep -Milliseconds 300
Verify-Music 'PAUSED'
Start-App
Load-Fixture '--campaign'; Start-App
Tap 'menu-sectors'
Tap 'campaign-chapter_prev'; Tap 'campaign-chapter_prev'
foreach ($chapter in 1..10) {
    Screenshot "chapter-$chapter"
    if ($chapter -lt 10) { Tap 'campaign-chapter_next' }
}
Tap 'campaign-final-row'; Tap 'menu-play'
Screenshot 'battle-five-opponents'
Tap 'battle-zoom_in'; Tap 'battle-zoom_in'
Screenshot 'battle-zoom'
Invoke-Adb @('shell','input','swipe','420','650','540','710','450') | Out-Null
Screenshot 'battle-pan'
Tap 'battle-fit_board'
Pause-To-Menu
Verify-Rule '--camera'
foreach ($entry in @(@('--intercept',450,'interception'),@('--king',500,'battle-one-king'),@('--overflow',1400,'troop-caps'),@('--four-kings',450,'battle-four-kings'),@('--five-kings',450,'battle-five-kings'))) {
    Load-Fixture $entry[0]; Start-App; Tap 'menu-resume'
    Start-Sleep -Milliseconds $entry[1]
    Screenshot $entry[2]
    Pause-To-Menu
    Verify-Rule $entry[0]
}
Tap 'menu-resume'; Tap 'battle-restart'; Pause-To-Menu
Verify-Rule '--restart'
Load-Fixture '--resign'; Start-App; Tap 'menu-resume'
Start-Sleep -Milliseconds 3300
Screenshot 'rival-surrender'
Verify-Rule '--resign'
Tap 'result-menu'
Load-Fixture '--large-map'; Start-App; Tap 'menu-resume'
Pause-To-Menu
Load-Fixture '--victory'; Start-App; Tap 'menu-resume'
Start-Sleep -Milliseconds 500
Screenshot 'campaign-complete'
Verify-Rule '--final'
Tap 'result-menu'
# Install the actual V5 binary when supplied, then upgrade with the same key.
$previous = Join-Path $projectRoot 'build\FrontlineV5.apk'
Load-Fixture '--v5'
if (Test-Path -LiteralPath $previous) { Invoke-Adb @('install','-r','-d',$previous) | Out-Null }
else { Write-Host 'V5 APK unavailable: exercising legacy save migration without a binary downgrade.' }
Start-App
$legacy = Read-Preferences
Invoke-Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
Invoke-Adb @('install','-r',(Join-Path $projectRoot 'build\Frontline-debug.apk')) | Out-Null
Start-App
$upgraded = Read-Preferences
foreach ($key in @('selected-sector','difficulty','wins','music','sound','haptics','tutorial-seen','best-29','stars-29')) {
    $old = $legacy.map.ChildNodes | Where-Object name -eq $key
    $new = $upgraded.map.ChildNodes | Where-Object name -eq $key
    if ($old.OuterXml -ne $new.OuterXml) { throw "V5 upgrade changed preference: $key" }
}
if (($upgraded.map.int | Where-Object name -eq 'unlocked').value -ne '30') { throw 'Cleared V5 campaign did not unlock Sector 31.' }
if ([float]($upgraded.map.float | Where-Object name -eq 'time-29').value -ne 90) { throw 'V5 best time was not retained.' }
Verify-Rule '--migration'
Screenshot 'v5-upgrade'
Tap 'menu-sectors'; Tap 'campaign-chapter_next'
Screenshot 'chapter-6-unlocked'
Tap 'campaign-final-row'
if (((Read-Preferences).map.int | Where-Object name -eq 'selected-sector').value -ne '29') { throw 'A locked new sector was selectable.' }
Tap 'campaign-first-row'; Tap 'menu-play'; Pause-To-Menu
$battle = ((Read-Preferences).map.string | Where-Object name -eq 'battle').InnerText
& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.SaveProbe' $battle '--level' '30'
if ($LASTEXITCODE -ne 0) { throw 'Newly unlocked Sector 31 did not launch.' }
Invoke-Adb @('shell','input','keyevent','3') | Out-Null
Start-Sleep -Milliseconds 300
& (Join-Path $PSScriptRoot 'test-android-gestures.ps1')
$errors = Invoke-Adb @('logcat','-d','-s','AndroidRuntime:E')
if ($errors -match 'FATAL EXCEPTION') { throw 'Android runtime crash detected.' }
Write-Host 'PASS: V6 offline tutorial, expanded swipe, ten chapters, five opponents, zoom/pan/fit, persistent king boosts, fixed caps, resignation, restart, music, saves, V5 upgrade, phone/tablet rendering, and no crashes.'
