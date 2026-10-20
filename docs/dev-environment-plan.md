# 개발 환경과 운영 환경 분리

관련 이슈: [#208](https://github.com/hyundai-autoever-team3/fleaflea-backend/issues/208)

2026-10-20에 저장소와 GCP 리소스를 확인했다. 이 문서는 확인된 구성과 적용 순서다.
이후 초기 구성을 적용했다. [실제 적용 결과와 남은 작업](dev-environment-setup.md)을 함께 확인한다.

## 최초 조사 당시 구성

| 구분 | 확인한 내용 |
| --- | --- |
| 프로젝트 / 존 | `<GCP_PROJECT_ID>` / `<GCP_ZONE>` |
| 운영 | `<PRODUCTION_VM_NAME>`, 실행 중, `e2-standard-2`, 40 GiB pd-balanced |
| dev 재사용 후보 | `<DEV_VM_NAME>`, 중지 중, `e2-standard-2`, 50 GiB pd-balanced |
| 부하 발생기 | `<LOAD_GENERATOR_VM_NAME>`, 중지 중, `e2-standard-2`, 30 GiB pd-balanced |
| 후보 VM 네트워크 | `<DEV_VPC_NAME>`, 내부 주소 `<DEV_PRIVATE_IP>` |
| 후보 VM 서비스 계정 | 없음 |
| 후보 VM 외부 주소 | 임시 외부 IP용 설정만 있고, 예약된 고정 IP 없음 |
| 후보 VM 인바운드 | IAP SSH, k6 VM의 `<LOAD_GENERATOR_PRIVATE_CIDR>`에서 오는 HTTPS만 허용 |
| 버킷 | 운영 이미지 / 운영 백업 버킷만 확인됨 |
| 원격 `develop` 브랜치 | 없음 |
| CI | `main` 대상 PR과 push에서 실행 |
| CD | `main`의 CI 성공 또는 `main`에서 수동 실행 시 운영 배포 |

기존 성능 테스트 서버를 재사용하면 새 VM과 디스크를 추가하지 않아도 된다.
다만 지금 방화벽으로는 팀원의 브라우저가 dev API에 접속할 수 없다.
VM을 켜면 기존 테스트 컨테이너가 자동으로 시작할 가능성도 있으므로,
첫 기동 직후 Docker 컨테이너와 자동 실행 설정부터 확인한다.

## 구성 제안

서버 재사용, 브랜치, 주소는 사용자 확인을 받았다. 운영 시간과 비용, 외부 설정 담당자는 적용 전에 확정한다.

| 항목 | 제안 |
| --- | --- |
| 서버 | `<DEV_VM_NAME>` 재사용, 기존 사양 유지 |
| 부하 발생기 | `<LOAD_GENERATOR_VM_NAME>`는 계속 중지 |
| 개발 브랜치 | `develop` → dev, 검증 후 PR로 `main`에 반영 |
| 프론트 | `<DEV_FRONTEND_URL>` |
| API | `<DEV_API_URL>` |
| DB | 같은 dev VM 안의 PostgreSQL, dev 전용 DB와 사용자 |
| Redis | 같은 dev VM 안의 별도 Redis, dev 전용 비밀번호 |
| 이미지 | dev 전용 GCS 버킷과 런타임 서비스 계정 |
| 배포 이미지 저장소 | 운영과 별개인 `fleaflea-dev` Artifact Registry repository |
| 초기 데이터 | 테스트 계정과 테스트 마켓. 운영 DB 복제는 하지 않음 |
| 배포 방식 | dev 전용 Compose/CD. 운영 배포 스크립트는 유지 |

dev를 사용하기 시작한 뒤에는 같은 VM에서 성능 시험을 함께 돌리지 않는다.
dev를 중지하고 테스트용으로 전환할 때도 dev DB 볼륨과 테스트 DB 볼륨을 구분한다.

## 그대로 복사하면 안 되는 설정

`deploy/gcp/compose.app.yml`에는 `APP_ENV=production`, 운영 DB 이름,
`/etc/fleaflea/*.env`, `fleaflea_default` 네트워크가 들어 있다.
`deploy.sh`와 복구 도구도 `/opt/fleaflea`, `/etc/fleaflea`, 운영 슬롯 상태 파일을 사용한다.

dev 설정은 별도 경로와 Compose 프로젝트를 사용한다.

| 항목 | dev에서 분리할 내용 |
| --- | --- |
| 서버 파일 | `/opt/fleaflea-dev`, `/etc/fleaflea-dev` |
| 컨테이너와 볼륨 | `fleaflea-dev` 프로젝트와 전용 볼륨 |
| DB 주소 | dev PostgreSQL의 전용 DB. 운영 사설 IP를 사용하지 않음 |
| Redis | dev 컨테이너, 비밀번호, 채팅 채널 구분 |
| 환경 | `APP_ENV=development`, 배포 SHA를 `APP_VERSION`에 기록 |
| 이미지 | `IMAGE_STORAGE=gcs`, dev 버킷만 허용 |
| URL | `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`, `SWAGGER_SERVER_URL`을 dev 주소로 설정 |
| 인증 | dev 전용 JWT / 이메일 인증 해시 비밀값 생성 |
| 메일 | dev 발송 계정과 사용 범위 결정. 운영 SMTP 비밀값을 자동 복사하지 않음 |
| OAuth | dev 전용 클라이언트 또는 공급자 정책에 맞는 dev 콜백 등록 |
| 관측 | dev 환경 라벨, 로그·지표·오류 수집 환경 구분 |

현재 Refresh Token 쿠키에는 `Domain`이 없어 호스트 단위로 저장된다.
dev API에도 HTTPS가 필요하며, 운영과 공유하는 상위 도메인 쿠키를 추가하지 않는다.
JWT 비밀값과 DB·Redis도 분리해 운영 토큰이 dev에서 통하지 않게 한다.

## IAM과 네트워크

1. dev VM 런타임 서비스 계정을 별도로 만든다.
2. 이미지 권한은 dev 버킷에만 부여한다. 서명 URL에 필요한 서명 권한도 별도 확인한다.
3. dev Artifact Registry repository를 만들고 런타임 계정에 해당 repository의 읽기 권한만 부여한다.
4. GitHub dev 배포 계정과 WIF 조건을 별도로 설정한다. `develop`에서 운영 배포 계정을 사용할 수 없어야 한다.
5. dev 배포 계정은 dev VM의 OS Login/IAP 대상만 허용하도록 구성한다.
   기존 운영 배포 계정에 부여된 프로젝트 역할을 그대로 복제하지 않는다.
6. dev용 고정 IP와 DNS를 연결하고 TLS 인증서를 발급한다.
7. dev 대상 태그에만 80/443을 허용한다. 80은 인증서 발급과 HTTPS 리다이렉트에 사용한다.
8. DB·Redis·관리 포트는 외부에 공개하지 않는다.

기존 운영 서비스 계정과 버킷은 dev VM에 연결하지 않는다.
프로젝트 수준 권한과 운영 버킷의 실제 IAM까지 대조한 뒤 권한 분리가 된 것으로 판단한다.

## 배포 파일 작업 순서

1. `deploy/gcp/dev/`에 인프라·앱 Compose와 비밀값 없는 환경변수 예제를 추가한다.
2. dev 환경변수가 누락되거나 운영 주소·버킷을 참조하면 배포 전에 실패하도록 검증한다.
3. dev 전용 배포 스크립트에서 이미지 digest로 배포하고 `/readyz`를 확인한다.
4. CI 대상에 `develop`의 PR과 push를 추가한다.
5. `gcp-dev-cd.yml`을 추가한다. 같은 저장소의 `develop` push에 대한 CI 성공만 자동 배포 대상으로 삼는다.
6. GitHub `development` Environment에 dev 전용 변수와 WIF 설정을 등록한다.
7. 이미지 태그는 `dev-candidate` / `dev-current` / `dev-rollback`으로 나눈다.
   운영의 `prod-*` 태그와 배포 concurrency 그룹을 공유하지 않는다.
8. dev 초기 배포를 수동 확인한 뒤 자동 배포를 활성화한다.

실제 서비스는 운영처럼 GCS를 사용해 이미지 서명과 썸네일도 같은 조건에서 확인한다.
Flyway `V1__init.sql`부터 `V23__create_item_comments.sql`까지 빈 dev DB에 적용하고,
`DDL_AUTO=validate`로 검사한다. 빈 DB에서 전체 마이그레이션 성공 여부는 첫 dev 기동 때 검증한다.
운영 DB의 schema나 비밀값을 가져오는 것으로 초기화를 대신하지 않는다.

## 비용 검토

VM을 재사용해도 중지 상태에서 상시 실행으로 바꾸면 컴퓨팅 요금이 추가된다.
운영처럼 24시간 켤지, 개발 시간에만 켤지 먼저 정한다.

시간당 VM 요금을 `r` USD라고 하면 다음처럼 계산한다.

| 운영 시간 | VM 컴퓨팅 예상액 |
| --- | --- |
| 상시 실행, 월 730시간 가정 | `730 × r` USD |
| 하루 10시간, 월 22일 가정 | `220 × r` USD |

계획 단계에서 `r = 0.09 USD`를 예산 가정으로 잡으면 각각 약 65.70 / 19.80 USD다.
이 값은 확정 견적이 아니다. 적용 전에 계산기에서 서울 리전, `e2-standard-2`, Linux,
온디맨드를 선택해 실제 표시 금액을 기록한다.

- 사용 중인 일반 VM 외부 IPv4는 공식 표 기준 시간당 0.005 USD다.
  고정 IP가 VM에 연결돼 있으면 VM이 중지돼 있어도 사용 중으로 계산한다.
- 기존 50 GiB pd-balanced 디스크 요금은 현재도 발생한다. 재사용 시 추가 디스크를 만들지 않는다.
- GCS 저장·요청, 외부 전송, 이미지 보관, 로그, 필요 시 스냅샷 비용은 별도다.
- 원화 환산과 세금, 계정 할인은 여기 계산에 포함하지 않았다.

확인 자료:

- [Compute Engine 요금](https://cloud.google.com/products/compute/pricing/general-purpose)
- [외부 IP와 네트워크 요금](https://cloud.google.com/vpc/network-pricing)
- [Google Cloud 계산기](https://cloud.google.com/products/calculator)

## 첫 적용 때 확인할 것

1. 테스트 결과 원본이 보존돼 있는지 확인하고 기존 컨테이너·볼륨 목록을 기록한다.
2. dev 전용 경로에 DB·Redis를 구성한다. 기존 테스트 볼륨을 삭제하지 않는다.
3. IAM·DNS·TLS·비밀값을 준비하고 dev API를 배포한다.
4. 프론트 담당자가 dev 배포와 API 주소, OAuth 콜백을 연결한다.
5. 로그인·재발급·이미지 업로드/목록/상세·댓글/대댓글·채팅을 테스트 데이터로 확인한다.
6. dev에서 만든 데이터가 운영 조회에 나오지 않고, 운영 토큰이 dev에서 거절되는지 확인한다.
7. dev 재배포와 재시작 후에도 dev 데이터가 유지되는지 확인한다.
8. 운영의 활성 이미지·슬롯·환경변수·데이터가 변경되지 않았는지 확인한다.

서버·데이터 저장소·IAM·DNS·HTTPS 초기 구성과 백엔드 기능 검증을 마쳤다.
자동 배포는 `DEV_DEPLOY_ENABLED=false`로 유지한다.
프론트 dev 배포, 실제 OAuth 클라이언트, PR 병합 후 Actions 배포 검증과 상시 운영 시간 결정은 남아 있다.

실제 프로젝트·VM·IP·버킷·서비스 계정·WIF·이미지 주소는 공개 문서에 기록하지 않는다.
GitHub Environment와 서버의 비공개 설정에서 관리한다.
