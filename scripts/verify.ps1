[CmdletBinding()]
param(
    [switch]$Connected
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $tasks = @(
        'clean',
        'testDebugUnitTest',
        'lintDebug',
        'lintRelease',
        'assembleDebugAndroidTest',
        'assembleRelease',
        'bundleRelease'
    )
    if ($Connected) {
        $tasks += 'connectedDebugAndroidTest'
    }

    & "$projectRoot\gradlew.bat" @tasks '--no-daemon'
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle verification failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
