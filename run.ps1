# SynchPlay - Development Server Launcher
# Starts backend (Spring Boot :8080) and frontend (Vite :5173) with health checks

param(
    [switch]$NoOpen = $false,
    [switch]$BackendOnly = $false,
    [switch]$FrontendOnly = $false
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendProcess = $null
$frontendProcess = $null

function Cleanup {
    Write-Host ""
    Write-Host "Shutting down..."
    
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
        Write-Host "Backend stopped (PID: $($backendProcess.Id))"
    }
    if ($frontendProcess -and -not $frontendProcess.HasExited) {
        Stop-Process -Id $frontendProcess.Id -Force -ErrorAction SilentlyContinue
        Write-Host "Frontend stopped (PID: $($frontendProcess.Id))"
    }
    
    exit
}

trap { Cleanup }

# Check prerequisites
Write-Host "Checking prerequisites..." -ForegroundColor Cyan
$javaCheck = java -version 2>&1 | Select-Object -First 1
if (-not $javaCheck) {
    Write-Host "ERROR: Java is not installed or not in PATH" -ForegroundColor Red
    exit 1
}

$nodeCheck = node --version 2>&1
if (-not $nodeCheck) {
    Write-Host "ERROR: Node.js is not installed or not in PATH" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Java found" -ForegroundColor Green
Write-Host "✓ Node.js found ($nodeCheck)" -ForegroundColor Green
Write-Host ""

# Start Backend
if (-not $FrontendOnly) {
    Write-Host "Starting Backend (Spring Boot :8080)..." -ForegroundColor Yellow
    $backendProcess = Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-NoExit", "-Command", "cd '$scriptDir\backend-springboot'; mvn spring-boot:run -q" `
        -PassThru
    Write-Host "Backend started (PID: $($backendProcess.Id))" -ForegroundColor Green
    Start-Sleep -Seconds 2
}

# Start Frontend
if (-not $BackendOnly) {
    Write-Host "Starting Frontend (Vite :5173)..." -ForegroundColor Yellow
    $frontendProcess = Start-Process -FilePath "powershell.exe" `
        -ArgumentList "-NoExit", "-Command", "cd '$scriptDir\frontend-vue'; npm run dev" `
        -PassThru
    Write-Host "Frontend started (PID: $($frontendProcess.Id))" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Services running:" -ForegroundColor Cyan
if (-not $FrontendOnly) {
    Write-Host "  Backend:  http://localhost:8080" -ForegroundColor Green
}
if (-not $BackendOnly) {
    Write-Host "  Frontend: http://localhost:5173" -ForegroundColor Green
}
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press Ctrl+C to stop all services." -ForegroundColor Yellow
Write-Host ""

# Wait for both processes
while ($true) {
    if ($backendProcess -and $backendProcess.HasExited) {
        Write-Host "WARNING: Backend exited" -ForegroundColor Red
        Cleanup
    }
    if ($frontendProcess -and $frontendProcess.HasExited) {
        Write-Host "WARNING: Frontend exited" -ForegroundColor Red
        Cleanup
    }
    Start-Sleep -Seconds 2
}
