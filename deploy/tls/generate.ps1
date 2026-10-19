param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$San = 'dns:localhost,ip:127.0.0.1',
    [string]$Alias = 'flats'
)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 17+ installation.' }
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$store = Join-Path $OutputDirectory 'keystore.p12'
$passwordFile = Join-Path $OutputDirectory 'keystore.password'
$certificate = Join-Path $OutputDirectory "$Alias.crt"
if ((Test-Path -LiteralPath $store) -or (Test-Path -LiteralPath $passwordFile)) {
    if (-not ((Test-Path -LiteralPath $store) -and (Test-Path -LiteralPath $passwordFile) -and (Test-Path -LiteralPath $certificate))) {
        throw 'Incomplete TLS directory. Use a new directory; existing keys are never overwritten.'
    }
    Write-Host "Reusing certificate: $certificate"
    return
}
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
[IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($bytes), [Text.Encoding]::ASCII)
$distinguishedName = if ($Alias -eq 'flats') { 'CN=Flat Collection Service' } else { "CN=$Alias Service" }
& $keytool -genkeypair -alias $Alias -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 365 -storetype PKCS12 -keystore $store -storepass:file $passwordFile -keypass:file $passwordFile -dname $distinguishedName -ext "SAN=$San" -ext 'EKU=serverAuth' -ext 'KU=digitalSignature,keyEncipherment' -noprompt
if ($LASTEXITCODE -ne 0) { throw 'keytool generation failed.' }
& $keytool -exportcert -rfc -alias $Alias -keystore $store -storepass:file $passwordFile -file $certificate
if ($LASTEXITCODE -ne 0) { throw 'keytool export failed.' }
Write-Host "Public certificate: $certificate"
