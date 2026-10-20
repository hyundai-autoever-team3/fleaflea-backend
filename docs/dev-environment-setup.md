# dev 서버 초기 구성 결과

관련 이슈: [#208](https://github.com/hyundai-autoever-team3/fleaflea-backend/issues/208)

2026-10-20에 기존 테스트 VM 한 대를 사용해 dev 백엔드를 구성했다.
운영 데이터는 복제하지 않았고, 테스트 계정과 마켓을 새로 만들었다.
VM은 11:31~12:06 KST 약 35분 사용한 뒤 중지했다.
최종 상태는 dev VM과 k6 runner 모두 `TERMINATED`, 운영 VM은 `RUNNING`이다.
초기 구성용 startup script 메타데이터도 제거했다.

## 만든 구성

| 항목 | 적용 값 |
| --- | --- |
| VM | `<DEV_VM_NAME>`, 서울 `<GCP_ZONE>`, 기존 e2-standard-2 / 50 GiB 디스크 |
| 고정 IP | `<DEV_STATIC_IP>` |
| API | `<DEV_API_URL>` |
| Swagger | `<DEV_API_URL>/swagger-ui/index.html` |
| DNS | 가비아 `api-dev` A 레코드, TTL 1800 |
| HTTPS | Let's Encrypt 인증서, 2027-01-18 만료, certbot timer와 Nginx 갱신 hook |
| DB | dev PostgreSQL 컨테이너, `fleaflea_dev` DB와 사용자 |
| Redis | dev Redis 컨테이너, 별도 비밀번호와 dev 채팅 채널 |
| 이미지 버킷 | `<DEV_GCS_BUCKET>`, uniform access / public access prevention |
| 이미지 저장소 | `<DEV_IMAGE_REPOSITORY>` |
| 실행 계정 | `<DEV_RUNTIME_SERVICE_ACCOUNT>` |
| 배포 계정 | `<DEV_DEPLOY_SERVICE_ACCOUNT>` |
| WIF pool / provider | `<DEV_WIF_POOL>` / `fleaflea-dev` |
| GitHub | `develop` 브랜치, `development` Environment, dev 변수 8개 |
| 자동 배포 | 저장소 변수 `DEV_DEPLOY_ENABLED=false` |

실행 계정은 dev 버킷의 객체 처리, 자기 계정의 URL 서명,
dev 이미지 repository 읽기 권한만 사용한다.
배포 계정의 이미지 쓰기는 dev repository에만 부여했다.
IAP SSH는 비공개 정책에 지정한 dev 대상의 SSH 포트에만 허용하고, OS Admin Login은 dev VM에만 허용했다.
VM 조회를 위한 Compute Viewer는 프로젝트 범위의 읽기 권한이다.

운영과 dev의 WIF pool도 다르다. dev provider를 운영 pool에 추가하면
기존 principalSet 바인딩과 겹칠 수 있어 별도 pool을 만들었다.
`workflow_run`은 기본 main ref에서 실행되므로 해당 dev CD 파일과 이벤트만 허용하고,
실제 소스는 워크플로 job에서 성공한 develop push로 제한한다.

## 배포한 앱

초기 배포는 이미 검증된 main 이미지의 내용을 그대로 dev repository로 복사했다.
두 repository의 이미지 digest가 같은지 확인했고, 운영 앱을 다시 빌드하거나 배포하지 않았다.

- 소스: `<SOURCE_COMMIT_SHA>`
- dev 이미지 digest: `<DEPLOYED_IMAGE_DIGEST>`
- 서버 파일: `/opt/fleaflea-dev`
- 비밀값 파일: `/etc/fleaflea-dev/runtime.env`, root만 읽기 가능
- 복구 상태: `/etc/fleaflea-dev/current-image`, `current-version`, `rollback-image`, `rollback-version`

DB·Redis·JWT·이메일 인증 해시 비밀값은 dev 서버에서 새로 생성했다.
운영 `.env`나 사용자 데이터를 복사하지 않았다.
OAuth 클라이언트는 준비용 값이므로 소셜 로그인을 사용할 수 있는 상태는 아니다.
인증 메일은 외부 발송 대신 dev Mailpit에 수집하며, 이메일 가입과 검증 API는 확인했다.

기존 성능 시험 컨테이너는 중지하고 자동 재시작을 해제했다.
기존 볼륨과 시험 결과는 보존했다. k6 runner는 켜지 않았다.

## 확인한 동작

| 확인 항목 | 결과 |
| --- | --- |
| 빈 DB 초기화 | Flyway V1~V23 성공, Hibernate validate 통과 |
| HTTPS / readiness / Swagger | 정상 인증서, `/readyz`와 Swagger 200 |
| 이메일 인증 → 가입 → 로그인 | Mailpit 수집부터 검증 토큰 소비, 가입, 로그인, 내 정보 조회까지 통과 |
| Refresh Token | Secure / HttpOnly / host-only 쿠키, 재발급 성공, 이전 토큰 재사용 401 |
| 마켓과 상품 | dev 데이터 생성과 조회 성공 |
| 이미지 | dev GCS 업로드, 원본 서명 URL 다운로드, 목록 썸네일 확인 |
| 댓글 | 일반 댓글·대댓글 생성, 수정, 조회, 부모 삭제 후 두 행 삭제 확인 |
| 친구·채팅 | 친구 요청/수락, 채팅방 생성, HTTPS WebSocket/STOMP 인증, Redis 경유 메시지 수신·ACK·이력 확인 |
| CORS | dev 프론트 Origin 허용, 운영 프론트 Origin 거절 |
| 운영 버킷 접근 | dev 실행 계정으로 운영 이미지·백업 버킷 조회 모두 403 |
| 운영 이미지 repository | dev 실행 계정의 download/upload 권한 없음 |
| dev 이미지 repository | dev 실행 계정은 download만 허용 |
| 앱 재배포 | 전용 이미지 repository로 옮겨 재배포 후 readiness와 DB 데이터 보존 확인 |

16×16의 아주 작은 테스트 PNG는 썸네일이 더 커져 원본을 사용했다.
이는 기존 코드의 정상 동작이다. 이후 640×640 테스트 이미지로 썸네일 생성을 확인했다.

테스트 계정과 fixture는 dev DB에만 있다. 비밀번호·토큰·인증 코드는 문서나 Git에 넣지 않았다.
백엔드 기능은 API와 WebSocket으로 확인했고, 실제 dev 프론트 화면과 OAuth는 아직 검증하지 않았다.

배포 검증에는 기존 운영 배포 테스트 14건을 Linux에서 실행했다.
최초 dev 배포 테스트 10건은 Linux에서 통과했다.
식별값 분리 이후에는 대상 VM ID 불일치 검사도 추가했으며 별도 검증 결과를 PR에 기록한다.
dev는 운영 VM 배포 거절, 이미지 digest/운영 repository 검사, 배포 실패 복구,
포트·DB·Redis·GCS·메일 설정 검사를 포함한다.
Mac에서 기존 Linux 테스트가 `flock` 부재로 실패한 부분은 Linux에서 통과했다.

## 남은 작업

- [ ] 다음 승인된 기동 때 비공개 deployment-policy.json과 새 검증기를 먼저 설치

- [ ] 이 변경 PR 검토·병합 후 develop에 반영
- [ ] GitHub dev CD를 실제 Actions로 실행해 WIF → SSH → 배포까지 확인
- [ ] 프론트 담당자가 `<DEV_FRONTEND_HOST>` 배포를 만들고 dev API에 연결
- [ ] dev 전용 OAuth 클라이언트와 콜백 등록 후 소셜 로그인 확인
- [ ] 상시 실행 / 개발 시간 실행 중 운영 시간 결정
- [ ] 개발 프론트까지 연결한 뒤 #208 최종 완료 처리

자동 배포를 활성화하기 전에 VM 실행 시간부터 정한다.
VM을 중지하면 API에 접속할 수 없다. 고정 IP·디스크·버킷·이미지 저장소는 보존되며,
컴퓨팅을 중지해도 해당 저장소와 고정 IP 요금은 남는다.
재기동은 별도 사용 시간 승인 후 진행한다.

실제 프로젝트·VM·IP·버킷·서비스 계정·WIF·이미지 주소는 공개 문서에 기록하지 않는다.
GitHub Environment와 서버의 비공개 설정에서 관리한다.

## 공개 설정 정리 후 적용 조건

실제 인프라 식별값은 문서·CI/CD·테스트·Nginx에서 제거했다. 테스트는 가상 주소만 사용한다.
배포 대상 고정 검증은 root 소유 비공개 정책 파일로 옮겼다.
운영 이미지 정리 작업은 활성 슬롯에 기록된 이미지에서 저장소를 읽고,
모니터링은 비공개 대상 VM 값으로 규칙을 렌더링한다.
모니터링 Compose를 다음에 실행할 때는 `GCP_ALERT_RULES_FILE`도 먼저 지정해야 한다.
실행 중인 서버의 파일은 이번 소스 정리로 바뀌지 않는다.
