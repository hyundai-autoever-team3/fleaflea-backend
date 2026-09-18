# k6 로컬 실행

조회 API는 `read-api.js`, 도감 거래 상태 변경은 `collection-trade-workflow.js`로 측정한다. 둘 다 로컬 테스트 DB 기준이다.

## 1. 서버와 데이터 준비

Docker Desktop을 켜고 `fleaflea-local-db` 컨테이너를 시작한다.

```powershell
docker start fleaflea-local-db
docker exec fleaflea-local-db pg_isready -U postgres -d fleaflea_db
```

처음 설치한다면 팀의 로컬 DB 설정에 따라 PostgreSQL 16 컨테이너를 먼저 만든다. 현재 설정은 호스트의 `5434` 포트를 사용한다. PowerShell에서 백엔드를 띄울 때 `DB_URL`도 같은 포트로 맞춘다. 나머지 DB·AWS·JWT 환경 변수는 팀에서 받은 로컬 설정 값을 사용한다.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5434/fleaflea_db'
.\gradlew.bat bootRun
```

`http://localhost:8080/actuator/health`가 `UP`이면 아래 계정을 **각각 한 번만** 만든다. 이 계정은 로컬 부하 테스트용이다.

```powershell
$owner = @{ email = 'k6-local@example.test'; password = 'k6local1234'; nickname = 'k6-local' } | ConvertTo-Json
$requester = @{ email = 'k6-requester@example.test'; password = 'k6local1234'; nickname = 'k6-requester' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/signup' -ContentType 'application/json' -Body $owner
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/signup' -ContentType 'application/json' -Body $requester
```

500개의 도감 아이템, 100개의 플리마켓, 친구 관계, 도감 거래 요청 한 건을 넣는다.

```powershell
Get-Content .\performance\k6\seed-local.sql -Raw | docker exec -i fleaflea-local-db psql -v ON_ERROR_STOP=1 -U postgres -d fleaflea_db
```

도감 목록의 인덱스 효과를 비교하려면 `seed-large-local.sql`을 같은 방식으로 적용한다. 도감 아이템이 총 50,000개가 된다.

## 2. 조회 API 측정

로그인해서 받은 토큰을 현재 PowerShell 세션에 저장한다.

```powershell
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/login' -ContentType 'application/json' -Body (@{ email = 'k6-local@example.test'; password = 'k6local1234' } | ConvertTo-Json)
$env:K6_TOKEN = $login.accessToken
```

먼저 1 VU로 응답이 200인지 확인하고, 그다음 같은 API를 10 VU로 측정한다.

```powershell
k6 run -e VUS=1 -e DURATION=30s .\performance\k6\read-api.js

New-Item -ItemType Directory -Force .\performance\k6\results | Out-Null
k6 run -e VUS=10 -e DURATION=1m --summary-export=performance/k6/results/collection-items.json .\performance\k6\read-api.js
```

기본 대상은 내 도감 목록이다. 다른 API는 `ENDPOINT`와 필요한 ID를 지정한다.

| `ENDPOINT` | 요청 | 추가 변수 |
| --- | --- | --- |
| `collection-items` | 내 도감 목록 | 없음 |
| `member-collection-items` | 회원 도감 목록 | `OWNER_ID` |
| `collection-item` | 도감 상세 | `COLLECTION_ITEM_ID` |
| `markets` | 참여 플리마켓 목록 | 없음 |
| `market` | 플리마켓 상세 | `MARKET_ID` |
| `market-members` | 플리마켓 참여자 목록 | `MARKET_ID` |
| `collection-trade` | 도감 거래 요청 상세 | `TRADE_REQUEST_ID` |

예를 들어 플리마켓 상세는 `k6 run -e ENDPOINT=market -e MARKET_ID=1 .\performance\k6\read-api.js`로 실행한다. ID가 다르면 DB에서 확인해서 바꾼다. 다른 서버를 대상으로 할 때는 `-e BASE_URL=http://주소:포트`를 추가한다.

스크립트는 반복 사이에 1초 쉰다. 대기 시간을 바꾸려면 `-e THINK_TIME=0`처럼 지정할 수 있다. 전후 비교에서는 데이터 건수, VU, 실행 시간, 대기 시간을 동일하게 둔다.

## 3. 도감 거래 흐름 측정

요청자와 소유자 토큰을 각각 준비한다. 각 반복은 다른 아이템에 거래를 요청한 뒤 **수락→완료 / 거절 / 취소** 중 하나를 실행한다.

```powershell
$ownerLogin = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/login' -ContentType 'application/json' -Body (@{ email = 'k6-local@example.test'; password = 'k6local1234' } | ConvertTo-Json)
$requesterLogin = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/auth/login' -ContentType 'application/json' -Body (@{ email = 'k6-requester@example.test'; password = 'k6local1234' } | ConvertTo-Json)
$env:OWNER_TOKEN = $ownerLogin.accessToken
$env:REQUESTER_TOKEN = $requesterLogin.accessToken
$env:FIRST_ITEM_ID = '2001'
k6 run -e VUS=3 -e DURATION=30s .\performance\k6\collection-trade-workflow.js
```

재실행할 때는 아직 거래 요청에 사용하지 않은 `FIRST_ITEM_ID`를 고른다. 현재 데이터의 아이템 ID는 `1`~`50000`이다. 사용한 ID로 다시 실행하면 중복 요청으로 실패한다.

결과는 p95(`http_req_duration`), 실패율(`http_req_failed`), 요청 수(`http_reqs`)를 기록한다. 토큰과 `performance/k6/results/`의 원본 결과는 Git에 올리지 않는다.
