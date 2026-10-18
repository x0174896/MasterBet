param(
    [Parameter(Mandatory=$true)][string]$PostgresHome,
    [switch]$ApproveTemporaryLocalAdminAccess
)
$ErrorActionPreference = 'Stop'
if (-not $ApproveTemporaryLocalAdminAccess) { throw 'Serve approvazione esplicita per l accesso amministrativo locale temporaneo.' }
$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot 'backend\.env.local'
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines($envFile)) {
    if ($line -match '^([A-Z_]+)=(.*)$') { $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'") }
}
if (-not $settings['DB_PASSWORD']) { throw 'DB_PASSWORD non configurata nel file privato.' }
$hba = Join-Path $PostgresHome 'data\pg_hba.conf'
$psql = Join-Path $PostgresHome 'bin\psql.exe'
$original = [IO.File]::ReadAllBytes($hba)
$backupDir = Join-Path $projectRoot '.local\admin'
[IO.Directory]::CreateDirectory($backupDir) | Out-Null
$backup = Join-Path $backupDir ('pg_hba.' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss') + '.backup')
[IO.File]::WriteAllBytes($backup, $original)
$temporary = [Text.Encoding]::UTF8.GetBytes("# MASTERBET TEMPORARY BOOTSTRAP`r`nhost postgres postgres 127.0.0.1/32 trust`r`n")
$process = $null
$restored = $false
try {
    [IO.File]::WriteAllBytes($hba, $temporary + $original)
    $info = [Diagnostics.ProcessStartInfo]::new($psql)
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($arg in @('-X','-w','-h','127.0.0.1','-p','5432','-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1','-A','-t','-q')) { $info.ArgumentList.Add($arg) }
    $process = [Diagnostics.Process]::Start($info)
    $process.StandardInput.WriteLine("SELECT 'MASTERBET_ADMIN_READY';")
    $process.StandardInput.Flush()
    $ready = $process.StandardOutput.ReadLineAsync()
    if (-not $ready.Wait(10000) -or $ready.Result -ne 'MASTERBET_ADMIN_READY') { throw 'Accesso amministrativo non stabilito.' }
    # Su Windows le nuove connessioni rileggono pg_hba.conf automaticamente.
    [IO.File]::WriteAllBytes($hba, $original)
    $restored = $true
    $escaped = $settings['DB_PASSWORD'].Replace("'", "''")
    $sql = @"
SET log_statement = 'none';
SET log_min_error_statement = 'panic';
DO `$guard`$ BEGIN IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='masterbet_app') OR EXISTS (SELECT 1 FROM pg_database WHERE datname='masterbet_local') THEN RAISE EXCEPTION 'Risorse MasterBet gia esistenti: interrompo senza modificarle'; END IF; END `$guard`$;
CREATE ROLE masterbet_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD '$escaped';
CREATE DATABASE masterbet_local OWNER masterbet_app;
REVOKE ALL ON DATABASE masterbet_local FROM PUBLIC;
GRANT CONNECT,TEMPORARY ON DATABASE masterbet_local TO masterbet_app;
SELECT 'MASTERBET_DATABASE_CREATED';
\q
"@
    $process.StandardInput.WriteLine($sql)
    $process.StandardInput.Close()
    if (-not $process.WaitForExit(20000)) { throw 'Timeout durante la creazione del database.' }
    $output = $process.StandardOutput.ReadToEnd()
    $errors = $process.StandardError.ReadToEnd()
    if ($process.ExitCode -ne 0 -or -not $output.Contains('MASTERBET_DATABASE_CREATED')) {
        throw 'Creazione non riuscita: verificare lo stato di masterbet_local e masterbet_app prima di riprovare. I dettagli SQL sono omessi per proteggere la password.'
    }
    # Isola questo nuovo ruolo dagli altri database e dagli accessi remoti.
    $isolation = [Text.Encoding]::UTF8.GetBytes("# MASTERBET APP ISOLATION`r`nhost masterbet_local masterbet_app 127.0.0.1/32 scram-sha-256`r`nhost all masterbet_app 0.0.0.0/0 reject`r`nhost all masterbet_app ::0/0 reject`r`n")
    [IO.File]::WriteAllBytes($hba, $isolation + $original)
    Write-Output 'Database e ruolo creati. Accesso amministrativo temporaneo rimosso. Ruolo limitato al database MasterBet da localhost.'
} finally {
    if (-not $restored) { [IO.File]::WriteAllBytes($hba, $original) }
    if ($process -and -not $process.HasExited) { $process.Kill() }
    if ($process) { $process.Dispose() }
}
