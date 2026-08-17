[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$requiredVariables = @(
    'NUMINA_KEYSTORE_PATH',
    'NUMINA_KEYSTORE_PASSWORD',
    'NUMINA_KEY_ALIAS',
    'NUMINA_KEY_PASSWORD'
)

foreach ($name in $requiredVariables) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required release signing variable is missing: $name"
    }
}

$keystore = [Environment]::GetEnvironmentVariable('NUMINA_KEYSTORE_PATH')
if (-not (Test-Path -LiteralPath $keystore -PathType Leaf)) {
    throw 'NUMINA_KEYSTORE_PATH does not point to an existing file'
}

$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & "$projectRoot\gradlew.bat" clean testDebugUnitTest lintRelease assembleRelease bundleRelease '--no-daemon'
    if ($LASTEXITCODE -ne 0) {
        throw "Signed release build failed with exit code $LASTEXITCODE"
    }

    $apk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
    $aab = Join-Path $projectRoot 'app\build\outputs\bundle\release\app-release.aab'
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf) -or
        -not (Test-Path -LiteralPath $aab -PathType Leaf)) {
        throw 'Expected signed APK or AAB was not produced'
    }

    Get-FileHash -LiteralPath $apk, $aab -Algorithm SHA256
} finally {
    Pop-Location
}
