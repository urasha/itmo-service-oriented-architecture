param(
    [string]$PayaraHome,
    [string]$DomainsDirectory,
    [string]$DomainName = 'agency',
    [int]$PortBase = 18000,
    [int]$HttpsPort = 8444,
    [string]$BindAddress = '127.0.0.1',
    [string]$San = 'dns:localhost,ip:127.0.0.1',
    [string]$FlatsInternalUrl = 'https://localhost:8443',
    [string]$FlatsPublicUrl = 'https://localhost:8443',
    [string]$AllowedOrigin = 'https://localhost:8443',
    [string]$FlatsCertificate
)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17.' }
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $PayaraHome) { $PayaraHome = $env:PAYARA_HOME }
if (-not $DomainsDirectory) { $DomainsDirectory = $env:PAYARA_DOMAINS }
if (-not $PayaraHome) { $PayaraHome = Join-Path $root '.runtime/payara-6.2025.10/payara6' }
if (-not $DomainsDirectory) { $DomainsDirectory = Join-Path $root '.runtime/payara-domains' }
if (-not $FlatsCertificate) { $FlatsCertificate = Join-Path $root '.runtime/jetty-base/etc/flats.crt' }
$cli = Join-Path $PayaraHome 'glassfish/lib/client/appserver-cli.jar'
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
if (($PayaraHome + $DomainsDirectory) -match '[^\x00-\x7F]') { throw 'On Windows use ASCII paths for PayaraHome and DomainsDirectory (Payara CLI corrupts non-ASCII paths). See docs/RUNNING.md.' }
if (-not (Test-Path -LiteralPath $cli)) { throw 'Install Payara Server 6.2025.10 and pass -PayaraHome (see docs/RUNNING.md).' }
if (-not (Test-Path -LiteralPath $FlatsCertificate)) { throw 'Prepare Jetty first or pass -FlatsCertificate.' }
if ($DomainName -notmatch '^[a-zA-Z0-9_-]+$') { throw 'DomainName must be alphanumeric.' }
[IO.Directory]::CreateDirectory($DomainsDirectory) | Out-Null
$DomainsDirectory = (Resolve-Path -LiteralPath $DomainsDirectory).Path
$privateDir = Join-Path $DomainsDirectory "$DomainName-private"
[IO.Directory]::CreateDirectory($privateDir) | Out-Null
function Protect-Directory([string]$Path) {
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetOwner($sid)
    $acl.SetAccessRuleProtection($true, $false)
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $Path -AclObject $acl
}
Protect-Directory $privateDir
$masterFile = Join-Path $privateDir 'master.password'
$asadminFile = Join-Path $privateDir 'asadmin.password'
if (-not (Test-Path -LiteralPath $asadminFile)) {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes); $master = [Convert]::ToBase64String($bytes)
        $rng.GetBytes($bytes); $admin = [Convert]::ToBase64String($bytes)
    } finally { $rng.Dispose() }
    [IO.File]::WriteAllText($masterFile, $master, [Text.Encoding]::ASCII)
    [IO.File]::WriteAllText($asadminFile, "AS_ADMIN_PASSWORD=$admin`nAS_ADMIN_MASTERPASSWORD=$master`n", [Text.Encoding]::ASCII)
    $master = $null; $admin = $null
}
$domain = Join-Path $DomainsDirectory $DomainName
$pidFile = Join-Path $domain 'config/pid'
if (Test-Path -LiteralPath $pidFile) {
    $domainPid = [int](Get-Content -LiteralPath $pidFile -Raw).Trim()
    if (Get-Process -Id $domainPid -ErrorAction SilentlyContinue) { throw 'Stop the domain before preparation.' }
}
if (-not (Test-Path -LiteralPath (Join-Path $domain 'config/domain.xml'))) {
    & $java '-Dfile.encoding=UTF-8' '-Duser.language=en' '-Duser.country=US' -jar $cli --user admin --passwordfile $asadminFile create-domain --domaindir $DomainsDirectory --portbase $PortBase --usemasterpassword true --savemasterpassword true --savelogin false $DomainName
    if ($LASTEXITCODE -ne 0) { throw 'Payara create-domain failed.' }
}
Protect-Directory $domain
$tls = Join-Path $privateDir 'tls'
& (Join-Path $root 'deploy/tls/generate.ps1') -OutputDirectory $tls -San $San -Alias agency
$appConfig = Join-Path $privateDir 'agency.properties'
# Properties uses forward slashes on Windows too; paths remain outside the WAR.
$trust = (Join-Path $tls 'flats-truststore.p12').Replace('\', '/')
$trustPass = (Join-Path $tls 'flats-truststore.password').Replace('\', '/')
$properties = "flats.internalBaseUrl=$FlatsInternalUrl`nflats.publicBaseUrl=$FlatsPublicUrl`nflats.trustStore=$trust`nflats.trustStorePasswordFile=$trustPass`nflats.connectTimeoutMillis=3000`nflats.requestTimeoutMillis=5000`ncors.allowedOrigin=$AllowedOrigin`n"
[IO.File]::WriteAllText($appConfig, $properties, (New-Object Text.UTF8Encoding($false)))
& $java (Join-Path $PSScriptRoot 'ConfigureDomain.java') $domain $tls $masterFile $HttpsPort $BindAddress $FlatsCertificate $appConfig
if ($LASTEXITCODE -ne 0) { throw 'Offline domain configuration failed.' }
Write-Host "Prepared domain: $domain"
Write-Host "Public certificate: $(Join-Path $tls 'agency.crt')"
