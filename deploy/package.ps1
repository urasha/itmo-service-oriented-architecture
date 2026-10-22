param([switch]$IncludeRuntimes, [string]$ArchivePath)
$ErrorActionPreference = 'Stop'
$projectDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeDirectory = Join-Path $projectDirectory '.runtime'
$stagingDirectory = Join-Path $runtimeDirectory ('helios-package-' + [Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($stagingDirectory) | Out-Null
$files = @('flats-service/target/flats-service.war','flats-service/target/flats-service-jetty-errors.jar','agency-service/target/agency-service.war','README.md','IMPLEMENTATION_PLAN.md','deploy/versions.env','deploy/env.example')
$files += Get-ChildItem (Join-Path $projectDirectory 'deploy') -File -Recurse | Where-Object {
    $_.Extension -in @('.sh','.java','.xml','.ini','.mod','.properties') -and $_.Name -notin @('local-secrets.ini','local.env')
} | ForEach-Object { $_.FullName.Substring($projectDirectory.Length + 1) }
$files += Get-ChildItem (Join-Path $projectDirectory 'docs') -Filter *.md -File | ForEach-Object { $_.FullName.Substring($projectDirectory.Length + 1) }
foreach ($relativePath in $files) {
    $destination = Join-Path $stagingDirectory $relativePath
    [IO.Directory]::CreateDirectory((Split-Path $destination -Parent)) | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectDirectory $relativePath) -Destination $destination
}
if ($IncludeRuntimes) {
    $archives = @('jetty-home-12.0.40.zip','payara-6.2025.10.zip')
    [IO.Directory]::CreateDirectory((Join-Path $stagingDirectory 'runtimes')) | Out-Null
    foreach ($name in $archives) { Copy-Item -LiteralPath (Join-Path $runtimeDirectory $name) -Destination (Join-Path $stagingDirectory "runtimes/$name") }
}
if (-not $ArchivePath) { $ArchivePath = Join-Path $runtimeDirectory 'soa-74611-helios.zip' }
$manifest = Get-ChildItem -LiteralPath $stagingDirectory -File -Recurse | ForEach-Object {
    (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA512).Hash.ToLowerInvariant() + '  ' + $_.FullName.Substring($stagingDirectory.Length + 1).Replace('\','/')
}
[IO.File]::WriteAllText((Join-Path $stagingDirectory 'SHA512SUMS'), ($manifest -join "`n") + "`n", (New-Object Text.UTF8Encoding($false)))
Compress-Archive -Path (Join-Path $stagingDirectory '*') -DestinationPath $ArchivePath -Force
$resolvedStagingDirectory = [IO.Path]::GetFullPath($stagingDirectory)
$runtimePrefix = [IO.Path]::GetFullPath($runtimeDirectory).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (-not $resolvedStagingDirectory.StartsWith($runtimePrefix, [StringComparison]::OrdinalIgnoreCase) -or
    [IO.Path]::GetFileName($resolvedStagingDirectory) -notmatch '^helios-package-[0-9a-f]{32}$') {
    throw 'Refusing to clean a staging directory outside the dedicated runtime directory.'
}
Remove-Item -LiteralPath $resolvedStagingDirectory -Recurse -Force
Write-Host "Transfer package: $ArchivePath"
Write-Host 'No locally generated private keys, passwords or prepared domains are included.'
