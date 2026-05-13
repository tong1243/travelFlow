param(
    [switch]$SkipDeps,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Name"
    }
}

function Test-PortOpen {
    param([int]$Port, [string]$HostName = "127.0.0.1", [int]$TimeoutMs = 600)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $result = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $result.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
            return $false
        }
        $client.EndConnect($result)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Wait-Port {
    param([int]$Port, [int]$TimeoutSec = 120, [string]$Name = "")
    $label = if ([string]::IsNullOrWhiteSpace($Name)) { "Port $Port" } else { $Name }
    for ($i = 0; $i -lt $TimeoutSec; $i++) {
        if (Test-PortOpen -Port $Port) {
            Write-Host "[$label] Ready on :$Port"
            return $true
        }
        Start-Sleep -Seconds 1
    }
    Write-Warning "[$label] Not ready after ${TimeoutSec}s (:${Port})."
    return $false
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDir = Join-Path $projectRoot "demo-app"
$frontendDir = Join-Path $projectRoot "frontend"
$depsScript = Join-Path $PSScriptRoot "start-local-deps.ps1"

if (-not (Test-Path $backendDir)) {
    throw "Backend directory not found: $backendDir"
}
if (-not (Test-Path $frontendDir)) {
    throw "Frontend directory not found: $frontendDir"
}

Require-Command "powershell"
Require-Command "mvn"
Require-Command "cmd"

Write-Host "[start] Project root: $projectRoot"

if (-not $SkipDeps) {
    if (Test-Path $depsScript) {
        Write-Host "[start] Booting local dependencies..."
        & powershell -ExecutionPolicy Bypass -File $depsScript
    } else {
        Write-Warning "[start] Dependency script not found, skipped: $depsScript"
    }
} else {
    Write-Host "[start] SkipDeps enabled, skipping dependency startup."
}

$nodeModulesDir = Join-Path $frontendDir "node_modules"
if (-not $SkipInstall) {
    if (-not (Test-Path $nodeModulesDir)) {
        Write-Host "[start] Frontend dependencies missing, running npm install..."
        Push-Location $frontendDir
        try {
            cmd /c npm install
        } finally {
            Pop-Location
        }
    } else {
        Write-Host "[start] Frontend dependencies already present."
    }
} else {
    Write-Host "[start] SkipInstall enabled, skipping npm install."
}

if (Test-PortOpen -Port 8080) {
    Write-Host "[start] Backend already running on :8080, skip launch."
} else {
    Write-Host "[start] Launching backend (Spring Boot)..."
    Start-Process -FilePath "powershell" -WorkingDirectory $backendDir -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", "Set-Location -LiteralPath '$backendDir'; mvn spring-boot:run"
    ) | Out-Null
}

Write-Host "[start] Waiting backend ready before frontend startup..."
$backendReady = Wait-Port -Port 8080 -TimeoutSec 150 -Name "Backend"
if (-not $backendReady) {
    throw "Backend failed to become ready on :8080. Please check backend terminal window logs."
}

if (Test-PortOpen -Port 5173) {
    Write-Host "[start] Frontend already running on :5173, skip launch."
} else {
    Write-Host "[start] Launching frontend (Vite)..."
    Start-Process -FilePath "powershell" -WorkingDirectory $frontendDir -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", "Set-Location -LiteralPath '$frontendDir'; cmd /c npm run dev"
    ) | Out-Null
}

Write-Host "[start] Waiting frontend ready..."
$frontendReady = Wait-Port -Port 5173 -TimeoutSec 60 -Name "Frontend"

Write-Host ""
Write-Host "========== TravelFlow =========="
Write-Host "Backend : http://localhost:8080  (ready=$backendReady)"
Write-Host "Frontend: http://localhost:5173  (ready=$frontendReady)"
Write-Host "================================"
Write-Host ""
Write-Host "Tip: Run '.\\scripts\\stop-local-deps.ps1' to stop local dependencies when finished."
