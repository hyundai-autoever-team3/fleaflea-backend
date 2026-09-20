# 상품(item) 성능 측정

측정 결과는 `ITEM-RESULTS.md`에 있다.

## 1. 환경 띄우기

개발용 컨테이너(`fleaflea-local-db`, `fleaflea-local-s3`)와 분리된 환경을 쓴다. PostgreSQL 16은 `localhost:5435`, MinIO는 `localhost:9010`(콘솔 `9011`)에 뜨고, 버킷 생성과 `pg_stat_statements` 설정까지 자동으로 된다.

```powershell
docker compose -f performance/compose.yml up -d
```

`.env`를 만들고 DB와 S3를 측정 환경으로 맞춘다. 값은 `.env.example` 참고.

```dotenv
DB_URL=jdbc:postgresql://localhost:5435/fleaflea_db
DB_USERNAME=postgres
DB_PASSWORD=password
S3_BUCKET=fleaflea-local
AWS_ACCESS_KEY_ID=k6local
AWS_SECRET_ACCESS_KEY=k6localpass123
AWS_ENDPOINT_URL_S3=http://127.0.0.1:9010
AWS_EC2_METADATA_DISABLED=true
```

백엔드는 `perf` 모드로 띄운다. actuator 지표와 엔드포인트별 서버 측 p95/p99가 켜지고, GC 로그가 `build/perf/gc.log`에 쌓인다. 이 설정은 `build.gradle`의 `bootRun` 작업에만 있어 배포 jar에는 들어가지 않는다.

```powershell
.\gradlew.bat bootRun -Pperf
```

환경을 지울 때는 `docker compose -f performance/compose.yml down -v`를 쓴다. `-v`는 시드 데이터가 든 볼륨까지 지운다.

## 2. 데이터 준비

백엔드가 뜬 상태에서 한 번 실행한다. 다시 실행해도 데이터가 중복되지 않는다.

```powershell
.\performance\k6\setup-perf-data.ps1
```

테스트 계정 생성, `seed-local.sql`, `seed-items-local.sql`, 데이터셋 추출(`data/item-dataset.json`)을 차례로 수행하고, 마지막에 테이블별 건수와 대형 마켓 ID를 출력한다. `read-api.js`의 `MARKET_ID`에 이 ID를 쓴다.

| 데이터 | 개수 |
| --- | ---: |
| 회원 | 1,000명 + 테스트 계정 2개 |
| 플리마켓 | 100개 |
| 참여자 | 대형 50명, 중형 20명, 소형 8명 (약 1,350건) |
| 상품 | 약 49,750건 (마켓 1~5는 3,000건, 6~25는 800건, 26~100은 250건) |

상품 목록 쿼리는 항상 `market_id`로 범위가 정해지므로 마켓별 상품 수가 성능을 결정한다. 검색어 선택도는 `rare` 약 1%, `popular` 약 9%, `item` 100%다. `data/`는 `.gitignore`에 있고, DB를 새로 만들면 스크립트를 다시 실행한다.

## 3. 단일 API 측정

인덱스와 쿼리의 전후 비교에 쓴다. 네 API를 번갈아 3회 실행하고 p95의 중앙값과 회차 편차를 `results/<라벨>/summary.csv`에 남긴다.

```powershell
.\performance\k6\measure-item-api.ps1 -Label baseline
.\performance\k6\measure-item-api.ps1 -Label v10-index
```

기본값은 10 VU, 1분, 요청 간 1초 대기다. p99를 비교하려면 회차당 요청이 수천 건이 되도록 `-Duration 5m` 이상으로 잰다.

`read-api.js`를 직접 실행할 수도 있다.

| `ENDPOINT` | 요청 | 추가 변수 |
| --- | --- | --- |
| `items` | 상품 목록 | `MARKET_ID`, `PAGE`(기본 0) |
| `items-filter` | 거래 유형, 상태 필터 목록 | `MARKET_ID`, `PAGE` |
| `items-keyword` | 상품명 검색 | `MARKET_ID`, `KEYWORD`(기본 `rare`) |
| `item` | 상품 상세 | `ITEM_ID` |

