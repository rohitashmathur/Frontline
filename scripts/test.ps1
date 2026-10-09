$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkHome = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.toolchain') -Directory -Filter 'jdk-*' | Select-Object -First 1
if (-not $jdkHome) { throw 'Java 17 is required. Run scripts/setup-android.ps1 first.' }
$javac = Join-Path $jdkHome.FullName 'bin\javac.exe'
$java = Join-Path $jdkHome.FullName 'bin\java.exe'
$classes = Join-Path $projectRoot 'build\tests'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sourceRoot = Join-Path $projectRoot 'app\src\main\java\com\frontline\offline'
$sources = @((Get-ChildItem -LiteralPath $sourceRoot -Filter '*.java' | Where-Object { $_.Name -notin @('MainActivity.java','BackgroundMusic.java') }).FullName)
$sources += (Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests'),(Join-Path $projectRoot 'tools') -Recurse -Filter '*.java').FullName
& $javac '-encoding' 'UTF-8' '-d' $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& $java '-cp' $classes 'com.frontline.offline.GameModelTest'
if ($LASTEXITCODE -ne 0) { throw 'Battle tests failed.' }
foreach ($test in @('V11ModelTest','V11ProgressTest','V11SceneTest','LocalizationTest','V11BalanceTest','RunStateTest','V11RunSceneTest','V11LogTest','LogisticsRoutesTest','LogisticsRecordsTest','V11LogisticsModelTest','V11LogisticsSceneTest')) {
    if (Test-Path -LiteralPath (Join-Path $classes "com\frontline\offline\$test.class")) {
        & $java '-cp' $classes "com.frontline.offline.$test"
        if ($LASTEXITCODE -ne 0) { throw "$test failed." }
    }
}
& $java '-Djava.awt.headless=true' '-cp' $classes 'com.frontline.offline.RenderPreview' (Join-Path $projectRoot 'build\previews')
if ($LASTEXITCODE -ne 0) { throw 'Preview rendering failed.' }
