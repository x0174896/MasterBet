[CmdletBinding()]
param([ValidateSet('all','backend','frontend')][string]$Component = 'all')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
foreach ($service in @('frontend','backend')) {
    if ($Component -ne 'all' -and $Component -ne $service) { continue }
    $componentName = $service
    $recordPath = Join-Path $projectRoot ".local\$componentName-process.json"
    if (!(Test-Path -LiteralPath $recordPath)) { continue }
    $record = Get-Content -Raw -LiteralPath $recordPath | ConvertFrom-Json
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $([int]$record.processId)"
    if ($process) {
        $expectedStart = if ($record.startedAt -is [DateTime]) { $record.startedAt.ToUniversalTime() } else {
            [DateTime]::Parse($record.startedAt, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::RoundtripKind).ToUniversalTime()
        }
        $actualStart = $process.CreationDate.ToUniversalTime()
        if ($process.ExecutablePath -ne $record.executable -or !$process.CommandLine.Contains($record.marker) -or [Math]::Abs(($actualStart-$expectedStart).TotalSeconds) -gt 2) {
            throw "Il PID salvato per $componentName non corrisponde al processo avviato dal progetto. Arresto rifiutato."
        }
        Stop-Process -Id ([int]$record.processId) -ErrorAction Stop
        Write-Output "$componentName MasterBet arrestato."
    }
    Remove-Item -LiteralPath $recordPath
}
