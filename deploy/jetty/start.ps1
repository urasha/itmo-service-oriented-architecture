param([string]$JettyHome, [string]$JettyBase)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17+.' }
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$version = ((Get-Content (Join-Path $projectRoot 'deploy/versions.env') | Where-Object { $_ -match '^JETTY_VERSION=' }) -split '=', 2)[1]
if (-not $JettyHome) { $JettyHome = Join-Path $projectRoot ".runtime/jetty-home-$version" }
if (-not $JettyBase) { $JettyBase = Join-Path $projectRoot '.runtime/jetty-base' }
$startJar = Join-Path (Resolve-Path -LiteralPath $JettyHome).Path 'start.jar'
Push-Location -LiteralPath $JettyBase
try {
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -Xms64m -Xmx256m '-Dfile.encoding=UTF-8' -jar $startJar
    if ($LASTEXITCODE -ne 0) { throw "Jetty exited with code $LASTEXITCODE" }
} finally { Pop-Location }
