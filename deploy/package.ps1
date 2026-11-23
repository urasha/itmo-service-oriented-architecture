param([string]$ArchivePath)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $ArchivePath) { $ArchivePath = Join-Path $root '.runtime/soa-lab3-helios.tar.gz' }
[IO.Directory]::CreateDirectory((Split-Path ([IO.Path]::GetFullPath($ArchivePath)))) | Out-Null
$files = @(
    'flats-ejb/target/flats-ejb.jar',
    'flats-service/target/flats-service.war',
    'flats-service/target/flats-service-jetty-errors.jar',
    'agency-service/target/agency-service.jar',
    'deploy/env.example', 'deploy/versions.env', 'deploy/lab3',
    'deploy/jetty', 'deploy/tls', 'docs/HELIOS.md', 'docs/RUNNING.md'
)
& tar.exe -czf $ArchivePath -C $root @files
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed. Run mvn clean verify first.' }
Write-Host "Helios package: $ArchivePath"
