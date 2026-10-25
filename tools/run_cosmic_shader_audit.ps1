# Run one prepared isolated client; never starts the player's instance. Author: NingMeng123.
param(
    [Parameter(Mandatory = $true)][ValidateSet('off', 'bsl', 'photon')][string]$Pack,
    [string]$AuditRoot = 'build/verification/cosmic-universal-20261025',
    [string]$CaptureTag = 'capture',
    [switch]$LifecycleAudit,
    [switch]$NativeLayerAudit,
    [string]$ExpectedShaderPack = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$verificationRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'build/verification'))
$auditDirectory = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $AuditRoot))
if (-not $auditDirectory.StartsWith($verificationRoot + [System.IO.Path]::DirectorySeparatorChar)) {
    throw 'The audit root must remain inside this project build/verification directory.'
}
if ($CaptureTag -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Use a simple capture folder name.' }
$clientDirectory = Join-Path $auditDirectory "$Pack/client"
if (-not (Test-Path -LiteralPath (Join-Path $clientDirectory 'config/iris.properties'))) {
    throw 'Prepare this isolated client first with prepare_cosmic_shader_audit.py.'
}
$captureDirectory = Join-Path $auditDirectory "$Pack/$CaptureTag"
if (Test-Path -LiteralPath $captureDirectory) { throw 'Use a new CaptureTag to retain earlier evidence.' }
if ($LifecycleAudit -and $NativeLayerAudit) { throw 'Choose either lifecycle or native layer audit.' }
$expected = if ($Pack -eq 'off' -and [string]::IsNullOrEmpty($ExpectedShaderPack)) { 'false' } else { 'true' }
$gradleArguments = @('runClient', '--no-configuration-cache', '--console=plain', '-I',
    (Join-Path $PSScriptRoot 'cosmic-world-audit.init.gradle'),
    "-PmodernIteClientDirectory=$clientDirectory", "-PcosmicExpected=$expected", "-PcosmicOutput=$captureDirectory")
if ($LifecycleAudit) { $gradleArguments += '-PcosmicLifecycleAudit=true' }
if ($NativeLayerAudit) { $gradleArguments += '-PcosmicNativeLayerAudit=true' }
if (-not [string]::IsNullOrEmpty($ExpectedShaderPack)) { $gradleArguments += "-PcosmicExpectedPack=$ExpectedShaderPack" }
Push-Location $projectRoot
try { & (Join-Path $projectRoot 'gradlew.bat') @gradleArguments; exit $LASTEXITCODE }
finally { Pop-Location }
