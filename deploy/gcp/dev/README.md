# dev 배포 준비

[구성 조사와 적용 계획](../../../docs/dev-environment-plan.md)

서버는 `<DEV_VM_NAME>`, 브랜치는 `develop`, 주소는
`<DEV_FRONTEND_HOST>` / `<DEV_API_HOST>`으로 정했다.
2026-10-20에 초기 서버 구성과 HTTPS 연결을 검증했다.
운영 시간은 아직 정하지 않았으며, 이번 초기 구성 후 VM은 중지한다.
dev CD는 `DEV_DEPLOY_ENABLED=false`로 비활성화해 두었다.

## 운영과 분리되는 부분

- Compose 프로젝트·네트워크·볼륨은 `fleaflea-dev` 전용이다.
- PostgreSQL과 Redis는 별도 컨테이너로 구동하고 외부 포트를 열지 않는다.
- 앱은 dev DB와 Redis 주소를 명시하며, 운영의 env 파일을 참조하지 않는다.
- API 포트는 loopback `18080`만 사용한다. 관리 포트는 publish하지 않는다.
- 인증 메일은 dev Mailpit에만 수집한다. 외부 수신자에게 발송하지 않는다.
  Mailpit 화면은 loopback `18025`이며 SSH 터널로 접근한다.
- GCS와 OAuth·메일·인증 비밀값은 dev 전용으로 채운다.
- 빈 DB에 Flyway V1~V23을 적용하고 Hibernate가 schema를 검증한다.
  첫 기동에서 전체 마이그레이션 성공을 확인하며 기존 테스트 schema를 재사용하지 않는다.

## 환경 파일 준비

`runtime.env.example`은 필요한 키 목록이다. 값은 서버의
`/etc/fleaflea-dev/runtime.env` 같은 비공개 파일에 저장한다.
`APP_IMAGE`는 승인한 Artifact Registry 이미지의 `@sha256:` digest로 채우고,
`APP_VERSION`은 해당 소스 commit SHA로 기록한다.
운영 `.env`를 복사해서 일부 값만 수정하는 방식은 사용하지 않는다.
OAuth 클라이언트를 아직 만들지 않았다면 식별 가능한 dev 준비용 값을 사용하고,
소셜 로그인은 검증 완료로 표시하지 않는다. 실제 dev 콜백 등록 후 별도로 확인한다.

이미지 서명은 VM의 dev 서비스 계정으로 수행한다. JSON 서비스 계정 키를 저장소에 넣지 않는다.
GCS 버킷 이름만 다르게 설정하는 것으로 IAM 분리가 완료되는 것은 아니므로
서비스 계정의 운영 버킷 접근 권한도 따로 확인한다.

구성 문법 확인은 다음 명령으로 한다. 비밀값이 출력되지 않도록 `--quiet`를 사용한다.

```bash
docker compose --env-file /etc/fleaflea-dev/runtime.env \
  -f deploy/gcp/dev/compose.yml config --quiet
```

## GitHub 설정

저장소 변수 `DEV_DEPLOY_ENABLED`는 초기 구성이 끝나고 수동 배포할 때만 `true`로 설정한다.
설정하지 않으면 수동 실행과 자동 실행 모두 건너뛴다.
`development` Environment는 `develop`과 `main` ref만 허용한다.
`workflow_run`은 기본 브랜치 `main`의 워크플로로 실행되므로 main ref도 필요하다.
실제 배포 대상 소스는 job 조건에서 성공한 develop push로 제한한다.
수동 배포는 develop에서만 가능하다. WIF도 이 두 실행 경로와 워크플로 파일을 제한한다.
dev WIF pool은 운영 pool과 별도로 만들어 운영 계정의 바인딩을 공유하지 않는다.

| dev Environment 변수 | 값 |
| --- | --- |
| `DEV_GCP_PROJECT_ID` | `<GCP_PROJECT_ID>` |
| `DEV_GCP_ZONE` | `<GCP_ZONE>` |
| `DEV_GCP_INSTANCE` | `<DEV_VM_NAME>` |
| `DEV_GCP_INSTANCE_ID` | `<DEV_VM_ID>` |
| `DEV_GCP_SSH_HOST_KEY` | 첫 서버 접속 시 별도 확인한 해당 VM 호스트 공개 키 |
| `DEV_GCP_IMAGE_REPOSITORY` | `<DEV_IMAGE_REPOSITORY>` |
| `DEV_GCP_WORKLOAD_IDENTITY_PROVIDER` | develop 전용 조건을 설정한 WIF provider |
| `DEV_GCP_DEPLOY_SERVICE_ACCOUNT` | 새 dev 배포 서비스 계정 |

