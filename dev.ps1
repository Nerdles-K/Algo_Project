# Starts backend (Spring Boot :8080) and frontend (Vite :5173) in parallel.
# Press Ctrl+C to stop both.

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendProcess = $null
$frontendProcess = $null

function Cleanup {
    Write-Host ""
    Write-Host "Shutting down..."
    
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
    }
    if ($frontendProcess -and -not $frontendProcess.HasExited) {
        Stop-Process -Id $frontendProcess.Id -Force -ErrorAction SilentlyContinue
    }
    
    exit
}

trap { Cleanup }

Write-Host "Starting backend  (Spring Boot :8080)..."
$backendProcess = Start-Process -FilePath "powershell.exe" `
    -ArgumentList "-NoExit", "-Command", "cd '$scriptDir\backend-springboot'; mvn spring-boot:run -q" `
    -PassThru

Write-Host "Starting frontend (Vite :5173)..."
$frontendProcess = Start-Process -FilePath "powershell.exe" `
    -ArgumentList "-NoExit", "-Command", "cd '$scriptDir\frontend-vue'; npm run dev" `
    -PassThru

Write-Host ""
Write-Host "Backend  PID: $($backendProcess.Id)"
Write-Host "Frontend PID: $($frontendProcess.Id)"
Write-Host "Open http://localhost:5173 once both services are ready."
Write-Host "Press Ctrl+C to stop."

# Wait for both processes
while ($true) {
    if ($backendProcess.HasExited -or $frontendProcess.HasExited) {
        Write-Host "One or more processes exited unexpectedly."
        Cleanup
    }
    Start-Sleep -Seconds 1
}
