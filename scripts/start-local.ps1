[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME, [switch]$UseWindowsTrustStore)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$localDir = Join-Path $projectRoot '.local'
$jarPath = Join-Path $projectRoot 'backend\target\masterbet-backend-0.1.0.jar'
$ngPath = Join-Path $projectRoot 'frontend\node_modules\@angular\cli\bin\ng.js'
foreach ($portNumber in @(8080,4200)) {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $portNumber -ErrorAction SilentlyContinue)
    if ($listeners.Count) {
        $owners = ($listeners.OwningProcess | Sort-Object -Unique) -join ', '
        throw "Porta $portNumber occupata (PID $owners). Nessun processo arrestato. Per MasterBet usare scripts\stop-local.ps1 prima di riavviare."
    }
}
foreach ($component in @('backend','frontend')) {
    $recordPath = Join-Path $localDir "$component-process.json"
    if (Test-Path -LiteralPath $recordPath) {
        $record = Get-Content -Raw -LiteralPath $recordPath | ConvertFrom-Json
        if (Get-Process -Id ([int]$record.processId) -ErrorAction SilentlyContinue) {
            throw "Esiste un processo registrato per $component. Eseguire scripts\stop-local.ps1 prima di avviare una nuova istanza."
        }
    }
}

function Test-Jdk25([string]$candidate) {
    if (!$candidate) { return $false }
    $release = Join-Path $candidate 'release'
    return (Test-Path -LiteralPath (Join-Path $candidate 'bin\javac.exe')) -and
        (Test-Path -LiteralPath $release) -and
        ((Get-Content -Raw -LiteralPath $release) -match 'JAVA_VERSION="25(?:[.+-]|")')
}
if (!(Test-Jdk25 $JavaHome)) {
    if ($PSBoundParameters.ContainsKey('JavaHome')) { throw 'Il parametro JavaHome deve indicare un JDK 25.' }
    $JavaHome = Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles 'Java') -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Jdk25 $_.FullName } | Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (!(Test-Jdk25 $JavaHome)) { throw 'JDK 25 non trovato. Impostare JAVA_HOME oppure passare -JavaHome. Consultare setup.md.' }
if (!(Test-Path -LiteralPath $ngPath)) { throw 'Dipendenze frontend mancanti: eseguire npm ci dalla cartella frontend.' }
if (!(Test-Path -LiteralPath (Join-Path $projectRoot 'backend\.env.local'))) { throw 'Configurazione backend/.env.local mancante. Consultare setup.md.' }
$javaPath = Join-Path $JavaHome 'bin\java.exe'
$nodePath = (Get-Command node.exe -ErrorAction Stop).Source
New-Item -ItemType Directory -Force -Path $localDir | Out-Null

Write-Output 'Compilazione del backend Java 25...'
$previousJavaHome = $env:JAVA_HOME
$previousMavenOpts = $env:MAVEN_OPTS
try {
    $env:JAVA_HOME = $JavaHome
    if ($UseWindowsTrustStore) { $env:MAVEN_OPTS = "$previousMavenOpts -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE" }
    Push-Location (Join-Path $projectRoot 'backend')
    try {
        # Windows PowerShell 5 tratta anche gli avvisi JVM su stderr come errori:
        # per questo comando nativo l'esito autorevole e' il codice di uscita Maven.
        try {
            $ErrorActionPreference = 'Continue'
            & .\mvnw.cmd package -DskipTests *> (Join-Path $localDir 'build-backend.log')
            $buildExit = $LASTEXITCODE
        } finally { $ErrorActionPreference = 'Stop' }
        if ($buildExit -ne 0) { throw 'Compilazione backend non riuscita. Consultare .local/build-backend.log.' }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:MAVEN_OPTS = $previousMavenOpts
}

function Wait-LocalService($process, [string]$url, [string]$label) {
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    do {
        if ($process.HasExited) { throw "$label terminato durante l'avvio. Consultare i log in .local." }
        try {
            $health = Invoke-RestMethod -Uri $url -TimeoutSec 2
            if ($health.status -eq 'UP') { return }
        } catch { }
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$label non pronto entro 60 secondi. Consultare i log in .local."
}

$javaArguments = @()
if ($UseWindowsTrustStore) { $javaArguments += @('-Djavax.net.ssl.trustStoreType=Windows-ROOT','-Djavax.net.ssl.trustStore=NONE') }
$javaArguments += @('-jar',('"'+$jarPath+'"'),'--server.port=8080')
$startedProcesses = @{}
try {
    $backendProcess = Start-Process -FilePath $javaPath -ArgumentList $javaArguments -WorkingDirectory (Join-Path $projectRoot 'backend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $localDir 'backend.out.log') -RedirectStandardError (Join-Path $localDir 'backend.err.log')
    $startedProcesses['backend'] = $backendProcess
    @{ processId=$backendProcess.Id; executable=$javaPath; marker=$jarPath; startedAt=$backendProcess.StartTime.ToUniversalTime().ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $localDir 'backend-process.json') -Encoding utf8
    Wait-LocalService $backendProcess 'http://127.0.0.1:8080/api/health' 'Backend'
    $frontendProcess = Start-Process -FilePath $nodePath -ArgumentList @(('"'+$ngPath+'"'),'serve','--host','127.0.0.1','--port','4200','--proxy-config','proxy.conf.json') -WorkingDirectory (Join-Path $projectRoot 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $localDir 'frontend.out.log') -RedirectStandardError (Join-Path $localDir 'frontend.err.log')
    $startedProcesses['frontend'] = $frontendProcess
    @{ processId=$frontendProcess.Id; executable=$nodePath; marker=$ngPath; startedAt=$frontendProcess.StartTime.ToUniversalTime().ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $localDir 'frontend-process.json') -Encoding utf8
    Wait-LocalService $frontendProcess 'http://127.0.0.1:4200/api/health' 'Frontend e proxy API'
} catch {
    # Gli oggetti Process appartengono solo a questo avvio, mai a servizi preesistenti.
    foreach ($component in $startedProcesses.Keys) {
        $started = $startedProcesses[$component]
        if (!$started.HasExited) { $started.Kill(); $started.WaitForExit() }
        Remove-Item -LiteralPath (Join-Path $localDir "$component-process.json") -ErrorAction SilentlyContinue
    }
    throw
}
Write-Output "MasterBet avviato: backend PID $($backendProcess.Id), frontend PID $($frontendProcess.Id)."
Write-Output 'Interfaccia: http://127.0.0.1:4200 - API: http://127.0.0.1:8080/api/health'
Write-Output 'Log nella cartella .local. Arresto: .\scripts\stop-local.ps1'