```powershell
k6 run -e ENDPOINT=items -e MARKET_ID=<대형마켓ID> -e VUS=10 -e DURATION=1m .\performance\k6\read-api.js
```

## 4. 혼합 워크로드 측정

목록 60%, 상세 30%, 검색 7%, 쓰기 3%를 동시에 실행한다. 트래픽의 80%는 대형 마켓 5개로 가고, 부하는 VU 수가 아니라 초당 트랜잭션 수(`TARGET_RPS`)로 지정한다.

| `PROFILE` | 목적 | 길이 |
| --- | --- | --- |
| `smoke` | 스크립트와 상태 코드 확인 | 1분 |
| `load` | 목표 부하에서 SLO 충족 확인 | 약 10분 |
| `stress` | 한계점 탐색 (목표의 4배까지) | 약 9분 |
| `soak` | 누수, 성능 저하 확인 | 약 33분 |

```powershell
k6 run -e PROFILE=smoke -e TARGET_RPS=50 .\performance\k6\item-scenarios.js
k6 run -e PROFILE=load -e TARGET_RPS=50 --summary-export=performance/k6/results/item-load.json .\performance\k6\item-scenarios.js
```

`dropped_iterations`가 0보다 크면 k6가 VU를 다 써서 요청을 보내지 못한 것이다. 이때의 p95는 실제보다 낙관적이므로 `preAllocatedVUs`를 늘려 다시 잰다.

## 5. 서버 지표

측정 중 다른 창에서 실행하면 커넥션 풀, 힙, CPU, GC, DB 컨테이너 지표를 5초마다 CSV로 남긴다.

```powershell
.\performance\k6\collect-metrics.ps1 -Output performance/k6/results/item-load-metrics.csv
```

k6의 p95와 서버가 직접 잰 p95를 비교하면 지연이 핸들러 안에서 생겼는지 밖에서 생겼는지 구분할 수 있다.

```powershell
Invoke-RestMethod 'http://localhost:8080/actuator/metrics/http.server.requests.percentile?tag=uri:/api/v1/markets/{marketId}/items'
```

GC 일시정지는 로그에서 확인한다.

```powershell
Select-String -Path .\build\perf\gc.log -Pattern 'Pause' | Select-Object -Last 20
```

## 6. 실행 계획

목록, count, 검증, 상세 쿼리의 실행 계획을 한 번에 출력한다. 두 번 실행하고 두 번째 결과를 쓴다. 첫 실행은 디스크에서 읽어 실제 상황과 다르다.

```powershell
New-Item -ItemType Directory -Force .\performance\k6\results | Out-Null
Get-Content .\performance\k6\explain-items.sql -Raw | docker exec -i fleaflea-perf-db psql -U postgres -d fleaflea_db | Out-File -Encoding utf8 .\performance\k6\results\explain-baseline.txt
```

`Seq Scan`, `Sort` 노드, `Rows Removed by Filter`, `Buffers`, `Execution Time`을 본다. 파일의 SQL은 앱이 실제로 보내는 SQL을 옮긴 것이므로, 리포지토리 쿼리를 바꾸면 `monitoring.pg_stat_statements`에서 새 SQL을 가져와 갱신한다.

앱이 받은 쿼리별 시간은 측정 직전에 초기화하고 끝난 뒤 조회한다.

```sql
SELECT monitoring.pg_stat_statements_reset();
-- 측정 실행 후
SELECT calls, round(total_exec_time) AS total_ms, round(mean_exec_time, 2) AS mean_ms, query
FROM monitoring.pg_stat_statements ORDER BY total_exec_time DESC LIMIT 10;
```

## 7. 회귀 테스트

쿼리를 고치기 전과 후에 실행한다. 실제 DB가 필요하므로 측정 환경을 먼저 띄운다. 각 테스트는 새 마켓 안에서만 조회하고 롤백하므로 시드 데이터에 영향을 주지 않는다.

```powershell
.\gradlew.bat test --tests 'com.anabada.fleaflea.domain.item.service.ItemServiceFindTest'
```
