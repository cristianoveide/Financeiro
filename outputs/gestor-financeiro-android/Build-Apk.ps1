$ErrorActionPreference = 'Stop'
$project = Join-Path $PSScriptRoot 'android'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$sdkManager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
$javaHome = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
$gradleVersion = '9.6.0'
$gradleRoot = Join-Path $env:LOCALAPPDATA "GestorFinanceiro\gradle-$gradleVersion"
$gradleZip = Join-Path $env:TEMP "gradle-$gradleVersion-bin.zip"
$gradleExe = Join-Path $gradleRoot 'bin\gradle.bat'

if (-not (Test-Path -LiteralPath $sdkManager)) { throw "SDK Manager não encontrado: $sdkManager" }
if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) { throw "Java do Android Studio não encontrado: $javaHome" }
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"

if (-not (Test-Path -LiteralPath (Join-Path $sdk 'platforms\android-36\android.jar'))) {
    Write-Host 'Instalando Android SDK Platform 36...'
    & $sdkManager --licenses
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível aceitar as licenças do Android SDK.' }
    & $sdkManager 'platforms;android-36' 'build-tools;36.0.0'
    if ($LASTEXITCODE -ne 0) { throw 'O SDK Manager não conseguiu instalar a plataforma Android 36.' }
}

if (-not (Test-Path -LiteralPath $gradleExe)) {
    Write-Host "Baixando Gradle $gradleVersion..."
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $gradleZip
    $gradleParent = Split-Path -Parent $gradleRoot
    New-Item -ItemType Directory -Path $gradleParent -Force | Out-Null
    Expand-Archive -LiteralPath $gradleZip -DestinationPath $gradleParent -Force
}

Push-Location $project
try {
    $buildLog = Join-Path $PSScriptRoot 'android-build.log'
    $oldErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $gradleExe --no-daemon --stacktrace --console=plain assembleDebug 2>&1 | Tee-Object -FilePath $buildLog
        $gradleExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldErrorActionPreference
    }
    if ($gradleExitCode -ne 0) { throw "A compilação Android falhou (código $gradleExitCode). Diagnóstico completo: $buildLog" }
} finally {
    Pop-Location
}
$apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
Write-Host "APK criado: $apk"
