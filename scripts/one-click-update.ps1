param(
    [string]$Host = "47.114.91.243",
    [string]$User = "root",
    [string]$KeyPath = "C:\Users\Breeze\.ssh\id_ed25519",
    [string]$ProjectRoot = "",
    [string]$RemoteZip = "/opt/travelflow.zip",
    [string]$RemoteScript = "/tmp/server-redeploy.sh"
)

$ErrorActionPreference = "Stop"

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Name"
    }
}

Require-Command "ssh"
Require-Command "scp"
Require-Command "Compress-Archive"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

if (-not (Test-Path $KeyPath)) {
    throw "SSH key not found: $KeyPath"
}

$remoteScriptLocal = Join-Path $ProjectRoot "scripts\server-redeploy.sh"
if (-not (Test-Path $remoteScriptLocal)) {
    throw "Remote deploy script not found: $remoteScriptLocal"
}

$stagingZip = Join-Path $env:TEMP "travelflow-update.zip"
if (Test-Path $stagingZip) {
    Remove-Item -Force $stagingZip
}

$items = @(
    "demo-app",
    "frontend",
    "scripts",
    "docker-compose.yml",
    "pom.xml",
    "README.md",
    ".gitignore",
    "DEPLOYMENT_GUIDE.md",
    "OPERATIONS_RUNBOOK.md"
)

$packagePaths = @()
foreach ($item in $items) {
    $full = Join-Path $ProjectRoot $item
    if (Test-Path $full) {
        $packagePaths += $full
    }
}

if ($packagePaths.Count -eq 0) {
    throw "No files found to package under $ProjectRoot"
}

Write-Host "[update] Packaging project files ..."
Compress-Archive -Path $packagePaths -DestinationPath $stagingZip -Force

$target = "$User@$Host"

Write-Host "[update] Uploading package to $target ..."
scp -i $KeyPath -o StrictHostKeyChecking=accept-new $stagingZip "$target`:$RemoteZip"

Write-Host "[update] Uploading server script ..."
scp -i $KeyPath -o StrictHostKeyChecking=accept-new $remoteScriptLocal "$target`:$RemoteScript"

Write-Host "[update] Running remote redeploy ..."
$remoteCmd = "chmod +x $RemoteScript && bash $RemoteScript $RemoteZip /opt/travelflow /var/www/travelflow travelflow"
ssh -i $KeyPath -o StrictHostKeyChecking=accept-new $target $remoteCmd

Write-Host "[update] Done."
Write-Host "[update] Verify in browser: http://$Host"

