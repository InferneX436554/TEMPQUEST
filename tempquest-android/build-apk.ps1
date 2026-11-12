$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$sdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if (Test-Path -LiteralPath $sdkPath) { $env:ANDROID_HOME = $sdkPath; $env:ANDROID_SDK_ROOT = $sdkPath }
$studioJdk = 'C:\Program Files\Android\Android Studio\jbr'
if (Test-Path -LiteralPath (Join-Path $studioJdk 'bin\java.exe')) { $env:JAVA_HOME = $studioJdk }
& (Join-Path $PSScriptRoot 'gradlew.bat') --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host ''
Write-Host "APK creado: $PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
