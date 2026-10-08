# Fleaflea

## 프로젝트 소개

Fleaflea는 서로 아는 사람들이 플리마켓을 열고 물건을 거래하는 서비스입니다.

## 핵심 기능
- 회원·인증
- 친구
- 콕찌르기
- 알림
- 물건 도감
- 플리마켓
- 플리마켓 아이템
- 거래
- 콕찌르기
## 작업 라우팅

- 코드, API, 도메인 모델, DB, 테스트를 설계·구현·수정·리뷰할 때는 반드시 [Fleaflea 프로젝트 컨벤션](.agents/skills/fleaflea-project-conventions/SKILL.md)을 먼저 읽고 따릅니다.
- 배포·운영 작업은 [`deploy/ec2/README.md`](deploy/ec2/README.md), 배포 Compose와 GitHub Actions 워크플로를 함께 확인합니다.
- 성능 측정·튜닝 작업은 [`performance/k6/README.md`](performance/k6/README.md), 관련 시나리오와 최신 결과 문서를 먼저 확인합니다.
- PR 작성은 [`.github/pull_request_template.md`](.github/pull_request_template.md)의 구조를 유지합니다.
- 이 파일에는 상세 규칙을 추가하지 않습니다. 반복 적용할 프로젝트 규칙은 프로젝트 컨벤션 스킬에 둡니다.
- 테스트는 [테스트 전략](docs/TESTING.md)을 참고합니다.