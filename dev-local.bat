@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-local.ps1" -UseWindowsTrustStore
set "launchResult=%errorlevel%"
if not "%launchResult%"=="0" if not "%~1"=="--no-pause" pause
exit /b %launchResult%
