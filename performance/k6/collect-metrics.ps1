# Polls server-side metrics every few seconds while k6 runs, so a p95 spike can be
# matched with what the JVM, connection pool and DB container were doing at that time.
# Requires the backend to run with `gradlew bootRun -Pperf` and the k6-local account
# (setup-perf-data.ps1 creates it). Stop with Ctrl+C.
#
#   .\performance\k6\collect-metrics.ps1 -Output performance/k6/results/item-load-metrics.csv

param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Output = 'performance/k6/results/metrics.csv',
    [int]$IntervalSeconds = 5,
    [string]$DbContainer = 'fleaflea-perf-db',
    [string]$Email = 'k6-local@example.test',
    [string]$Password = 'k6local1234'
)

$ErrorActionPreference = 'Stop'

# /actuator/metrics sits behind Spring Security (only /actuator/health is public),
# so the script logs in with the local test account.
function Get-AccessToken {
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType 'application/json' -Body $body).accessToken
}

$script:token = Get-AccessToken

function Get-Metric([string]$Name, [string]$Statistic = 'VALUE', [string]$Tag = '') {
    $uri = "$BaseUrl/actuator/metrics/$Name"
    if ($Tag) { $uri += "?tag=$Tag" }
    for ($attempt = 0; $attempt -lt 2; $attempt++) {
        try {
            $response = Invoke-RestMethod -Uri $uri -TimeoutSec 2 -Headers @{ Authorization = "Bearer $script:token" }
            return ($response.measurements | Where-Object { $_.statistic -eq $Statistic }).value
        } catch {
            # The access token expires after 30 minutes; log in again once, e.g. during a soak run.
            if ($attempt -eq 0 -and $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) {
                $script:token = Get-AccessToken
                continue
            }
            return $null
        }
    }
}

New-Item -ItemType Directory -Force (Split-Path $Output) | Out-Null
'time,pool_active,pool_pending,heap_used_mb,process_cpu,gc_pause_count,gc_pause_total_ms,gc_pause_max_ms,db_cpu,db_mem' |
    Set-Content -Encoding utf8 $Output

Write-Host "Writing to $Output every ${IntervalSeconds}s. Ctrl+C to stop."

while ($true) {
    $heap = Get-Metric 'jvm.memory.used' -Tag 'area:heap'
    $cpu = Get-Metric 'process.cpu.usage'
    $gcTotal = Get-Metric 'jvm.gc.pause' 'TOTAL_TIME'
    $gcMax = Get-Metric 'jvm.gc.pause' 'MAX'
    $db = docker stats $DbContainer --no-stream --format '{{.CPUPerc}},{{.MemUsage}}' 2>$null
    $dbCpu, $dbMem = if ($db) { $db -split ',', 2 } else { '', '' }

    $row = @(
        (Get-Date -Format 'HH:mm:ss'),
        (Get-Metric 'hikaricp.connections.active'),
        (Get-Metric 'hikaricp.connections.pending'),
        $(if ($null -ne $heap) { [math]::Round($heap / 1MB, 1) }),
        $(if ($null -ne $cpu) { [math]::Round($cpu * 100, 1) }),
        (Get-Metric 'jvm.gc.pause' 'COUNT'),
        $(if ($null -ne $gcTotal) { [math]::Round($gcTotal * 1000, 1) }),
        $(if ($null -ne $gcMax) { [math]::Round($gcMax * 1000, 1) }),
        $dbCpu,
        "`"$dbMem`""
    ) -join ','

    Add-Content -Encoding utf8 $Output $row
    Start-Sleep -Seconds $IntervalSeconds
}
