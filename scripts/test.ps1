$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jdkHome = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.toolchain') -Directory -Filter 'jdk-*' | Select-Object -First 1
if (-not $jdkHome) { throw 'Java 17 is required. Run scripts/setup-android.ps1 first.' }
$javac = Join-Path $jdkHome.FullName 'bin\javac.exe'
$java = Join-Path $jdkHome.FullName 'bin\java.exe'
$classes = Join-Path $projectRoot 'build\tests'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sourceRoot = Join-Path $projectRoot 'app\src\main\java\com\frontline\offline'
$sources = @((Join-Path $sourceRoot 'GameModel.java'),(Join-Path $sourceRoot 'GameScene.java'),(Join-Path $sourceRoot 'Campaign.java'))
$sources += (Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests'),(Join-Path $projectRoot 'tools') -Recurse -Filter '*.java').FullName
& $javac '-encoding' 'UTF-8' '-d' $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
& $java '-cp' $classes 'com.frontline.offline.GameModelTest'
if ($LASTEXITCODE -ne 0) { throw 'Battle tests failed.' }
& $java '-Djava.awt.headless=true' '-cp' $classes 'com.frontline.offline.RenderPreview' (Join-Path $projectRoot 'build\previews')
if ($LASTEXITCODE -ne 0) { throw 'Preview rendering failed.' }
