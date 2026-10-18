[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('backend','frontend')][string]$Component,
    [Parameter(Mandatory=$true)][string]$ExecutablePath,
    [Parameter(Mandatory=$true)][string]$RunId,
    [switch]$UseWindowsTrustStore
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$localDir = Join-Path $projectRoot '.local'
$port = if ($Component -eq 'backend') { 8080 } else { 4200 }
$Host.UI.RawUI.WindowTitle = "MasterBet - $Component - porta $port"
Write-Host "MasterBet $Component - http://127.0.0.1:$port" -ForegroundColor Cyan
Write-Host 'Chiudi questa finestra per arrestare soltanto questo servizio.'
Write-Host ''
$serviceExit = 1
try {
    Add-Type -Path (Join-Path $PSScriptRoot 'ConsoleProcessJob.cs')
    [MasterBet.ConsoleProcessJob]::Attach()
    Set-Location (Join-Path $projectRoot $Component)
    $serviceArguments = @()
    if ($Component -eq 'backend') {
        if ($UseWindowsTrustStore) { $serviceArguments += @('-Djavax.net.ssl.trustStoreType=Windows-ROOT','-Djavax.net.ssl.trustStore=NONE') }
        $serviceArguments += @('-jar',(Join-Path $projectRoot 'backend\target\masterbet-backend-0.1.0.jar'),'--server.port=8080',('--logging.file.name=' + (Join-Path $localDir 'backend.log')))
    } else {
        # La prima console interattiva non deve fermarsi sulla richiesta di telemetria.
        $env:NG_CLI_ANALYTICS = 'false'
        $serviceArguments = @((Join-Path $projectRoot 'frontend\node_modules\@angular\cli\bin\ng.js'),'serve','--host','127.0.0.1','--port','4200','--proxy-config','proxy.conf.json')
    }
    # Esecuzione in primo piano: stdout/stderr e Ctrl+C restano nella console.
    # PowerShell 5 non deve trasformare gli avvisi JVM su stderr in errori fatali.
    $ErrorActionPreference = 'Continue'
    & $ExecutablePath @serviceArguments
    $serviceExit = $LASTEXITCODE
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
} finally {
    $ErrorActionPreference = 'Stop'
    @{ runId=$RunId; exitCode=$serviceExit } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $localDir "$Component-exit.json") -Encoding utf8
}
Write-Host "`n$Component terminato (codice $serviceExit). I log restano visibili."
[void](Read-Host 'Premi Invio per chiudere questa console')
exit $serviceExit
