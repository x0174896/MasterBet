[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME, [switch]$UseWindowsTrustStore)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$localDir = Join-Path $projectRoot '.local'
$jarPath = Join-Path $projectRoot 'backend\target\masterbet-backend-0.1.0.jar'
$ngPath = Join-Path $projectRoot 'frontend\node_modules\@angular\cli\bin\ng.js'
if (!$JavaHome) { throw 'Impostare JAVA_HOME al JDK 25 o passare -JavaHome.' }
$javaPath = Join-Path $JavaHome 'bin\java.exe'
if (!(Test-Path -LiteralPath $javaPath) -or !(Test-Path -LiteralPath $jarPath) -or !(Test-Path -LiteralPath $ngPath)) { throw 'JDK, pacchetto backend o dipendenze frontend mancanti. Consultare setup.md.' }
foreach ($portNumber in @(8081,4201)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $portNumber -ErrorAction SilentlyContinue) { throw "Porta $portNumber occupata: nessun processo esistente viene arrestato." }
}
New-Item -ItemType Directory -Force -Path $localDir | Out-Null
$nodePath = (Get-Command node.exe -ErrorAction Stop).Source
$javaArguments = @()
if ($UseWindowsTrustStore) { $javaArguments += @('-Djavax.net.ssl.trustStoreType=Windows-ROOT','-Djavax.net.ssl.trustStore=NONE') }
$javaArguments += @('-jar',('"'+$jarPath+'"'))
$backendProcess = Start-Process -FilePath $javaPath -ArgumentList $javaArguments -WorkingDirectory (Join-Path $projectRoot 'backend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $localDir 'backend.out.log') -RedirectStandardError (Join-Path $localDir 'backend.err.log')
$backendRecord = @{ processId=$backendProcess.Id; executable=$javaPath; marker=$jarPath; startedAt=$backendProcess.StartTime.ToUniversalTime().ToString('o') }
$backendRecord | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $localDir 'backend-process.json') -Encoding utf8
$frontendProcess = Start-Process -FilePath $nodePath -ArgumentList @(('"'+$ngPath+'"'),'serve','--host','127.0.0.1','--port','4201','--proxy-config','proxy.conf.json') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $localDir 'frontend.out.log') -RedirectStandardError (Join-Path $localDir 'frontend.err.log')
@{ processId=$frontendProcess.Id; executable=$nodePath; marker=$ngPath; startedAt=$frontendProcess.StartTime.ToUniversalTime().ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $localDir 'frontend-process.json') -Encoding utf8
Write-Output "MasterBet avviato: backend PID $($backendProcess.Id), frontend PID $($frontendProcess.Id)."
Write-Output 'Interfaccia: http://127.0.0.1:4201 — API: http://127.0.0.1:8081/api/health'
Write-Output 'Log nella cartella .local. Arresto: .\scripts\stop-local.ps1'
