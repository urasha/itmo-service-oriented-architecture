param(
    [string]$FlatsBaseUrl = 'https://localhost:8443',
    [string]$AgencyBaseUrl = 'https://localhost:8444'
)
$ErrorActionPreference = 'Stop'
foreach ($baseUrl in @($FlatsBaseUrl, $AgencyBaseUrl)) {
    $uri = [Uri]$baseUrl
    if (-not $uri.IsAbsoluteUri -or $uri.Scheme -ne 'https' -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
        throw 'API addresses must be absolute HTTPS URLs without credentials, query or fragment.'
    }
}
$projectDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$outputDirectory = Join-Path $projectDirectory '.runtime/client-dist'
$appDirectory = Join-Path $outputDirectory 'app'
$docsDirectory = Join-Path $outputDirectory 'docs'
[IO.Directory]::CreateDirectory($appDirectory) | Out-Null
[IO.Directory]::CreateDirectory($docsDirectory) | Out-Null
# The client uses browser modules directly; no transpiler or Node runtime is needed on Helios.
Get-ChildItem (Join-Path $projectDirectory 'client') -File | Where-Object {
    $_.Extension -in @('.html', '.css', '.js')
} | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $appDirectory -Force }
Copy-Item -Path (Join-Path $projectDirectory 'public/*') -Destination $docsDirectory -Recurse -Force
$configuration = @{ flatsBaseUrl = $FlatsBaseUrl.TrimEnd('/'); agencyBaseUrl = $AgencyBaseUrl.TrimEnd('/'); requestTimeoutMillis = 20000 }
$utf8 = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $appDirectory 'config.js'), 'window.SOA_CONFIG = ' + ($configuration | ConvertTo-Json -Compress) + ";`n", $utf8)
[IO.File]::WriteAllText((Join-Path $outputDirectory 'index.html'), '<!doctype html><html lang="ru"><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=./app/index.html"><title>SOA Lab 2</title><a href="./app/index.html">Open application</a></html>', $utf8)
Write-Host "Static frontend: $outputDirectory"
