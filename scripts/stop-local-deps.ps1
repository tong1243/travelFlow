$ErrorActionPreference = "SilentlyContinue"

Write-Host "[deps] Stopping qdrant..."
Get-Process -Name "qdrant" | Stop-Process -Force

Write-Host "[deps] Stopping mysqld..."
Get-Process -Name "mysqld" | Stop-Process -Force

Write-Host "[deps] Stopping Redis service..."
Stop-Service -Name "Redis"

Write-Host "[deps] Done."
