# 로컬 조회 API 성능 측정

`read-api.js`는 데이터를 변경하지 않는 담당 API를 한 번에 하나씩 측정합니다.
운영 서버 대신 로컬 또는 별도 테스트 환경에서 사용하세요. 테스트 계정과 충분한 데이터가 필요합니다.

## Windows 로컬 서버

이 저장소의 로컬 PostgreSQL 컨테이너는 호스트 포트 `5434`로 실행했습니다. Docker Desktop을 시작한 뒤 컨테이너가 이미 있으면 `docker start fleaflea-local-db`, 처음이라면 팀의 로컬 설정 문서에 있는 `docker run` 명령을 PowerShell 줄바꿈 문자 `` ` ``로 바꿔 실행합니다. `docker exec fleaflea-local-db pg_isready -U postgres -d fleaflea_db`로 DB를 확인합니다.

백엔드는 Java 25와 팀에서 받은 로컬 환경 변수가 필요합니다. PowerShell에서는 `export DB_PASSWORD=...` 대신 `$env:DB_PASSWORD = '...'`처럼 설정합니다. Docker 포트가 `5434`라면 `$env:DB_URL = 'jdbc:postgresql://localhost:5434/fleaflea_db'`로 맞춘 뒤 `.\gradlew.bat bootRun`을 실행합니다. 비밀번호와 JWT 키를 저장소 파일에 쓰지 마세요.

## 준비

1. PostgreSQL과 백엔드를 실행하고 `http://localhost:8080/actuator/health`가 `UP`인지 확인합니다.
2. 테스트 계정으로 `POST /api/v1/auth/login`을 호출합니다.
3. PowerShell에서 응답의 `accessToken`을 현재 세션 변수 `$env:K6_TOKEN`에 지정합니다. 토큰과 비밀번호는 커밋하지 않습니다.

```powershell
$loginBody = @{ email = 'k6-local@example.test'; password = 'k6local1234' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/login' -ContentType 'application/json' -Body $loginBody
$env:K6_TOKEN = $login.accessToken
```

새 로컬 DB라면 아래의 전용 테스트 계정을 회원가입 API로 만든 뒤 `seed-local.sql`을 적용할 수 있습니다. 이 SQL은 도감 아이템 500개와 플리마켓 100개를 추가합니다. 해당 계정이 있는 로컬 테스트 DB에서만 사용하세요.

```powershell
$owner = @{ email = 'k6-local@example.test'; password = 'k6local1234'; nickname = 'k6-local' } | ConvertTo-Json
$requester = @{ email = 'k6-requester@example.test'; password = 'k6local1234'; nickname = 'k6-requester' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/signup' -ContentType 'application/json' -Body $owner
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/signup' -ContentType 'application/json' -Body $requester
```

```powershell
Get-Content .\performance\k6\seed-local.sql -Raw | docker exec -i fleaflea-local-db psql -v ON_ERROR_STOP=1 -U postgres -d fleaflea_db
```

이 SQL은 도감 거래 요청 한 건도 만듭니다. 각 상세 조회에 필요한 ID는 아래 쿼리로 확인합니다.

```powershell
docker exec fleaflea-local-db psql -U postgres -d fleaflea_db -c "SELECT collection_item_id FROM collection_items WHERE title='k6 collection item 1'; SELECT market_id FROM markets WHERE invite_code='k6-local-market-1'; SELECT collection_trade_request_id FROM collection_trade_requests ORDER BY collection_trade_request_id LIMIT 1;"
```

더 큰 데이터에서 인덱스와 조회 쿼리를 비교할 때는 `seed-large-local.sql`을 같은 방법으로 적용해 도감 아이템 총 50,000개로 확장합니다. 먼저 작은 데이터의 측정을 끝낸 뒤 사용합니다.

## 실행 (PowerShell)

프로젝트 루트에서 먼저 한 명의 가상 사용자로 정상 응답을 확인합니다.

```powershell
k6 run -e K6_TOKEN="$env:K6_TOKEN" -e VUS=1 -e DURATION=30s .\performance\k6\read-api.js
```

그다음 같은 API를 10명, 2분 동안 측정합니다.

```powershell
New-Item -ItemType Directory -Force .\performance\k6\results | Out-Null
k6 run -e K6_TOKEN="$env:K6_TOKEN" -e VUS=10 -e DURATION=2m --summary-export=performance/k6/results/collection-items-before.json .\performance\k6\read-api.js
```

다른 조회 API를 측정할 때는 `-e ENDPOINT=...`와 필요한 ID를 추가합니다.

| ENDPOINT | API | 필요한 추가 변수 |
| --- | --- | --- |
| `collection-items` | 내 도감 목록 | 없음 |
| `member-collection-items` | 회원 도감 목록 | `OWNER_ID` |
| `collection-item` | 도감 상세 | `COLLECTION_ITEM_ID` |
| `markets` | 참여 플리마켓 목록 | 없음 |
| `market` | 플리마켓 상세 | `MARKET_ID` |
| `market-members` | 플리마켓 참여자 목록 | `MARKET_ID` |
| `collection-trade` | 도감 거래 요청 상세 | `TRADE_REQUEST_ID` |

예: `-e ENDPOINT=market -e MARKET_ID=1`. 다른 서버를 측정할 때는 `-e BASE_URL=http://주소:포트`를 추가합니다.

`http_req_duration`의 p95, `http_reqs`의 초당 처리량, `http_req_failed`와 `checks`를 기록합니다. 데이터 건수와 VU, 실행 시간도 함께 기록하고 개선 전후에 같은 조건을 사용합니다. `401`은 토큰 만료, `403`은 접근 권한, `404`는 ID 오류를 먼저 확인합니다.

기본 요청 간 대기 시간은 1초입니다. 최대 처리량을 살펴볼 때는 `-e THINK_TIME=0`을 사용할 수 있지만, 전후 비교에는 반드시 같은 값을 적용하세요.

`before.json` 같은 결과 파일과 토큰은 저장소에 커밋하지 않습니다.

## 도감 거래 상태 변경 시나리오

`collection-trade-workflow.js`는 매 반복마다 다른 도감 아이템에 요청을 만들고, 수락 후 완료·거절·취소를 번갈아 실행합니다. `seed-local.sql`이 테스트 계정 간 친구 관계를 준비합니다. 데이터가 변경되므로 로컬 테스트 DB에서만 실행하고, 재실행할 때는 사용하지 않은 `FIRST_ITEM_ID`부터 시작하세요.

```powershell
$ownerLogin = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/login' -ContentType 'application/json' -Body (@{email='k6-local@example.test';password='k6local1234'} | ConvertTo-Json)
$requesterLogin = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/login' -ContentType 'application/json' -Body (@{email='k6-requester@example.test';password='k6local1234'} | ConvertTo-Json)
$env:OWNER_TOKEN = $ownerLogin.accessToken
$env:REQUESTER_TOKEN = $requesterLogin.accessToken
$env:FIRST_ITEM_ID = '2001'
$env:VUS = '3'
$env:DURATION = '30s'
k6 run .\performance\k6\collection-trade-workflow.js
```

현재 로컬 데이터에서 `FIRST_ITEM_ID`는 `1`부터 `50000`까지 유효합니다. 같은 ID로 다시 요청을 생성하면 정상적인 중복 요청 오류가 발생할 수 있습니다.
