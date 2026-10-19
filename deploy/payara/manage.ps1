param(
    [Parameter(Mandatory = $true)][ValidateSet('start','deploy','stop','version')][string]$Action,
    [string]$PayaraHome,
    [string]$DomainsDirectory,
    [string]$DomainName = 'agency',
    [int]$AdminPort = 18048
)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17.' }
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $PayaraHome) { $PayaraHome = $env:PAYARA_HOME }
if (-not $DomainsDirectory) { $DomainsDirectory = $env:PAYARA_DOMAINS }
if (-not $PayaraHome) { $PayaraHome = Join-Path $root '.runtime/payara-6.2025.10/payara6' }
if (-not $DomainsDirectory) { $DomainsDirectory = Join-Path $root '.runtime/payara-domains' }
$cli = Join-Path $PayaraHome 'glassfish/lib/client/appserver-cli.jar'
$java = Join-Path $env:JAVA_HOME 'bin/java.exe'
$env:AS_JAVA = $env:JAVA_HOME
$passwordFile = Join-Path $DomainsDirectory "$DomainName-private/asadmin.password"
$arguments = @('-Dfile.encoding=UTF-8','-Duser.language=en','-Duser.country=US','-jar',$cli)
switch ($Action) {
    'start' {
        # Give the CLI its own hidden console: the detached server must not inherit
        # PowerShell's native-command pipes. Server output is in domain/logs/server.log.
        $quotedCli = '"' + $cli + '"'
        $quotedPassword = '"' + $passwordFile + '"'
        $quotedDomains = '"' + [IO.Path]::GetFullPath($DomainsDirectory).TrimEnd([char[]]@('\','/')) + '"'
        $startArgs = @('-Dfile.encoding=UTF-8','-Duser.language=en','-Duser.country=US','-jar',$quotedCli,'--host','127.0.0.1','--port',"$AdminPort",'--passwordfile',$quotedPassword,'start-domain','--domaindir',$quotedDomains,$DomainName)
        $process = Start-Process -FilePath $java -ArgumentList $startArgs -WindowStyle Hidden -PassThru
        $process.Handle | Out-Null
        $deadline = [DateTime]::UtcNow.AddSeconds(120)
        while (-not $process.WaitForExit(1000)) {
            if ([DateTime]::UtcNow -gt $deadline) { throw 'CLI startup timed out; inspect domain/logs/server.log. The server may already be running.' }
        }
        if ($process.ExitCode -ne 0) { throw 'Payara start failed; inspect domain server.log.' }
        Write-Host 'Payara start completed; see domain/logs/server.log.'
        return
    }
    'stop' { & $java @arguments --passwordfile $passwordFile stop-domain --domaindir $DomainsDirectory $DomainName }
    'deploy' { & $java @arguments --host 127.0.0.1 --port $AdminPort --user admin --passwordfile $passwordFile deploy --force true --name agency-service --contextroot / --virtualservers server (Join-Path $root 'agency-service/target/agency-service.war') }
    'version' { & $java @arguments version --local true }
}
if ($LASTEXITCODE -ne 0) { throw "Payara $Action failed." }
