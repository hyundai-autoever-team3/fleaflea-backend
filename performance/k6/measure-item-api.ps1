# Measures the item read APIs under identical conditions so before/after runs can be
# compared. Each round runs every endpoint once, and rounds repeat, so drift during
# the session (other programs, thermal throttling) spreads over all endpoints
# instead of landing on one.
#
#   .\performance\k6\measure-item-api.ps1 -Label baseline
#   .\performance\k6\measure-item-api.ps1 -Label v10-index
#
# Results: performance/k6/results/<Label>/<endpoint>-r<round>.json and summary.csv

param(
    [Parameter(Mandatory = $true)][string]$Label,
    [int]$Rounds = 3,
    [int]$Vus = 10,
    [string]$Duration = '1m',
    [double]$ThinkTime = 1,
    [string[]]$Endpoints = @('items', 'items-filter', 'items-keyword', 'item'),
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'

$k6 = (Get-Command k6 -ErrorAction SilentlyContinue).Source
if (-not $k6) { $k6 = "$env:ProgramFiles\k6\k6.exe" }
if (-not (Test-Path $k6)) { throw 'k6 not found. Install it with: winget install k6 --source winget' }

$script = Join-Path $PSScriptRoot 'read-api.js'
$datasetPath = Join-Path $PSScriptRoot 'data\item-dataset.json'
if (-not (Test-Path $datasetPath)) { throw "Missing $datasetPath. Run setup-perf-data.ps1 first." }

# The same market and item every time, so runs are comparable.
$dataset = [IO.File]::ReadAllText($datasetPath) | ConvertFrom-Json
$marketId = $dataset.hotMarkets[0]
$itemId = $dataset.hotItems[0]

$outDir = Join-Path $PSScriptRoot "results\$Label"
New-Item -ItemType Directory -Force $outDir | Out-Null

function Get-AccessToken {
    $body = @{ email = 'k6-local@example.test'; password = 'k6local1234' } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType 'application/json' -Body $body).accessToken
}

Write-Host "Label=$Label market=$marketId item=$itemId rounds=$Rounds vus=$Vus duration=$Duration think=$ThinkTime"

for ($round = 1; $round -le $Rounds; $round++) {
    # A fresh token per round; access tokens expire after 30 minutes.
    $env:K6_TOKEN = Get-AccessToken

    foreach ($endpoint in $Endpoints) {
        $file = Join-Path $outDir "$endpoint-r$round.json"
        Write-Host ("[{0}] round {1}/{2} {3}" -f (Get-Date -Format 'HH:mm:ss'), $round, $Rounds, $endpoint)
        & $k6 run -q `
            -e BASE_URL=$BaseUrl -e ENDPOINT=$endpoint -e MARKET_ID=$marketId -e ITEM_ID=$itemId `
            -e VUS=$Vus -e DURATION=$Duration -e THINK_TIME=$ThinkTime `
            --summary-trend-stats 'avg,min,med,max,p(90),p(95),p(99)' `
            --summary-export=$file $script 2>&1 | Out-Null
    }
}

function Get-Median([double[]]$values) {
    $sorted = $values | Sort-Object
    $mid = [math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2) { $sorted[$mid] } else { ($sorted[$mid - 1] + $sorted[$mid]) / 2 }
}

$rows = foreach ($endpoint in $Endpoints) {
    $runs = 1..$Rounds | ForEach-Object {
        (Get-Content (Join-Path $outDir "$endpoint-r$_.json") -Raw | ConvertFrom-Json).metrics
    }
    $p95 = [double[]]($runs | ForEach-Object { $_.http_req_duration.'p(95)' })
    $p99 = [double[]]($runs | ForEach-Object { $_.http_req_duration.'p(99)' })
    $med = [double[]]($runs | ForEach-Object { $_.http_req_duration.med })

    [pscustomobject]@{
        endpoint      = $endpoint
        p95_median_ms = [math]::Round((Get-Median $p95), 2)
        p95_runs_ms   = ($p95 | ForEach-Object { [math]::Round($_, 2) }) -join ' / '
        p95_spread_ms = [math]::Round((($p95 | Measure-Object -Maximum).Maximum - ($p95 | Measure-Object -Minimum).Minimum), 2)
        # p99 needs thousands of requests per run to be stable; with ~600 it is the
        # 6th slowest request. Use -Duration 5m or more when comparing p99.
        p99_median_ms = [math]::Round((Get-Median $p99), 2)
        p99_runs_ms   = ($p99 | ForEach-Object { [math]::Round($_, 2) }) -join ' / '
        med_median_ms = [math]::Round((Get-Median $med), 2)
        requests      = ($runs | ForEach-Object { $_.http_reqs.count } | Measure-Object -Sum).Sum
        failed_rate   = [math]::Round((($runs | ForEach-Object { $_.http_req_failed.value } | Measure-Object -Maximum).Maximum), 4)
    }
}

$rows | Export-Csv -NoTypeInformation -Encoding UTF8 (Join-Path $outDir 'summary.csv')
$rows | Format-Table -AutoSize | Out-String -Width 200 | Write-Host
Write-Host "Saved $outDir"
