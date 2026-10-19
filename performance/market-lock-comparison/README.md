# 마켓 락 비교 (#186)

현재 상태: 2026-10-19 측정 완료. [결과와 해석](RESULTS.md). 운영 락은 변경하지 않았다.

## 비교 범위

동일 커밋의 `MarketService.updateMarket`만 바꾼 세 소스를 만든다.

| 버전 | 분산 락 | 마켓 행 조회 |
| --- | --- | --- |
| plain | 해당 수정 메서드만 제외 | 일반 조회 |
| db | 해당 수정 메서드만 제외 | PostgreSQL PESSIMISTIC_WRITE |
| redisson-db | 기존 그대로 | PostgreSQL PESSIMISTIC_WRITE |

분산 락 어노테이션은 유지하고 Aspect의 대상에서 수정 메서드만 제외한다. 따라서 기존 `market.change.execution` 계측은 세 버전에 남는다. 트랜잭션 어노테이션과 나머지 서비스는 동일하다. 일반 조회 버전에서 `market.locked.lookup`이 없는 것은 의도한 차이다. 이 지표는 조회 전체 시간이며 DB 락 순수 대기 시간은 아니다. DB 대기는 pg_stat_activity/pg_locks와 함께 해석한다. 이 실험만으로 참여·탈퇴·삭제의 락을 제거하지 않는다.

## 준비

```sh
python3 performance/market-lock-comparison/prepare.py --ref <고정한-커밋-SHA> --output /tmp/fleaflea-lock-186
```

출력 경로는 새 디렉터리이며 저장소 밖이어야 한다. 출력은 manifest.json에 커밋·변경 파일 SHA256을 기록한다. 각 소스에서 같은 Java 25/Gradle로 bootJar를 빌드한다. 운영 이미지 레지스트리나 배포 워크플로는 사용하지 않는다.

전용 데이터: 이미지 없는 동일 호스트 소유 마켓 21개와 호스트 토큰. 기존 market-lock 데이터 형식(accounts[0].token, markets[2].marketId, spreadMarkets 20개)을 사용한다. 토큰 파일은 git에 올리지 않는다. 운영 DB 스냅샷·실사용자 개인정보는 사용하지 않고 합성 데이터를 생성한다. 각 실행 전 동일 합성 데이터 스냅샷으로 복원한다. DB 테이블/필드 구성은 고정 커밋의 마이그레이션을 따른다.

## 실행 순서

1. 전용 앱/DB/Redis VM과 별도 부하 VM인지 확인한다. 운영 주소·DB·버킷을 사용하지 않는다.
2. 버전별로 제목·설명 100쌍 동시 수정 후 값 보존을 확인한다. 단일 VU가 두 HTTP 요청을 batch 전송하며 쌍 사이에는 경합이 없다. 일반 조회에서 우연히 모두 통과해도 안전성을 증명하지 못한다.
3. 커밋 중 다른 트랜잭션 대기, 롤백 후 대기자 진행은 PostgreSQL 통합 테스트로 별도 검증한다. HTTP 200만으로 롤백 경합을 검증했다고 기록하지 않는다.
4. 각 조건 30초 워밍업(결과 제외) 후 60초 측정. 요청률 20/100 RPS, 조회 80%·수정 20%, 한 마켓/20마켓, 3회 반복. 버전 순서는 plain→db→redisson-db, db→redisson-db→plain, redisson-db→plain→db로 교대한다. 각 실행 사이 요청 종료와 DB 초기화를 확인한다.
5. k6 summary JSON, 앱/DB/부하 VM 자원, Hikari 대기, 락 지표를 같은 타임스탬프로 보관한다. 성공 수정 건수/초, 쓰기 p95/p99, 409/503/500, dropped_iterations를 나눠 비교한다. 부하 VM 포화/예약 누락 발생 조건은 공정한 처리량 비교에서 제외하고 별도 표기한다.
6. 결과와 한계 기록 후 두 VM 중지 확인. 운영 변경은 별도 PR로 검토한다.

```sh
export BASE_URL=http://10.0.0.10:8080
export TEST_ORIGIN="$BASE_URL"
export CONFIRM_ISOLATED_TEST=yes
export DATA_FILE=/secure/market-lock-test-data.json
k6 run -e BASE_URL -e TEST_ORIGIN -e CONFIRM_ISOLATED_TEST -e DATA_FILE preservation.js
k6 run -e BASE_URL -e TEST_ORIGIN -e CONFIRM_ISOLATED_TEST -e DATA_FILE \
  -e RATE=20 -e DISTRIBUTION=single -e RUN_ID=db-single-20-r1 \
  -e SUMMARY_PATH=/results/db-single-20-r1.json load.js
```

