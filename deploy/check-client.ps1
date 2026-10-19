param(
    [string]$FlatsBaseUrl = 'https://localhost:8443',
    [string]$AgencyBaseUrl = 'https://localhost:8444',
    [string]$FlatsCertificate,
    [Parameter(Mandatory = $true)][string]$AgencyCertificate
)
$ErrorActionPreference = 'Stop'
$projectDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $FlatsCertificate) { $FlatsCertificate = Join-Path $projectDirectory '.runtime/jetty-base/etc/flats.crt' }
if (-not (Test-Path -LiteralPath (Join-Path $projectDirectory '.runtime/browser-check/node_modules/playwright/index.mjs'))) {
    throw 'Install test-only Playwright: npm.cmd install --prefix .runtime/browser-check --no-audit --no-fund playwright@1.64.1'
}
# Real browser trust, scoped to the invoking user. Remove only roots added by this run.
$store = New-Object Security.Cryptography.X509Certificates.X509Store('Root','CurrentUser')
$added = New-Object 'Collections.Generic.List[Security.Cryptography.X509Certificates.X509Certificate2]'
$oldFlats = $env:FLATS_BASE_URL
$oldAgency = $env:AGENCY_BASE_URL
try {
    foreach ($certificatePath in @($FlatsCertificate, $AgencyCertificate)) {
        $certificate = [Security.Cryptography.X509Certificates.X509Certificate2]::new((Resolve-Path -LiteralPath $certificatePath).Path)
        $store.Open([Security.Cryptography.X509Certificates.OpenFlags]::ReadOnly)
        $existing = $store.Certificates.Find([Security.Cryptography.X509Certificates.X509FindType]::FindByThumbprint, $certificate.Thumbprint, $false)
        $store.Close()
        if ($existing.Count -eq 0) {
            & certutil.exe -user -f -addstore Root $certificatePath | Out-Null
            if ($LASTEXITCODE -ne 0) { throw 'Cannot trust the local test certificate.' }
            $added.Add($certificate)
        }
    }
    $env:FLATS_BASE_URL = $FlatsBaseUrl
    $env:AGENCY_BASE_URL = $AgencyBaseUrl
    Push-Location $projectDirectory
    try { & node client/test/browser.mjs; if ($LASTEXITCODE -ne 0) { throw 'Browser acceptance failed.' } }
    finally { Pop-Location }
} finally {
    $cleanupFailures = @()
    foreach ($certificate in $added) {
        try {
            & certutil.exe -user -f -delstore Root $certificate.Thumbprint | Out-Null
            if ($LASTEXITCODE -ne 0) { $cleanupFailures += $certificate.Thumbprint }
        } catch { $cleanupFailures += $certificate.Thumbprint }
    }
    $store.Close()
    $env:FLATS_BASE_URL = $oldFlats
    $env:AGENCY_BASE_URL = $oldAgency
    if ($cleanupFailures.Count -gt 0) { throw "Cannot remove temporary test roots: $($cleanupFailures -join ', ')" }
}
