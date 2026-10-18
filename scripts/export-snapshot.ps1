[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$PostgresHome)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines((Join-Path $projectRoot 'backend\.env.local'))) {
    if ($line -match '^([A-Z_]+)=(.*)$') { $settings[$matches[1]] = $matches[2].Trim() }
}
if ($settings['DB_URL'] -ne 'jdbc:postgresql://127.0.0.1:5432/masterbet_local' -or $settings['DB_USERNAME'] -ne 'masterbet_app') {
    throw 'Export consentito soltanto dal database MasterBet locale documentato.'
}
$oldPassword = $env:PGPASSWORD
$oldEncoding = $env:PGCLIENTENCODING
try {
    $env:PGPASSWORD = $settings['DB_PASSWORD']
    $env:PGCLIENTENCODING = 'UTF8'
    $snapshotText = & (Join-Path $PostgresHome 'bin\psql.exe') -X -w -h 127.0.0.1 -p 5432 -U $settings['DB_USERNAME'] -d masterbet_local -t -A -c "SELECT payload FROM simulation_snapshot WHERE snapshot_id='eliteserien-2026-round7'"
    if ($LASTEXITCODE -ne 0 -or !$snapshotText) { throw 'Export dello scenario non riuscito.' }
    $auditDir = Join-Path $projectRoot '.local\audit'
    [IO.Directory]::CreateDirectory($auditDir) | Out-Null
    [IO.File]::WriteAllText((Join-Path $auditDir 'snapshot.json'), ($snapshotText -join "`n"), [Text.UTF8Encoding]::new($false))
    Write-Output 'Snapshot esportato in .local/audit/snapshot.json (escluso da Git).'
} finally {
    $env:PGPASSWORD = $oldPassword
    $env:PGCLIENTENCODING = $oldEncoding
}
