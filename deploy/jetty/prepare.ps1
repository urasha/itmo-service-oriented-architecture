param(
    [string]$JettyHome,
    [string]$JettyBase,
    [ValidateRange(1024, 65535)][int]$Port = 8443,
    [string]$BindAddress = '127.0.0.1',
    [string]$San = 'dns:localhost,ip:127.0.0.1',
    [string]$FlatsPublicUrl = '',
    [string]$AgencyPublicUrl = 'https://localhost:8444'
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17+.' }
$version = ((Get-Content (Join-Path $projectRoot 'deploy/versions.env') | Where-Object { $_ -match '^JETTY_VERSION=' }) -split '=', 2)[1]
$runtimeDir = Join-Path $projectRoot '.runtime'
[IO.Directory]::CreateDirectory($runtimeDir) | Out-Null
if (-not $JettyHome) {
    $JettyHome = Join-Path $runtimeDir "jetty-home-$version"
    if (-not (Test-Path -LiteralPath (Join-Path $JettyHome 'start.jar'))) {
        $archive = Join-Path $runtimeDir "jetty-home-$version.zip"
        $url = "https://repo.maven.apache.org/maven2/org/eclipse/jetty/jetty-home/$version/jetty-home-$version.zip"
        Invoke-WebRequest -UseBasicParsing $url -OutFile $archive
        $expected = ((Invoke-WebRequest -UseBasicParsing "$url.sha512").Content.Trim() -split '\s+')[0]
        if ((Get-FileHash -Algorithm SHA512 -LiteralPath $archive).Hash -ne $expected) { throw 'Jetty archive checksum mismatch.' }
        Expand-Archive -LiteralPath $archive -DestinationPath $runtimeDir -Force
    }
}
$JettyHome = (Resolve-Path -LiteralPath $JettyHome).Path
if (-not $JettyBase) { $JettyBase = Join-Path $runtimeDir 'jetty-base' }
[IO.Directory]::CreateDirectory($JettyBase) | Out-Null
$JettyBase = (Resolve-Path -LiteralPath $JettyBase).Path
foreach ($dir in @('start.d', 'etc', 'modules', 'webapps', 'lib/ext')) { [IO.Directory]::CreateDirectory((Join-Path $JettyBase $dir)) | Out-Null }
foreach ($dir in @('start.d', 'etc', 'modules', 'webapps')) { Copy-Item -Path (Join-Path $PSScriptRoot "$dir/*") -Destination (Join-Path $JettyBase $dir) -Force }
if ((Test-Path -LiteralPath (Join-Path $JettyBase 'start.d/http.ini')) -or (Test-Path -LiteralPath (Join-Path $JettyBase 'start.ini'))) { throw 'Use a dedicated Jetty base without existing HTTP configuration.' }
$utf8 = New-Object Text.UTF8Encoding($false)
$httpsIni = "--module=https`njetty.ssl.host=$BindAddress`njetty.ssl.port=$Port`njetty.httpConfig.requestHeaderSize=65536`njetty.httpConfig.sendServerVersion=true`n"
[IO.File]::WriteAllText((Join-Path $JettyBase 'start.d/https.ini'), $httpsIni, $utf8)
foreach ($publicUrl in @($FlatsPublicUrl, $AgencyPublicUrl)) {
    if ($publicUrl -match '[\r\n]') { throw 'Public URL must be a single line.' }
    if ($publicUrl -and ([Uri]$publicUrl).Scheme -ne 'https') { throw 'Public URLs must use HTTPS.' }
}
[IO.File]::WriteAllText((Join-Path $JettyBase 'start.d/client.ini'), "-Dsoa.flatsPublicBaseUrl=$FlatsPublicUrl`n-Dsoa.agencyPublicBaseUrl=$AgencyPublicUrl`n", $utf8)
& (Join-Path $projectRoot 'deploy/tls/generate.ps1') -OutputDirectory (Join-Path $JettyBase 'etc') -San $San
$password = [IO.File]::ReadAllText((Join-Path $JettyBase 'etc/keystore.password')).Trim()
[IO.File]::WriteAllText((Join-Path $JettyBase 'start.d/local-secrets.ini'), "jetty.sslContext.keyStorePassword=$password`n", $utf8)
Copy-Item -LiteralPath (Join-Path $projectRoot 'flats-service/target/flats-service.war') -Destination (Join-Path $JettyBase 'webapps/ROOT.war') -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'flats-service/target/flats-service-jetty-errors.jar') -Destination (Join-Path $JettyBase 'lib/ext/flats-service-jetty-errors.jar') -Force
Write-Host "Jetty home: $JettyHome"
Write-Host "Jetty base: $JettyBase"
Write-Host "HTTPS: https://localhost:$Port/flats (use the SAN hostname configured above)"
Write-Host 'Start with deploy/jetty/start.ps1; trust etc/flats.crt on the client.'
