[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$configuration=Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'backend\.env.local') | ConvertFrom-StringData
if($configuration.DB_URL -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost):5432/masterbet_local$' -or $configuration.DB_USERNAME -ne 'masterbet_app') { throw 'Backup consentito soltanto sul database locale MasterBet documentato.' }
$pgDirectory='C:\Program Files\PostgreSQL\18\bin'
$backupDirectory=Join-Path $projectRoot '.local\backups'
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$backupFile=Join-Path $backupDirectory ('masterbet-before-lab-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfff')+'.dump')
$previousPassword=$env:PGPASSWORD
try {
  $env:PGPASSWORD=$configuration.DB_PASSWORD
  & (Join-Path $pgDirectory 'pg_dump.exe') --host=127.0.0.1 --port=5432 --username=masterbet_app --dbname=masterbet_local --format=custom --file=$backupFile --no-password
  if($LASTEXITCODE -ne 0){throw 'Backup PostgreSQL fallito; nessun reset autorizzato dal file.'}
  & (Join-Path $pgDirectory 'pg_restore.exe') --list $backupFile | Out-Null
  if($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath $backupFile).Length -lt 1024){throw 'Verifica backup fallita.'}
  $hash=(Get-FileHash -LiteralPath $backupFile -Algorithm SHA256).Hash
  $manifest=@{path=$backupFile;sha256=$hash;database='masterbet_local';verifiedAt=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json
  [System.IO.File]::WriteAllText((Join-Path $backupDirectory 'verified-lab-backup.json'),$manifest,(New-Object System.Text.UTF8Encoding($false)))
  Write-Output "Backup locale verificato: $backupFile"
} finally { $env:PGPASSWORD=$previousPassword }
