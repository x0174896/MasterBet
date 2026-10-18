# Test isolato del legame console/processi: non avvia MasterBet e non usa database o API.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$testDir = Join-Path $projectRoot ('.local\console-tests\' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDir -Force | Out-Null
$runner = Join-Path $testDir 'owner.ps1'
$fixture = Join-Path $testDir 'child.cjs'
@'
param($JobSource, $NodePath, $Fixture, $StateFile)
$ErrorActionPreference = 'Stop'
Add-Type -Path $JobSource
[MasterBet.ConsoleProcessJob]::Attach()
& $NodePath $Fixture $StateFile
'@ | Set-Content -LiteralPath $runner -Encoding ascii
@'
const fs = require('node:fs');
const { spawn } = require('node:child_process');
const grandchild = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { stdio: 'ignore', windowsHide: true });
fs.writeFileSync(process.argv[2], JSON.stringify({ child: process.pid, grandchild: grandchild.pid }));
setInterval(() => {}, 1000);
'@ | Set-Content -LiteralPath $fixture -Encoding ascii
$nodePath = (Get-Command node.exe -ErrorAction Stop).Source
$powershellPath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$owners = @()
$states = @()
try {
    foreach ($number in @(1,2)) {
        $stateFile = Join-Path $testDir "state-$number.json"
        $arguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$runner+'"'),'-JobSource',('"'+(Join-Path $PSScriptRoot 'ConsoleProcessJob.cs')+'"'),'-NodePath',('"'+$nodePath+'"'),'-Fixture',('"'+$fixture+'"'),'-StateFile',('"'+$stateFile+'"'))
        $owner = Start-Process -FilePath $powershellPath -ArgumentList $arguments -WindowStyle Hidden -PassThru
        $owners += $owner
        $deadline = [DateTime]::UtcNow.AddSeconds(20)
        while (!(Test-Path -LiteralPath $stateFile)) {
            if ($owner.HasExited -or [DateTime]::UtcNow -gt $deadline) { throw 'Processo di test non avviato.' }
            Start-Sleep -Milliseconds 100
        }
        $states += Get-Content -Raw -LiteralPath $stateFile | ConvertFrom-Json
    }
    for ($index=0; $index -lt 2; $index++) {
        # Simula anche una chiusura brusca: nessun finally PowerShell puo' intervenire.
        $owners[$index].Kill()
        $owners[$index].WaitForExit()
        $deadline = [DateTime]::UtcNow.AddSeconds(5)
        do {
            $remaining = @(Get-Process -Id @([int]$states[$index].child,[int]$states[$index].grandchild) -ErrorAction SilentlyContinue)
            if (!$remaining.Count) { break }
            Start-Sleep -Milliseconds 100
        } while ([DateTime]::UtcNow -lt $deadline)
        if ($remaining.Count) { throw 'Processi figli rimasti attivi dopo la chiusura della console.' }
        if ($index -eq 0) {
            $other = @(Get-Process -Id @([int]$states[1].child,[int]$states[1].grandchild) -ErrorAction SilentlyContinue)
            if ($owners[1].HasExited -or $other.Count -ne 2) { throw 'La seconda console e stata coinvolta nella chiusura della prima.' }
        }
    }
    Write-Output 'PASS: chiusura brusca dei due proprietari, figli e nipoti terminati, isolamento fra console verificato.'
} finally {
    foreach ($owner in $owners) { if (!$owner.HasExited) { $owner.Kill(); $owner.WaitForExit() } }
}
