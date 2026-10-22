param(
    [string]$SshUser = 's413022',
    [int]$FlatsLocalPort = 8443,
    [int]$AgencyLocalPort = 8444
)
$ErrorActionPreference = 'Stop'
Write-Host "Flats: https://localhost:$FlatsLocalPort -> Helios:32022"
Write-Host "Agency: https://localhost:$AgencyLocalPort -> Helios:32023"
Write-Host 'Keep this terminal open. Stop the tunnel with Ctrl+C.'
& ssh.exe -N -T -p 2222 -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 `
    -L "127.0.0.1:${FlatsLocalPort}:127.0.0.1:32022" `
    -L "127.0.0.1:${AgencyLocalPort}:127.0.0.1:32023" "${SshUser}@helios.cs.ifmo.ru"
exit $LASTEXITCODE