`gcp-dev-cd.yml`은 CI가 성공한 develop commit을 빌드한다.
`deploy.sh`도 metadata의 VM 이름과 프로젝트를 확인한 뒤 배포를 시작한다.
서버 경로는 `/opt/fleaflea-dev/{compose.yml,validate.py,deploy.sh}`다.
이미지 registry 로그인은 VM 런타임 서비스 계정으로 수행한다.
Artifact Registry도 운영 `fleaflea`와 별개인 `fleaflea-dev` repository를 사용한다.
dev 계정은 운영 repository에 권한을 갖지 않는다.
`artifact-cleanup.json`은 dev repository에만 적용하며 active/rollback 이미지를 보존한다.
운영 repository의 정리 정책은 변경하지 않는다.

배포 중에는 짧은 dev API 중단이 발생할 수 있다. 상태 검사 실패 시 직전 앱 이미지를
다시 구동하며, DB 마이그레이션은 자동으로 되돌리지 않는다. 이전 앱과 호환되지 않는
schema 변경은 별도 복구 계획이 필요하다.

`nginx.conf`는 DNS와 인증서를 준비한 뒤 dev VM에만 설치한다.
기존 성능 시험용 virtual host와 충돌 여부를 확인하고 `nginx -t` 후 reload한다.
첫 인증서 발급 시에는 HTTP ACME location부터 활성화하고 TLS server 블록을 추가한다.

기존 성능 시험 컨테이너의 자동 시작을 정리하고 결과 원본·볼륨을 보존한다.
이후 dev DB와 Redis를 먼저 구동하고 앱을 배포해 Flyway와 `/readyz`를 확인한다.
운영 버킷 접근 거절, 운영 토큰 거절, 로그인·이미지·댓글·채팅 검증 후 자동 배포를 사용한다.

실제 프로젝트·VM·IP·버킷·서비스 계정·WIF·이미지 주소는 공개 문서에 기록하지 않는다.
GitHub Environment와 서버의 비공개 설정에서 관리한다.

## 비공개 배포 정책

`deployment-policy.example.json`의 빈 값을 채워 서버에 `/etc/fleaflea-dev/deployment-policy.json`으로 설치한다.
소유자는 root, 권한은 600으로 설정한다. 정책 파일과 실제 값은 Git에 추가하지 않는다.
정책은 승인된 VM 이름·ID·프로젝트·이미지 저장소·버킷·프론트/API 주소를 고정한다.
GitHub 변수와 정책이 맞지 않거나 정책이 없으면 배포를 거절한다.

새 서버를 구성할 때는 이 정책과 새 validate.py를 먼저 설치한다.
현재 dev 서버에는 설치와 정책 검증을 마쳤지만 VM 운영 시간과 Actions 배포 검증은 남아 있다.
그 전까지 자동 배포를 활성화하지 않는다.
Nginx 템플릿은 서버에서 실제 호스트를 설정한 뒤 다음처럼 해당 변수만 치환한다.

```bash
export DEV_API_HOST=api-dev.example.invalid
envsubst '${DEV_API_HOST}' < deploy/gcp/dev/nginx.conf > /tmp/dev-nginx.conf
```

예제 도메인을 실제 비공개 운영 설정으로 바꾼 뒤 설치하고 `nginx -t`로 검사한다.
다른 Nginx 변수는 치환하지 않는다.

## 워크플로 실행 구조

`.github/workflows/gcp-dev-cd.yml`은 실행 조건·권한·인증 Action·단계 연결을 관리한다.
셸 명령은 `workflow.sh`에 두고 YAML에서 다음처럼 실행한다.

```yaml
run: bash deploy/gcp/dev/workflow.sh deploy
```

`workflow.sh`의 `validate`, `build`, `push`, `deploy`는 GitHub runner에서 실행된다.
VM으로 전달한 `deploy.sh`는 서버에서 대상 정책 검사·상태 확인·실패 복구를 수행한다.
실제 인프라 값은 계속 GitHub Environment와 서버의 비공개 정책 파일에서 관리한다.
