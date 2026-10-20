# Campus Marketplace - Full Stack Launcher
param(
    [switch]$Build
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "      CAMPUS MARKETPLACE LAUNCHER         " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$backendDir = Join-Path $PSScriptRoot "backend"
$frontendDir = Join-Path $PSScriptRoot "frontend"

# Build backend if requested
if ($Build) {
    Write-Host "`n[1/3] Compiling backend..." -ForegroundColor Yellow
    Push-Location $backendDir
    & powershell -ExecutionPolicy Bypass -File .\build-backend.ps1
    Pop-Location
}

# Start backend if port 8080 is not active
$backendPortActive = Test-NetConnection -ComputerName localhost -Port 8080 -InformationLevel Quiet
if (-not $backendPortActive) {
    Write-Host "`n[1/2] Starting Spring Boot Backend on http://localhost:8080..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-ExecutionPolicy Bypass -NoExit -Command `"Set-Location '$backendDir'; .\run-backend.ps1`""
    Start-Sleep -Seconds 6
} else {
    Write-Host "`n[1/2] Backend is already running on http://localhost:8080." -ForegroundColor Green
}

# Start frontend if port 5173 is not active
$frontendPortActive = Test-NetConnection -ComputerName localhost -Port 5173 -InformationLevel Quiet
if (-not $frontendPortActive) {
    Write-Host "`n[2/2] Starting Frontend Vite Dev Server on http://localhost:5173..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoExit -Command `"Set-Location '$frontendDir'; npm run dev`""
} else {
    Write-Host "`n[2/2] Frontend is already running on http://localhost:5173." -ForegroundColor Green
}

Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host " Application URLs:" -ForegroundColor White
Write-Host " Frontend : http://localhost:5173" -ForegroundColor Green
Write-Host " Backend  : http://localhost:8080" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Cyan
