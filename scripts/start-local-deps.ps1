$ErrorActionPreference = "Stop"

Write-Host "[deps] Checking Redis..."
$redisService = Get-Service -Name "Redis" -ErrorAction SilentlyContinue
if ($null -ne $redisService -and $redisService.Status -ne "Running") {
    Start-Service -Name "Redis"
}

Write-Host "[deps] Checking MySQL..."
$mysqlPortOpen = (Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -WarningAction SilentlyContinue).TcpTestSucceeded
if (-not $mysqlPortOpen) {
    $mysqld = "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe"
    $myIni = "D:\Java agent\.local\mysql\my.ini"
    if (!(Test-Path $mysqld)) {
        throw "mysqld.exe not found at: $mysqld"
    }
    if (!(Test-Path $myIni)) {
        throw "my.ini not found at: $myIni"
    }
    Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=`"$myIni`"" -WindowStyle Hidden | Out-Null
}

Write-Host "[deps] Checking Qdrant..."
$qdrantPortOpen = (Test-NetConnection -ComputerName 127.0.0.1 -Port 6333 -WarningAction SilentlyContinue).TcpTestSucceeded
if (-not $qdrantPortOpen) {
    $qdrant = "D:\Java agent\.local\qdrant\qdrant.exe"
    if (!(Test-Path $qdrant)) {
        throw "qdrant.exe not found at: $qdrant"
    }
    Start-Process -FilePath $qdrant -WorkingDirectory "D:\Java agent\.local\qdrant" -WindowStyle Hidden | Out-Null
}

Start-Sleep -Seconds 2

$mysqlReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -WarningAction SilentlyContinue).TcpTestSucceeded
$redisReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port 6379 -WarningAction SilentlyContinue).TcpTestSucceeded
$qdrantReady = (Test-NetConnection -ComputerName 127.0.0.1 -Port 6333 -WarningAction SilentlyContinue).TcpTestSucceeded

Write-Host "[deps] MySQL 127.0.0.1:3306  => $mysqlReady"
Write-Host "[deps] Redis 127.0.0.1:6379  => $redisReady"
Write-Host "[deps] Qdrant 127.0.0.1:6333 => $qdrantReady"

if (-not ($mysqlReady -and $redisReady -and $qdrantReady)) {
    throw "Dependency startup incomplete. Please check the port status above."
}

Write-Host "[deps] All dependencies are ready."
