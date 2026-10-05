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
    Start-Sleep -Milliseconds 300
    Invoke-Adb @('shell','screencap','-p',"/sdcard/$Name.png") | Out-Null
    Invoke-Adb @('pull',"/sdcard/$Name.png",(Join-Path $outputRoot "$Name.png")) | Out-Null
}
function Read-Preferences {
    [xml]$preferences = (Invoke-Adb @('shell','run-as','com.frontline.offline','cat','shared_prefs/frontline-v1.xml')) -join "`n"
    return $preferences
}
function Load-Fixture([string]$Mode) {
    Invoke-Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
    $path = Join-Path $outputRoot "campaign$Mode.xml"
    $inputSave = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes((Read-Preferences).OuterXml))
    & $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.VictoryFixture' $inputSave $path $Mode
    if ($LASTEXITCODE -ne 0) { throw "Fixture generation failed: $Mode" }
    Invoke-Adb @('push',$path,'/data/local/tmp/frontline-campaign.xml') | Out-Null
    Invoke-Adb @('shell','run-as','com.frontline.offline','cp','/data/local/tmp/frontline-campaign.xml','shared_prefs/frontline-v1.xml') | Out-Null
}
function Start-App {
    Invoke-Adb @('shell','am','start','-W','-n','com.frontline.offline/.MainActivity') | Out-Null
    Start-Sleep -Milliseconds 1800
}
function Verify-Level([int]$Level) {
    $save = ((Read-Preferences).map.string | Where-Object name -eq 'battle').InnerText
    & $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.SaveProbe' $save '--level' "$Level"
    if ($LASTEXITCODE -ne 0) { throw "Wrong native campaign sector: $Level" }
}
function Verify-Rule([string]$Rule) {
    $save = ((Read-Preferences).map.string | Where-Object name -eq 'battle').InnerText
    & $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.SaveProbe' $save $Rule
    if ($LASTEXITCODE -ne 0) { throw "Native V5 rule failed: $Rule" }
}
function Verify-Music([string]$State) {
    $events = Invoke-Adb @('logcat','-d','-s','FrontlineMusic:D')
    $last = $events | Where-Object { $_ -match 'PLAYING|PAUSED|FAILED' } | Select-Object -Last 1
    if ($last -notmatch "$State$") { throw "Expected music $State, got: $last" }
}
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$controls = (& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.DeviceControls') | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Could not calculate scene control positions.' }
function Tap([string]$Control) {
    $point = $controls.$Control
    Invoke-Adb @('shell','input','tap',"$($point[0])","$($point[1])") | Out-Null
    Start-Sleep -Milliseconds 200
}
Invoke-Adb @('logcat','-c') | Out-Null
Invoke-Adb @('shell','pm','clear','com.frontline.offline') | Out-Null
Invoke-Adb @('shell','cmd','connectivity','airplane-mode','enable') | Out-Null
Invoke-Adb @('shell','am','start','-W','-n','com.frontline.offline/.MainActivity') | Out-Null
Screenshot 'android-splash'
Start-Sleep -Milliseconds 1800
Screenshot 'offline-launch'
if (((Read-Preferences).map.boolean | Where-Object name -eq 'music').value -ne 'true') { throw 'Music did not default on.' }
Verify-Music 'PLAYING'
Tap 'fresh-play'
Screenshot 'android-tutorial-1'
Tap 'tutorial-tutorial_next'
Screenshot 'android-tutorial-2'
Tap 'tutorial-tutorial_prev'
Screenshot 'android-tutorial-previous'
Tap 'tutorial-tutorial_next'
$from = $controls.'practice-from'; $to = $controls.'practice-to'
Invoke-Adb @('shell','input','swipe',"$($from[0])","$($from[1])","$($to[0])","$($to[1])",'220') | Out-Null
Screenshot 'android-tutorial-practice'
Tap 'tutorial-tutorial_next'
Screenshot 'android-tutorial-3'
Tap 'tutorial-demo_half'
Screenshot 'android-tutorial-half'
Tap 'tutorial-demo_all'
Screenshot 'android-tutorial-all'
Tap 'tutorial-demo_quarter'
Tap 'tutorial-tutorial_next'
Screenshot 'android-tutorial-4'
Tap 'tutorial-tutorial_next'
Tap 'battle-quarter'
$from = $controls.'swipe-from'; $to = $controls.'swipe-to'
Invoke-Adb @('shell','input','swipe',"$($from[0])","$($from[1])","$($to[0])","$($to[1])",'220') | Out-Null
Start-Sleep -Milliseconds 250
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Screenshot 'android-pause'
Tap 'pause-menu'
Screenshot 'android-main-menu'
Invoke-Adb @('shell','input','keyevent','3') | Out-Null
Start-Sleep -Milliseconds 350
Verify-Music 'PAUSED'
$saved = Read-Preferences
$battle = ($saved.map.string | Where-Object name -eq 'battle').InnerText
& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.SaveProbe' $battle '8'
if ($LASTEXITCODE -ne 0) { throw 'Android touch dispatch verification failed.' }
Invoke-Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
Invoke-Adb @('shell','am','start','-W','-n','com.frontline.offline/.MainActivity') | Out-Null
Start-Sleep -Milliseconds 1800
Screenshot 'android-restored'
Verify-Music 'PLAYING'
$restored = Read-Preferences
$restoredBattle = ($restored.map.string | Where-Object name -eq 'battle').InnerText
if ($battle -ne $restoredBattle) { throw 'Restored battle state changed in main menu.' }
Tap 'menu-sectors'
Screenshot 'android-sectors'
Tap 'locked-sector'
Screenshot 'android-sector-still-locked'
if ((Get-FileHash (Join-Path $outputRoot 'android-sectors.png')).Hash -ne (Get-FileHash (Join-Path $outputRoot 'android-sector-still-locked.png')).Hash) {
    throw 'Locked sector tap changed the sector screen.'
}
Tap 'sectors-back'
Tap 'menu-tutorial'
Tap 'tutorial-tutorial_skip'
Tap 'menu-settings'
Screenshot 'android-settings'
Tap 'settings-music'
Verify-Music 'PAUSED'
Tap 'settings-sound'
Tap 'settings-difficulty_2'
Start-Sleep -Milliseconds 350
$settings = Read-Preferences
$difficulty = ($settings.map.int | Where-Object name -eq 'difficulty').value
$sound = ($settings.map.boolean | Where-Object name -eq 'sound').value
if ($difficulty -ne '2' -or $sound -ne 'false') { throw 'Android settings controls did not persist.' }
if (($settings.map.boolean | Where-Object name -eq 'music').value -ne 'false') { throw 'Music-off preference did not persist.' }
if (($settings.map.boolean | Where-Object name -eq 'tutorial-seen').value -ne 'true') { throw 'Tutorial completion did not persist.' }
Tap 'settings-back'
Tap 'menu-resume'
Start-Sleep -Milliseconds 150
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Screenshot 'android-resumed-pause'
Tap 'pause-menu'
$beforeUpdate = (Read-Preferences).OuterXml
Invoke-Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
Invoke-Adb @('install','-r',(Join-Path $projectRoot 'build\Frontline-debug.apk')) | Out-Null
Invoke-Adb @('shell','am','start','-W','-n','com.frontline.offline/.MainActivity') | Out-Null
Start-Sleep -Milliseconds 1800
Verify-Music 'PAUSED'
if ($beforeUpdate -ne (Read-Preferences).OuterXml) { throw 'Installing an update changed local preferences.' }
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
$foreground = Invoke-Adb @('shell','dumpsys','activity','activities')
if ($foreground -match 'mResumedActivity:.*com.frontline.offline') { throw 'Back from main menu did not quit app.' }
# Inject only into the named emulator's test app, never a connected physical phone.
Invoke-Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
$fixturePath = Join-Path $outputRoot 'victory-fixture.xml'
$fixtureInput = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes((Read-Preferences).OuterXml))
& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.VictoryFixture' $fixtureInput $fixturePath
if ($LASTEXITCODE -ne 0) { throw 'Victory fixture generation failed.' }
Invoke-Adb @('push',$fixturePath,'/data/local/tmp/frontline-victory.xml') | Out-Null
Invoke-Adb @('shell','run-as','com.frontline.offline','cp','/data/local/tmp/frontline-victory.xml','shared_prefs/frontline-v1.xml') | Out-Null
Invoke-Adb @('shell','am','start','-W','-n','com.frontline.offline/.MainActivity') | Out-Null
Start-Sleep -Milliseconds 1800
Tap 'menu-resume'
Start-Sleep -Milliseconds 350
Screenshot 'android-victory'
$won = Read-Preferences
if (($won.map.int | Where-Object name -eq 'unlocked').value -ne '1' -or ($won.map.int | Where-Object name -eq 'wins').value -ne '1') {
    throw 'Native victory failed to unlock the next sector.'
}
Tap 'result-menu'
Tap 'cleared-menu-sectors'
Screenshot 'android-cleared-sectors'
Tap 'cleared-sectors-level_1'
Screenshot 'android-selected-sector'
if (((Read-Preferences).map.int | Where-Object name -eq 'selected-sector').value -ne '1') { throw 'Selected sector did not persist.' }
Tap 'cleared-menu-play'
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Screenshot 'android-selected-battle'
$selectedBattle = ((Read-Preferences).map.string | Where-Object name -eq 'battle').InnerText
& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.SaveProbe' $selectedBattle '--level' '1'
if ($LASTEXITCODE -ne 0) { throw 'Selected sector did not launch.' }
Tap 'pause-menu'
$fixturePath = Join-Path $outputRoot 'ai-fixture.xml'
Invoke-Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
$fixtureInput = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes((Read-Preferences).OuterXml))
& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.VictoryFixture' $fixtureInput $fixturePath '--ai'
if ($LASTEXITCODE -ne 0) { throw 'AI fixture generation failed.' }
Invoke-Adb @('push',$fixturePath,'/data/local/tmp/frontline-ai.xml') | Out-Null
Invoke-Adb @('shell','run-as','com.frontline.offline','cp','/data/local/tmp/frontline-ai.xml','shared_prefs/frontline-v1.xml') | Out-Null
Invoke-Adb @('shell','am','start','-W','-n','com.frontline.offline/.MainActivity') | Out-Null
Start-Sleep -Milliseconds 1800
Tap 'menu-resume'
Start-Sleep -Seconds 7
Screenshot 'android-ai-battle'
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Tap 'pause-menu'
$aiBattle = ((Read-Preferences).map.string | Where-Object name -eq 'battle').InnerText
& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.SaveProbe' $aiBattle '--ai'
if ($LASTEXITCODE -ne 0) { throw 'Native AI reserve verification failed.' }
# Exercise an actual 0.3-to-0.4 upgrade when the previous build is available.
Load-Fixture '--legacy'
$legacyFixture = Join-Path $outputRoot 'campaign--legacy.xml'
$previousApk = Join-Path $projectRoot 'build\FrontlineV3.apk'
if (Test-Path -LiteralPath $previousApk) {
    Invoke-Adb @('install','-r','-d',$previousApk) | Out-Null
    Invoke-Adb @('push',$legacyFixture,'/data/local/tmp/frontline-legacy.xml') | Out-Null
    Invoke-Adb @('shell','run-as','com.frontline.offline','cp','/data/local/tmp/frontline-legacy.xml','shared_prefs/frontline-v1.xml') | Out-Null
    Start-App
    Screenshot 'android-legacy-menu'
} else {
    Write-Host 'Previous APK unavailable; verifying legacy save migration without a binary downgrade.'
    Start-App
}
$legacy = Read-Preferences
Invoke-Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
Invoke-Adb @('install','-r',(Join-Path $projectRoot 'build\Frontline-debug.apk')) | Out-Null
Start-App
Screenshot 'android-migrated-menu'
$migrated = Read-Preferences
if (($migrated.map.int | Where-Object name -eq 'unlocked').value -ne '6') { throw 'Old final clear did not unlock Sector 7.' }
foreach ($key in @('selected-sector','wins','difficulty','sound','haptics','tutorial-seen','battle')) {
    $old = $legacy.map.ChildNodes | Where-Object name -eq $key
    $new = $migrated.map.ChildNodes | Where-Object name -eq $key
    if ($old.OuterXml -ne $new.OuterXml) { throw "Migration changed preference: $key" }
}
foreach ($level in 0..5) {
    foreach ($prefix in @('best-','stars-','time-')) {
        $key = "$prefix$level"
        $old = $legacy.map.ChildNodes | Where-Object name -eq $key
        $new = $migrated.map.ChildNodes | Where-Object name -eq $key
        if ($prefix -eq 'time-') {
            $culture = [Globalization.CultureInfo]::InvariantCulture
            if ([single]::Parse($old.value,$culture) -ne [single]::Parse($new.value,$culture)) { throw "Migration changed legacy time: $key" }
        } elseif ($old.value -ne $new.value) { throw "Migration changed legacy score: $key" }
    }
}
Tap 'cleared-menu-sectors'
Screenshot 'android-chapter-1-cleared'
Tap 'campaign-chapter_next'
Screenshot 'android-chapter-2-unlocked'
Tap 'campaign-final-row'
Screenshot 'android-chapter-2-still-locked'
if ((Get-FileHash (Join-Path $outputRoot 'android-chapter-2-unlocked.png')).Hash -ne (Get-FileHash (Join-Path $outputRoot 'android-chapter-2-still-locked.png')).Hash) {
    throw 'Locked new sector was selectable after migration.'
}
Tap 'campaign-first-row'
Tap 'cleared-menu-play'
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Verify-Level 6
Tap 'pause-menu'
Load-Fixture '--campaign'
Start-App
Tap 'menu-sectors'
Screenshot 'android-chapter-3'
Tap 'campaign-chapter_prev'
Screenshot 'android-chapter-2'
Tap 'campaign-chapter_prev'
Screenshot 'android-chapter-1'
Tap 'campaign-chapter_next'
Tap 'campaign-chapter_next'
Tap 'campaign-chapter_next'
Screenshot 'android-chapter-4'
Tap 'campaign-chapter_next'
Screenshot 'android-chapter-5'
$chapterHashes = 1..5 | ForEach-Object { (Get-FileHash (Join-Path $outputRoot "android-chapter-$_.png")).Hash } | Select-Object -Unique
if ($chapterHashes.Count -ne 5) { throw 'Native campaign pages did not all render distinct content.' }
Tap 'campaign-chapter_prev'
Tap 'campaign-chapter_prev'
Tap 'campaign-first-row'
Tap 'menu-play'
Screenshot 'android-gold-faction'
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Verify-Level 12
Tap 'pause-menu'
Tap 'menu-sectors'
Tap 'campaign-chapter_next'
Tap 'campaign-first-row'
Tap 'menu-play'
Screenshot 'android-violet-faction'
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Verify-Level 18
Tap 'pause-menu'
Tap 'menu-sectors'
Tap 'campaign-chapter_next'
Tap 'campaign-final-row'
Tap 'menu-play'
Screenshot 'android-final-sector'
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Verify-Level 29
Tap 'pause-menu'
Load-Fixture '--victory'
Start-App
Tap 'menu-resume'
Start-Sleep -Milliseconds 350
Screenshot 'android-campaign-complete'
$final = Read-Preferences
$finalSave = ($final.map.string | Where-Object name -eq 'battle').InnerText
& $java '-cp' (Join-Path $projectRoot 'build\tests') 'com.frontline.offline.SaveProbe' $finalSave '--final'
if ($LASTEXITCODE -ne 0) { throw 'Native campaign completion failed.' }
if (($final.map.int | Where-Object name -eq 'unlocked').value -ne '29' -or ($final.map.int | Where-Object name -eq 'stars-29').value -ne '3') {
    throw 'Final sector clearance did not persist.'
}
Tap 'result-menu'
Tap 'cleared-menu-sectors'
Screenshot 'android-final-cleared'
# V5 mechanics run through the actual Android canvas/activity and saved battle.
Load-Fixture '--intercept'
Start-App
Tap 'menu-resume'
Start-Sleep -Milliseconds 450
Screenshot 'android-v5-intercept'
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Tap 'pause-menu'
Verify-Rule '--intercept'
Load-Fixture '--king'
Start-App
Tap 'menu-resume'
Start-Sleep -Milliseconds 250
Screenshot 'android-v5-king-boost'
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Tap 'pause-menu'
Verify-Rule '--king'
Load-Fixture '--overflow'
Start-App
Tap 'menu-resume'
Start-Sleep -Milliseconds 1400
Screenshot 'android-v5-overflow'
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Tap 'pause-menu'
Verify-Rule '--overflow'
Tap 'menu-resume'
Tap 'battle-restart'
Screenshot 'android-v5-restarted'
Invoke-Adb @('shell','input','keyevent','4') | Out-Null
Tap 'pause-menu'
Verify-Rule '--restart'
# Verify an in-progress V4 save against the real previous APK, with the new music default.
Load-Fixture '--v4'
$previousV4 = Join-Path $projectRoot 'build\FrontlineV4.apk'
if (Test-Path -LiteralPath $previousV4) { Invoke-Adb @('install','-r','-d',$previousV4) | Out-Null }
Start-App
$beforeV5 = Read-Preferences
Invoke-Adb @('shell','am','force-stop','com.frontline.offline') | Out-Null
Invoke-Adb @('install','-r',(Join-Path $projectRoot 'build\Frontline-debug.apk')) | Out-Null
Start-App
$afterV5 = Read-Preferences
foreach ($entry in $beforeV5.map.ChildNodes) {
    if ($entry -isnot [System.Xml.XmlElement]) { continue }
    $key = $entry.GetAttribute('name')
    $new = $afterV5.map.ChildNodes | Where-Object name -eq $key
    if ($entry.OuterXml -ne $new.OuterXml) { throw "V4 update changed saved preference: $key" }
}
if (($afterV5.map.boolean | Where-Object name -eq 'music').value -ne 'true') { throw 'V4 update did not enable new music by default.' }
Verify-Music 'PLAYING'
Screenshot 'android-v5-upgrade'
Tap 'menu-settings'
Tap 'settings-music'
Verify-Music 'PAUSED'
Tap 'settings-music'
Verify-Music 'PLAYING'
Tap 'settings-back'
Invoke-Adb @('shell','input','keyevent','3') | Out-Null
Start-Sleep -Milliseconds 350
Verify-Music 'PAUSED'
$errors = Invoke-Adb @('logcat','-d','-s','AndroidRuntime:E')
if ($errors -match 'FATAL EXCEPTION') { throw 'Android runtime crash detected.' }
Write-Host 'PASS: offline campaign and V4 upgrade; tutorial previous/demo controls; music playback, toggles and background pause; restart; 20v30 interception; king bonus; growth beyond 99; saves; AI reserves; no crashes.'
