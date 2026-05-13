param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$Username = "rag_seed_user",
    [string]$Password = "12345678",
    [switch]$RegisterIfMissing,
    [switch]$Overwrite
)

$ErrorActionPreference = "Stop"

function Invoke-JsonPost {
    param(
        [string]$Uri,
        [hashtable]$Body
    )
    return Invoke-RestMethod -Method Post -Uri $Uri -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8)
}

if ($RegisterIfMissing) {
    try {
        Invoke-JsonPost -Uri "$BaseUrl/api/v1/auth/register" -Body @{
            username = $Username
            email = "$Username@example.local"
            password = $Password
        } | Out-Null
        Write-Host "[seed] Registered user: $Username"
    } catch {
        Write-Host "[seed] Register skipped (user may already exist)."
    }
}

$auth = Invoke-JsonPost -Uri "$BaseUrl/api/v1/auth/login" -Body @{
    username = $Username
    password = $Password
}

$token = $auth.token
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Login succeeded but token is empty."
}

$overwriteValue = if ($Overwrite) { "true" } else { "false" }
$seedResult = Invoke-RestMethod -Method Post `
    -Uri "$BaseUrl/api/v1/knowledge/documents/seed/popular-attractions?overwrite=$overwriteValue" `
    -Headers @{ Authorization = "Bearer $token" }

Write-Host "[seed] Popular attractions seeded."
$seedResult | ConvertTo-Json -Depth 8
