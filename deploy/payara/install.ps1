param([Parameter(Mandatory = $true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if ($OutputDirectory -match '[^\x00-\x7F]') { throw 'Use an ASCII path for the Payara runtime on Windows.' }
$versionLine = Get-Content (Join-Path $PSScriptRoot '../versions.env') | Where-Object { $_ -like 'PAYARA_VERSION=*' }
$version = $versionLine.Split('=',2)[1]
$payaraInstallationDirectory = Join-Path $OutputDirectory 'payara6'
if (Test-Path -LiteralPath (Join-Path $payaraInstallationDirectory 'glassfish/lib/client/appserver-cli.jar')) { Write-Host "Reusing Payara: $payaraInstallationDirectory"; return }
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$zip = Join-Path $OutputDirectory "payara-$version.zip"
$url = "https://repo.maven.apache.org/maven2/fish/payara/distributions/payara/$version/payara-$version.zip"
if (-not (Test-Path -LiteralPath $zip)) { Invoke-WebRequest -UseBasicParsing $url -OutFile $zip }
$expected = (Invoke-WebRequest -UseBasicParsing "$url.sha512").Content.Trim().Split(' ')[0]
$actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA512).Hash
if ($actual -ine $expected) { throw 'Payara SHA-512 mismatch.' }
if (Test-Path -LiteralPath $payaraInstallationDirectory) { throw 'Incomplete Payara installation; use a new OutputDirectory.' }
Expand-Archive -LiteralPath $zip -DestinationPath $OutputDirectory
Write-Host "Installed Payara ${version}: $payaraInstallationDirectory"
