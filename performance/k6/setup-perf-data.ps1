# Prepares the perf database from performance/compose.yml for item measurements:
# test accounts, markets, members, participants, items, and the k6 dataset.
# Start the backend first (gradlew bootRun -Pperf). Safe to re-run.
#
#   .\performance\k6\setup-perf-data.ps1

param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$DbContainer = 'fleaflea-perf-db'
)

$ErrorActionPreference = 'Stop'

function Invoke-SqlFile([string]$File, [switch]$TuplesOnly) {
    $psqlArgs = @('exec', '-i', $DbContainer, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', 'fleaflea_db')
    if ($TuplesOnly) { $psqlArgs += '-At' }

    $output = Get-Content (Join-Path $PSScriptRoot $File) -Raw | docker @psqlArgs
    if ($LASTEXITCODE -ne 0) { throw "$File failed (exit $LASTEXITCODE)" }
    $output
}

Write-Host "Waiting for the backend at $BaseUrl ..."
$deadline = (Get-Date).AddSeconds(90)
while ($true) {
    try {
        if ((Invoke-RestMethod "$BaseUrl/actuator/health" -TimeoutSec 2).status -eq 'UP') { break }
    } catch { }
    if ((Get-Date) -gt $deadline) {
        throw "Backend is not UP at $BaseUrl. Start it with: .\gradlew.bat bootRun -Pperf"
    }
    Start-Sleep -Seconds 2
}

# Accounts go through the signup API so the password hash is real.
$accounts = @(
    @{ email = 'k6-local@example.test'; password = 'k6local1234'; nickname = 'k6-local' },
    @{ email = 'k6-requester@example.test'; password = 'k6local1234'; nickname = 'k6-requester' }
)
foreach ($account in $accounts) {
    try {
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/signup" `
            -ContentType 'application/json' -Body ($account | ConvertTo-Json) | Out-Null
        Write-Host "Created account  $($account.email)"
    } catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 409) {
            Write-Host "Account exists   $($account.email)"
        } else {
            throw
        }
    }
}

Write-Host 'Seeding markets and collection items ...'
Invoke-SqlFile 'seed-local.sql' | Out-Null

Write-Host 'Seeding members, participants and items ...'
Invoke-SqlFile 'seed-items-local.sql' | Out-Null

# Written without a BOM: Set-Content -Encoding utf8 adds one on Windows
# PowerShell 5.1, and k6's JSON.parse rejects it.
$dataDir = Join-Path $PSScriptRoot 'data'
New-Item -ItemType Directory -Force $dataDir | Out-Null
$datasetPath = Join-Path $dataDir 'item-dataset.json'
$json = (Invoke-SqlFile 'dataset-export.sql' -TuplesOnly) -join ''
[System.IO.File]::WriteAllText($datasetPath, $json, (New-Object System.Text.UTF8Encoding $false))
Write-Host "Dataset written  $datasetPath"

$summary = @'
SELECT
    (SELECT count(*) FROM members)        AS members,
    (SELECT count(*) FROM markets)        AS markets,
    (SELECT count(*) FROM market_members) AS participants,
    (SELECT count(*) FROM items)          AS items;
'@
$summary | docker exec -i $DbContainer psql -U postgres -d fleaflea_db

$dataset = $json | ConvertFrom-Json
Write-Host "Large markets (MARKET_ID for read-api.js): $($dataset.hotMarkets -join ', ')"
