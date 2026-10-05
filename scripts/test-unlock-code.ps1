$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $projectRoot '.toolchain\android-sdk\platform-tools\adb.exe'
$jdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.toolchain') -Directory -Filter 'jdk-*' | Select-Object -First 1
$java = Join-Path $jdk.FullName 'bin\java.exe'
$device = 'emulator-5580'
$output = Join-Path $projectRoot 'build\device'
$apk = Join-Path $projectRoot 'build\Frontline-debug.apk'
$previous = Join-Path $projectRoot 'build\FrontlineV6.apk'
function Adb([string[]]$Arguments) {
    $result = & $adb -s $device @Arguments
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $Arguments" }
    return $result
}
function Start-App {
    Adb @('shell','am','start','-W','-n','com.frontline.offline/.MainActivity') | Out-Null
    Start-Sleep -Milliseconds 1300
}
function Preferences {
    [xml]$xml = (Adb @('shell','run-as','com.frontline.offline','cat','shared_prefs/frontline-v1.xml')) -join "`n"
    $values = @{}
    foreach ($node in $xml.map.ChildNodes) {
        if ($node.NodeType -ne [Xml.XmlNodeType]::Element) { continue }
        $values[$node.GetAttribute('name')] = if ($node.LocalName -eq 'string') { $node.InnerText } else { $node.GetAttribute('value') }
    }
    return $values
}
function Assert-Preserved($Before,$After,[string[]]$Except = @()) {
    foreach ($key in $Before.Keys) {
        if ($key -in $Except) { continue }
        if (-not $After.ContainsKey($key) -or $Before[$key] -ne $After[$key]) { throw "Profile or battle changed unexpectedly: $key" }
    }
    foreach ($key in $After.Keys) {
        if (-not $Before.ContainsKey($key) -and $key -notin @('progress-v10','playtest-v10','camera-guide-seen')) { throw "Unexpected new profile entry: $key" }
    }
}
function Tap([string]$Control) {
    $point = $controls.$Control
    if (-not $point) { throw "Unknown Canvas control: $Control" }
    Adb @('shell','input','tap',"$($point[0])","$($point[1])") | Out-Null
    Start-Sleep -Milliseconds 200
}
function Native-Tree {
    Adb @('shell','uiautomator','dump','/sdcard/frontline-code-ui.xml') | Out-Null
    [xml]$tree = (Adb @('shell','cat','/sdcard/frontline-code-ui.xml')) -join "`n"
    return $tree
}
function Tap-Native([string]$Id) {
    $tree = Native-Tree
    $node = $tree.SelectSingleNode("//node[@resource-id='$Id']")
    if (-not $node) { throw "Native control is not visible: $Id" }
    $bounds = [regex]::Match($node.bounds,'^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $bounds.Success) { throw 'Invalid native control bounds.' }
    $x = [int](([int]$bounds.Groups[1].Value+[int]$bounds.Groups[3].Value)/2)
    $y = [int](([int]$bounds.Groups[2].Value+[int]$bounds.Groups[4].Value)/2)
    Adb @('shell','input','tap',"$x","$y") | Out-Null
    Start-Sleep -Milliseconds 250
}
function Screenshot([string]$Name) {
    Start-Sleep -Milliseconds 300
    Adb @('shell','screencap','-p',"/sdcard/$Name.png") | Out-Null
    Adb @('pull',"/sdcard/$Name.png",(Join-Path $output "$Name.png")) | Out-Null
}
New-Item -ItemType Directory -Force -Path $output | Out-Null
$controls = (& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.DeviceControls') | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Cannot calculate Canvas controls.' }
Adb @('logcat','-c') | Out-Null
try {
Adb @('shell','wm','size','720x1280') | Out-Null
Adb @('shell','wm','density','320') | Out-Null
Adb @('shell','cmd','connectivity','airplane-mode','enable') | Out-Null
# Only the named emulator is modified; physical phones are never selected.
if (-not (Test-Path -LiteralPath $previous)) { throw 'Put the actual V6 APK in build/FrontlineV6.apk for the upgrade check.' }
Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
Adb @('install','-r','-d',$previous) | Out-Null
Adb @('shell','pm','clear','com.frontline.offline') | Out-Null
Start-App
# The baseline V6 menu has its original layout; subsequent taps use current Canvas coordinates.
Adb @('shell','input','tap','360','509') | Out-Null
Tap 'tutorial-tutorial_skip'
Adb @('shell','input','keyevent','4') | Out-Null
Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
$fixture = Join-Path $output 'v7-upgrade-fixture.xml'
$raw = (Adb @('shell','run-as','com.frontline.offline','cat','shared_prefs/frontline-v1.xml')) -join "`n"
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($raw))
& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.VictoryFixture' $encoded $fixture '--v5'
if ($LASTEXITCODE -ne 0) { throw 'Cannot create upgrade fixture.' }
Adb @('push',$fixture,'/data/local/tmp/frontline-v7-fixture.xml') | Out-Null
Adb @('shell','run-as','com.frontline.offline','cp','/data/local/tmp/frontline-v7-fixture.xml','shared_prefs/frontline-v1.xml') | Out-Null
Start-App
Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
$before = Preferences
Adb @('install','-r',$apk) | Out-Null
Start-App
$after = Preferences
Assert-Preserved $before $after @('battle')
& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.SaveProbe' $before['battle'] '--compare' $after['battle']
if ($LASTEXITCODE -ne 0) { throw 'V6 battle migration changed retained state.' }
if ($after['unlocked'] -ne '30' -or $after['best-29'] -ne '2700' -or $after['music'] -ne 'false') { throw 'V6 upgrade fixture was not preserved.' }
Tap 'menu-settings'
Screenshot 'settings-v7'
Tap 'settings-unlock_code'
Tap-Native 'android:id/edit'
Screenshot 'unlock-code-dialog'
Tap-Native 'android:id/button1'
Assert-Preserved $after (Preferences)
Adb @('shell','input','text','11111') | Out-Null
Tap-Native 'android:id/button1'
Screenshot 'unlock-code-invalid'
if ((Native-Tree).OuterXml -notmatch 'Invalid code') { throw 'Invalid-code error is not visible.' }
Assert-Preserved $after (Preferences)
Tap-Native 'android:id/button2'
Assert-Preserved $after (Preferences)
Tap 'settings-unlock_code'
Adb @('shell','input','text','12345') | Out-Null
Adb @('shell','input','keyevent','66') | Out-Null
Start-Sleep -Milliseconds 400
$unlocked = Preferences
if ($unlocked['unlocked'] -ne '59') { throw 'Correct code did not unlock all sectors through the native keyboard action.' }
Assert-Preserved $after $unlocked @('unlocked')
if ((Native-Tree).SelectSingleNode("//node[@resource-id='android:id/edit']")) { throw 'Successful code did not dismiss the dialog.' }
Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
Start-App
Assert-Preserved $unlocked (Preferences)
Tap 'menu-settings'; Tap 'settings-unlock_code'
Adb @('shell','input','text','12345') | Out-Null
Tap-Native 'android:id/button1'
Assert-Preserved $unlocked (Preferences)
Tap 'settings-back'; Tap 'menu-sectors'
foreach ($i in 1..5) { Tap 'campaign-chapter_next' }
Screenshot 'unlocked-final-chapter-v7'
Tap 'campaign-final-row'
if ((Preferences)['selected-sector'] -ne '59') { throw 'Unlocked final sector could not be selected.' }
Tap 'menu-play'
Tap 'brief-begin_attempt'; Tap 'confirm-confirm_replace'
Tap 'camera-camera_ready'
Adb @('shell','input','keyevent','4') | Out-Null
Start-Sleep -Milliseconds 250
$battle = (Preferences)['battle']
& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.SaveProbe' $battle '--level' '59'
if ($LASTEXITCODE -ne 0) { throw 'Final sector was not playable after unlocking.' }
Adb @('shell','input','keyevent','4') | Out-Null
Tap 'battle-settings'
Tap 'settings-unlock_code'
Tap-Native 'android:id/button2'
Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
Adb @('shell','wm','size','480x800') | Out-Null
Adb @('shell','wm','density','240') | Out-Null
Start-App
$controls = (& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.DeviceControls' '620' '480') | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Cannot calculate small-screen controls.' }
Tap 'menu-settings'
Screenshot 'settings-v7-small'
Tap 'settings-unlock_code'
Adb @('shell','input','text','12345') | Out-Null
Screenshot 'unlock-code-small'
Tap-Native 'android:id/button1'
if ((Preferences)['unlocked'] -ne '59') { throw 'Small-screen unlock did not persist.' }
    $crashes = Adb @('logcat','-d','-s','AndroidRuntime:E')
    if ($crashes -match 'FATAL EXCEPTION') { throw 'Android crashed during V7 verification.' }
    Write-Host 'PASS: V6-to-current binary upgrade, empty/wrong/cancelled codes, keyboard submit, button submit, persistent all-sector unlock, unchanged scores/preferences/battle, playable Sector 60, and small-phone dialog.'
} finally {
    Adb @('shell','wm','size','720x1280') | Out-Null
    Adb @('shell','wm','density','320') | Out-Null
    Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
}
