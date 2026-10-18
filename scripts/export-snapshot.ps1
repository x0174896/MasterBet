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
    $snapshotText = & (Join-Path $PostgresHome 'bin\psql.exe') -X -w -h 127.0.0.1 -p 5432 -U $settings['DB_USERNAME'] -d masterbet_local -t -A -c "SELECT payload FROM simulation_snapshot ORDER BY cutoff DESC LIMIT 1"
    if ($LASTEXITCODE -ne 0 -or !$snapshotText) { throw 'Export dello scenario non riuscito.' }
    $auditDir = Join-Path $projectRoot '.local\audit'
    [IO.Directory]::CreateDirectory($auditDir) | Out-Null
    [IO.File]::WriteAllText((Join-Path $auditDir 'snapshot.json'), ($snapshotText -join "`n"), [Text.UTF8Encoding]::new($false))
    $rounds = & (Join-Path $PostgresHome 'bin\psql.exe') -X -w -h 127.0.0.1 -p 5432 -U $settings['DB_USERNAME'] -d masterbet_local -t -A -c "SELECT COALESCE(json_agg(COALESCE(p.payload,r.payload)::json ORDER BY r.cutoff),'[]'::json) FROM betting_round r LEFT JOIN betting_proposal p ON p.scenario_id=r.scenario_id"
    if ($LASTEXITCODE -ne 0) { throw 'Export schedine non riuscito.' }
    [IO.File]::WriteAllText((Join-Path $auditDir 'betting-rounds.json'), ($rounds -join "`n"), [Text.UTF8Encoding]::new($false))
    $reveals = & (Join-Path $PostgresHome 'bin\psql.exe') -X -w -h 127.0.0.1 -p 5432 -U $settings['DB_USERNAME'] -d masterbet_local -t -A -c "SELECT COALESCE(json_agg(payload::json),'[]'::json) FROM betting_reveal"
    if ($LASTEXITCODE -ne 0) { throw 'Export esiti non riuscito.' }
    [IO.File]::WriteAllText((Join-Path $auditDir 'betting-reveals.json'), ($reveals -join "`n"), [Text.UTF8Encoding]::new($false))
    $snapshots = & (Join-Path $PostgresHome 'bin\psql.exe') -X -w -h 127.0.0.1 -p 5432 -U $settings['DB_USERNAME'] -d masterbet_local -t -A -c "SELECT COALESCE(json_agg(payload::json ORDER BY cutoff),'[]'::json) FROM simulation_snapshot"
    if ($LASTEXITCODE -ne 0) { throw 'Export storico snapshot non riuscito.' }
    [IO.File]::WriteAllText((Join-Path $auditDir 'snapshots.json'), ($snapshots -join "`n"), [Text.UTF8Encoding]::new($false))
    Write-Output 'Snapshot esportato in .local/audit/snapshot.json (escluso da Git).'
} finally {
    $env:PGPASSWORD = $oldPassword
    $env:PGCLIENTENCODING = $oldEncoding
}