명령은 이 문서 디렉터리에서 실행한다. 가드는 실수 방지용이며 사설 주소 자체가 테스트 서버임을 보장하지 않으므로 VM ID와 환경도 확인한다. 20 RPS 예열 시 DURATION=30s와 별도 결과 경로를 사용한다. 100 RPS도 해당 요청률로 예열한다.

## 비용·중단 기준 제안

기존 테스트 VM 2대만 최대 2시간 사용. 크기 변경·새 VM 생성은 제외한다. 사용 요금은 기존 VM 사양을 조회한 뒤 확인하고 기동한다. 5xx 5% 이상 30초 지속, 메모리 90% 이상/반복 OOM, 테스트 데이터 외 쓰기 발견 시 중단한다. 운영 장애 시험은 없다. 이 문서 작성은 VM 기동 승인이 아니다.

## 완료 전 남은 일

- [x] VM 사양·현재 상태, 합성 데이터 준비/스냅샷 복원 명령 확인
- [x] 버전별 빌드 및 커밋/롤백 경합 통합 검증
- [x] 비용·실행 시간 확인 후 기동, 전체 비교 실행
- [x] 결과 위키와 #186 갱신, VM 중지

## 2026-10-19 실행 환경

- 고정 커밋: `efc15a702930afc2f7577656829e9161ac9611b7` (2026-10-19 원격 main과 동일함을 확인).
- 앱/DB VM `fleaflea-perf-server`, 부하 VM `fleaflea-k6-runner`: 각각 e2-standard-2, asia-northeast3-a.
- 앱 2개: 각각 1 vCPU/1 GiB, Xms256m/Xmx768m. PostgreSQL·Redis·MinIO와 앱 VM 자원을 공유하므로 운영 용량으로 일반화하지 않는다.
- 이미지 없는 마켓 21개 생성 후 전용 DB 덤프 고정. 버전 전환 때 복원하고 각 조건 시작 전 대상 21개 마켓의 제목·설명을 같은 값으로 초기화한다. 부하 중 수정 필드는 제목뿐이다.
- 테스트 nginx는 기존 사설 HTTPS 주소를 사용한다. k6에서만 TEST_SELF_SIGNED_TLS=yes를 명시한다. 운영 도메인은 가드에서 거절한다.
- OpenTelemetry 에이전트 없이 동일 JRE 이미지에서 세 jar를 바인드 마운트한다. Micrometer 지표는 두 앱에서 각각 수집한다.
- transaction-probe.py: 외부 PostgreSQL 트랜잭션이 설명 변경 후 2초 보유하는 동안 실제 API가 제목을 수정한다. COMMIT과 ROLLBACK 후 최종 두 필드를 검사한다. 애플리케이션 내부에서 예외를 주입하는 테스트와는 구분한다.
- collect.py는 Docker 자원·DB wait event·두 앱 Prometheus를 수집한다. before/after 지표 차이로 각 측정 구간을 비교한다.
- 메모리 90% 중단 감시는 별도 watchdog, 서버 5xx는 k6 누적 오류율을 30초 이후 평가한다. 30초 이동 창을 구현한 것은 아니다.
- 첫 Redisson 사전 검증은 두 검증이 같은 마켓을 동시에 사용해 결과가 오염되었으므로 제외한다. 순차 재검증 및 본 행렬 결과만 사용한다.

실행 VM의 JRE는 Temurin 25.0.4.1+1, PostgreSQL 16.15, Redis 7.4.11이다. 재현용 compose.override.yml은 기존 /opt/fleaflea-perf/compose.yml에 더하는 파일이며, VM의 lock186/compose.yml과 내용이 같다. 저장소 스크립트는 compose.override.yml 이름을 사용한다. bootstrap.py는 기존 합성 계정으로 실제 로그인하고 토큰을 파일에만 저장한다.

집계: analyze.py로 반복별 CSV를 만든 뒤 analyze-locks.py로 두 앱의 전후 타이머 건수 합계를 성공 수정 건수와 대조한다. 종료 스냅샷이 없는 DB 락 단독 한 구간은 --recover-from-samples로 실제 주기 수집 원본에서 종료 건수가 일치하는 샘플을 찾는다. 시작 스냅샷 뒤 180초 이내로 제한하고 원본 UTC·요청 건수·출처를 recovery.json에 남긴다. Redis 계측이 없는 주기 원본으로 Redisson 구간을 복원하지 않는다.
