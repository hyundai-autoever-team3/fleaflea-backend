# 상품·도감 원본 이미지 캐시

## 적용 범위와 동작

GCS의 `items/`, `collection-items/` 아래 jpg/png/webp 원본을 새로 업로드할 때 `private, max-age=300, must-revalidate`를 저장한다. 서명 URL 유효 시간이 300초보다 짧으면 그 시간으로 줄인다. 프로필·마켓 사진, S3/MinIO, 버킷 공개 권한은 변경하지 않는다. 복사는 기존처럼 원본 메타데이터를 상속한다. 원본에 이전 정책이 남아 있으면 복사본도 기존 정책을 사용할 수 있어 기존 객체 갱신 후 다시 확인한다.

브라우저 캐시는 이미지 수신 시점부터 계산한다. 따라서 서명 URL의 남은 유효 시간과 정확히 일치하지 않으며, URL 만료나 도감 공개 설정 변경 뒤에도 이미 받은 사진이 최대 max-age 동안 재사용될 수 있다. `private`는 공유 캐시를 제한하는 지시어이며, `must-revalidate`는 오래된 캐시의 재검증 조건이지 즉시 접근 회수 기능이 아니다. 즉시 재검증이 필요하면 max-age=0, 저장 자체를 막으려면 no-store 정책이 필요하다. 이 PR은 최대 5분의 브라우저 재사용을 허용한다.

키는 기존 UUID 형식을 유지하고 원본 교체 시 새 키를 사용한다. API가 같은 서명 URL을 반환할 때 브라우저 캐시 재사용을 기대할 수 있다. 다른 서버 인스턴스에서 URL이 달라지거나 URL이 갱신되면 브라우저가 새로 요청할 수 있다. 사진 용량 감소와 최초 다운로드 개선은 별도 썸네일 PR #195의 범위다.

## 서버에서 따로 설정할 항목

기본값 사용 시 앱 환경변수나 Nginx 변경은 없다. GCS 응답의 Cache-Control은 객체 메타데이터에서 결정되므로 앱 API 응답 헤더를 바꿔서는 해결되지 않는다. 새 원본은 앱 배포 후 적용되고 기존 원본은 아래 명령으로 한 번 갱신한다. 자동 배포에서 유지보수 명령을 실행하지 않는다.

SSH는 필수가 아니다. Java 25와 ADC가 준비된 관리 환경에서 실행할 수 있다. 서버에서 실행하려면 저장소와 Gradle 실행 환경이 필요하다. 운영 앱 컨테이너는 실행 jar만 포함하므로 그 안에서 `./gradlew`를 실행할 수 있다고 가정하지 않는다. 이 문서의 명령은 앱 `.env`를 읽지 않는다.

필요 권한은 대상 버킷의 객체 목록·조회·메타데이터 수정이다. 공개 권한 변경, 원본 삭제·새 콘텐츠 업로드, DB 권한은 필요하지 않다. 운영 실행 전 실제 버킷, ADC 인증 주체, 앱의 실제 `GCS_SIGNED_URL_DURATION`을 확인한다. 아래 3600초는 앱 기본값 1시간의 예시이며, 운영 설정이 다르면 맞춰 바꾼다.

## 기존 원본 갱신

읽기 전용 미리보기:

```bash
./gradlew backfillImageCacheMetadata --args='--bucket=YOUR_BUCKET --signed-url-duration-seconds=3600 --limit=100'
```

미리보기에서 대상·기존/신규 정책을 확인한 뒤 실제 적용:

```bash
./gradlew backfillImageCacheMetadata --args='--bucket=YOUR_BUCKET --signed-url-duration-seconds=3600 --limit=100 --apply'
```

원본 내용 다운로드·이미지 변환·DB 변경 없이 Cache-Control만 패치한다. generation과 metageneration을 모두 조건으로 걸어 삭제·내용 교체·다른 메타데이터 수정과의 경합을 404/412로 건너뛴다. 실패는 요약에 집계하며 실패 객체가 있으면 명령은 비정상 종료한다. 이미 같은 정책이면 건너뛰므로 반복 실행할 수 있다. `--limit`은 변경 후보 수이며 전체 목록 읽기 비용의 상한은 아니다. 큰 버킷은 유지보수 시간에 작은 단위로 실행한다. `raced` 객체는 재실행에서 최신 상태를 확인한다.

재검증 정책으로 되돌리는 명령도 먼저 --apply 없이 확인한다:

```bash
./gradlew backfillImageCacheMetadata --args='--bucket=YOUR_BUCKET --signed-url-duration-seconds=3600 --max-age-seconds=0 --limit=100 --apply'
```

이는 기존 객체의 재검증 정책을 복구하는 명령이며 변경 전 메타데이터를 그대로 복원하는 기능은 아니다. 새 업로드의 기본 정책을 되돌리려면 앱 변경 롤백도 필요하다. 이미 브라우저에 저장된 캐시는 즉시 지울 수 없다.

## 썸네일과 함께 적용

이 PR은 main 기반이며 #195와 독립적으로 배포 가능하다. 썸네일 백필은 #195의 별도 `backfillImageThumbnails` 명령이다. 원본 메타데이터 갱신은 썸네일을 생성하지 않는다. #195의 새 썸네일 캐시 정책도 같은 최대 5분 기준으로 맞춘다. 기존 썸네일의 캐시 정책을 갱신하는 작업은 원본 갱신 명령의 대상이 아니다.

검증은 원본 업로드/복사/상세 조회 회귀, 짧은 서명 TTL 상한, 미리보기 무쓰기, 메타데이터 조건부 갱신을 확인한다. 운영 적용 뒤 DevTools의 Disable cache를 끈 상태에서 같은 URL 재요청과 Cache-Control 응답 헤더를 확인하고, 원본 content-type·내용·키가 유지되는지 확인한다.

참고: https://docs.cloud.google.com/storage/docs/metadata#cache-control
