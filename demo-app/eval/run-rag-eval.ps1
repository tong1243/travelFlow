param(
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory = $true)]
    [string]$Token,
    [string]$InputFile = ".\\eval\\rag_eval_cases.json",
    [string]$OutputFile = ".\\eval\\rag_eval_report.md"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path $InputFile)) {
    throw "Input file not found: $InputFile"
}

$cases = Get-Content -Raw -Path $InputFile | ConvertFrom-Json
$headers = @{
    "Authorization" = "Bearer $Token"
}

$rows = @()
foreach ($case in $cases) {
    $payload = @{
        sessionId = $null
        question = $case.question
        topK = if ($case.topK) { [int]$case.topK } else { 5 }
        sourceType = if ($case.sourceType) { $case.sourceType } else { $null }
        sourceRefContains = if ($case.sourceRefContains) { $case.sourceRefContains } else { $null }
    }

    try {
        $response = Invoke-RestMethod `
            -Method Post `
            -Uri "$BaseUrl/api/v1/chat/ask" `
            -Headers $headers `
            -ContentType "application/json" `
            -Body ($payload | ConvertTo-Json -Depth 8)

        $answer = [string]$response.answer
        $referenceCount = if ($response.references) { @($response.references).Count } else { 0 }
        $expected = @($case.expectedKeywords)
        $hit = 0
        foreach ($kw in $expected) {
            if ($answer -like "*$kw*") {
                $hit++
            }
        }
        $score = if ($expected.Count -gt 0) { [math]::Round(($hit / $expected.Count) * 100, 1) } else { 0 }
        $pass = $score -ge 60

        $rows += [PSCustomObject]@{
            id = $case.id
            score = $score
            pass = $pass
            references = $referenceCount
            hit = "$hit/$($expected.Count)"
            question = $case.question
        }
    } catch {
        $rows += [PSCustomObject]@{
            id = $case.id
            score = 0
            pass = $false
            references = 0
            hit = "0/0"
            question = "$($case.question) (error: $($_.Exception.Message))"
        }
    }
}

$passed = @($rows | Where-Object { $_.pass }).Count
$total = @($rows).Count
$avgScore = if ($total -gt 0) { [math]::Round((($rows | Measure-Object -Property score -Average).Average), 1) } else { 0 }

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("# RAG Eval Report")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("- Base URL: $BaseUrl")
[void]$sb.AppendLine("- Total cases: $total")
[void]$sb.AppendLine("- Passed: $passed")
[void]$sb.AppendLine("- Average keyword score: $avgScore")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("| id | pass | score | references | keyword hit | question |")
[void]$sb.AppendLine("|---|---|---:|---:|---|---|")
foreach ($row in $rows) {
    [void]$sb.AppendLine("| $($row.id) | $($row.pass) | $($row.score) | $($row.references) | $($row.hit) | $($row.question) |")
}

$reportDir = Split-Path -Parent $OutputFile
if ($reportDir -and -not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir | Out-Null
}
Set-Content -Path $OutputFile -Value $sb.ToString() -Encoding UTF8

Write-Host "Report generated: $OutputFile"
