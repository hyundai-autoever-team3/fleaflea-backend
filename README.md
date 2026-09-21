# FleaFlea Backend

친구끼리 플리마켓을 열고, 도감에 등록한 물건을 거래할 수 있는 **FleaFlea**의 백엔드 저장소입니다.

Spring Boot를 기반으로 API 서버를 구성했으며, Docker Compose를 이용해 애플리케이션과 PostgreSQL을 함께 운영하고 있습니다.
실제 서비스는 AWS EC2에 배포되어 있으며, Vercel에 배포된 프론트엔드와 HTTPS로 통신합니다.

---

## 1. 시스템 아키텍처

FleaFlea는 프론트엔드와 백엔드를 분리하여 운영합니다.

- `fleaflea.app`: Vercel에서 React 프론트엔드 제공
- `api.fleaflea.app`: AWS EC2에서 Spring Boot API 제공
- Gabia DNS에서 프론트엔드와 백엔드 도메인의 DNS 레코드 관리
- EC2 호스트의 Nginx에서 HTTPS를 처리하고 Spring Boot 컨테이너로 요청 전달
- Spring Boot와 PostgreSQL은 Docker Compose로 실행
- 업로드 이미지는 Amazon S3에 저장
- `main` 브랜치 배포는 GitHub Actions, GHCR, AWS SSM을 통해 자동화

```mermaid
flowchart LR
    USER["사용자<br/>Web Browser"]

    subgraph DNS["Gabia DNS"]
        WEB_DNS["fleaflea.app<br/>Frontend DNS"]
        API_DNS["api.fleaflea.app<br/>Backend DNS"]
    end

    VERCEL["Vercel<br/>React 18 + Vite<br/>Frontend Hosting"]

    subgraph CICD["CI/CD"]
        GHA["GitHub Actions<br/>Build & Test"]
        GHCR["GitHub Container Registry<br/>Spring Boot Image"]
    end

    subgraph AWS["AWS Cloud"]
        SSM["AWS Systems Manager<br/>EC2 Deployment"]
        S3["Amazon S3<br/>Profile & Item Images"]

        subgraph EC2["Amazon EC2 · Ubuntu 24.04 LTS"]
            SG["Security Group<br/>80 · 443 Public"]

            NGINX["Host Nginx<br/>TLS Termination<br/>Reverse Proxy"]

            subgraph DOCKER["Docker Compose"]
                APP["Spring Boot API<br/>Java 25<br/>127.0.0.1:8080"]

                DB["PostgreSQL 16<br/>127.0.0.1:5432"]

                VOLUME[("Named Volume<br/>fleaflea_postgres_data")]

                APP -->|"JDBC<br/>postgres:5432"| DB
                DB --- VOLUME
            end
        end
    end

    USER -->|"Frontend HTTPS"| WEB_DNS
    WEB_DNS --> VERCEL
    VERCEL -->|"HTML · CSS · JavaScript"| USER

    USER -->|"REST API HTTPS"| API_DNS
    API_DNS --> SG
    SG -->|"80 / 443"| NGINX
    NGINX -->|"HTTP<br/>127.0.0.1:8080"| APP

    APP -->|"AWS SDK · HTTPS"| S3

    GHA -->|"Docker Image Push"| GHCR
    GHA -->|"AWS OIDC"| SSM
    SSM -->|"Deploy Command"| DOCKER
    DOCKER -.->|"Image Pull"| GHCR
```

### 요청 흐름

```text
사용자 브라우저
 ├─ https://fleaflea.app
 │   └─ Gabia DNS
 │       └─ Vercel
 │           └─ React + Vite 프론트엔드 제공
 │
 └─ https://api.fleaflea.app
     └─ Gabia DNS
         └─ AWS EC2 Security Group
             └─ Host Nginx
                 └─ 127.0.0.1:8080
                     └─ Spring Boot 컨테이너
                         ├─ PostgreSQL 컨테이너
                         │   └─ fleaflea_postgres_data 볼륨
                         └─ Amazon S3
```

### 배포 흐름

```text
main 브랜치 Push 또는 PR 병합
 └─ GitHub Actions
     ├─ Gradle Build 및 Test
     ├─ Docker 이미지 생성
     ├─ GHCR에 이미지 Push
     └─ AWS Systems Manager 명령 실행
         └─ EC2 Docker Compose 배포
             ├─ GHCR 이미지 Pull
             ├─ Spring Boot 컨테이너 재생성
             └─ Health Check
```

### 인프라 구성

| 구성 요소 | 역할 |
|---|---|
| **Gabia DNS** | `fleaflea.app`, `api.fleaflea.app` DNS 레코드 관리 |
| **Vercel** | React 18 및 Vite 프론트엔드 배포, CDN과 HTTPS 제공 |
| **AWS EC2** | Nginx, Spring Boot, PostgreSQL 실행 |
| **Security Group** | EC2 인바운드 및 아웃바운드 트래픽 제어 |
| **Nginx** | HTTPS 처리, HTTP에서 HTTPS 리다이렉트, Spring Boot Reverse Proxy |
| **Let's Encrypt / Certbot** | `api.fleaflea.app` TLS 인증서 발급 및 갱신 |
| **Docker Compose** | Spring Boot 및 PostgreSQL 컨테이너 실행과 상태 관리 |
| **Spring Boot** | REST API, JWT 인증, 비즈니스 로직 처리 |
| **PostgreSQL 16** | 회원, 마켓, 도감 및 거래 데이터 저장 |
| **Flyway** | PostgreSQL 스키마 버전 관리 |
| **Named Volume** | `fleaflea_postgres_data`에 PostgreSQL 데이터 영구 저장 |
| **Amazon S3** | 프로필, 도감, 상품 및 마켓 이미지 저장 |
| **EC2 Instance Role** | Spring Boot 컨테이너의 S3 접근 권한 제공 |
| **GitHub Actions** | CI 빌드·테스트 및 운영 배포 자동화 |
| **GHCR** | 커밋 SHA별 Spring Boot Docker 이미지 저장 |
| **AWS SSM** | 공개 SSH 배포 없이 EC2에서 배포 명령 실행 |

### 네트워크 구성

| 대상 | 포트 | 접근 범위 | 설명 |
|---|---:|---|---|
| **Vercel** | `443` | Public | 프론트엔드 HTTPS 제공 |
| **EC2 Nginx** | `80` | Public | HTTPS로 리다이렉트 |
| **EC2 Nginx** | `443` | Public | 백엔드 API 및 Swagger HTTPS 제공 |
| **SSH** | `22` | 관리자 IP 제한 | EC2 운영 및 장애 대응 |
| **Spring Boot** | `8080` | Loopback | `127.0.0.1:8080`에만 바인딩 |
| **PostgreSQL** | `5432` | Loopback / Docker 내부 | `127.0.0.1:5432` 및 Docker 네트워크에서만 접근 |
| **Amazon S3** | `443` | Outbound | Spring Boot가 AWS SDK로 이미지 저장 및 조회 |
| **AWS SSM** | `443` | Outbound | GitHub Actions에서 전달된 배포 명령 수신 |

Spring Boot와 PostgreSQL 포트는 외부에 공개하지 않습니다. 외부 API 요청은 반드시 Nginx를 거쳐 Spring Boot로 전달됩니다.

### 운영 주소

| 구분 | 주소 |
|---|---|
| **프론트엔드** | `https://fleaflea.app` |
| **백엔드 API** | `https://api.fleaflea.app` |
| **Swagger UI** | `https://api.fleaflea.app/swagger-ui/index.html` |
| **OpenAPI JSON** | `https://api.fleaflea.app/v3/api-docs` |
| **Health Check** | `https://api.fleaflea.app/actuator/health` |
## 2. 기술 스택

| 구분                       | 기술 / 도구                             | 버전 / 비고                      |
| :----------------------- | :---------------------------------- | :--------------------------- |
| **Language & Framework** | Java, Spring Boot                   | Java 25, Spring Boot 3.4.3   |
| **Database & ORM**       | PostgreSQL, Spring Data JPA, Flyway | PostgreSQL 16                |
| **Web Server & SSL**     | Nginx, Certbot                      | Nginx 1.24+, Let's Encrypt   |
| **Container**            | Docker, Docker Compose              | Bridge Network, Named Volume |
| **Cloud**                | AWS EC2, Amazon S3, Gabia DNS       | Ubuntu 24.04 LTS             |
| **CI/CD**                | GitHub Actions, GHCR, AWS SSM       | Docker 이미지 기반 자동 배포          |
| **API Docs**             | Springdoc OpenAPI                   | Swagger UI                   |
| **Frontend**             | React 18, Vite, Vercel              | HTTPS 통신                     |

## 3. 구성 및 요청 흐름

### 3-1. Frontend

프론트엔드는 React와 Vite로 구성되어 있으며 Vercel을 통해 배포하고 있습니다.

사용자가 `fleaflea.app`에 접속하면 Vercel이 프론트엔드를 제공하고, 브라우저는 백엔드 API 도메인인 `api.fleaflea.app`으로 요청을 보냅니다.

프론트엔드와 백엔드 간 통신은 HTTPS 기반의 REST API 방식으로 이루어집니다.

### 3-2. DNS / Nginx

백엔드 서버는 AWS EC2에서 운영하며, Gabia DNS의 `api` 레코드로 EC2 IP와 연결합니다.

```text
api.fleaflea.app
        ↓
AWS EC2
        ↓
Nginx
        ↓
Spring Boot
```

AWS Security Group에서는 외부에 `80`, `443` 포트만 열어두고 있습니다.

Nginx는 외부 요청을 가장 먼저 받아 다음 역할을 수행합니다.

* HTTP 요청을 HTTPS로 리다이렉트
* Let's Encrypt 인증서를 이용한 HTTPS 처리
* `/` 요청을 Spring Boot의 `127.0.0.1:8080`으로 전달
* 외부에서 Spring Boot의 8080 포트에 직접 접근하지 못하도록 구성

HTTP로 들어온 요청은 HTTPS로 자동 전환됩니다.

```text
http://api.fleaflea.app
        ↓
https://api.fleaflea.app
```

### 3-3. Docker Compose

EC2 내부에서는 Docker Compose를 이용해 Spring Boot와 PostgreSQL을 실행하고 있습니다.

#### fleaflea-app

Spring Boot 애플리케이션 컨테이너입니다.

주요 역할은 다음과 같습니다.

* JWT 기반 회원 인증
* 플리마켓 생성 및 관리
* 플리마켓 회원 관리
* 도감 아이템 관리
* 거래 요청 처리
* 이미지 업로드
* REST API 제공

컨테이너의 8080 포트는 EC2의 `127.0.0.1:8080`에 연결되어 있어 Nginx를 통해서만 외부 요청을 받을 수 있습니다.

CORS 설정에서는 Vercel 배포 주소와 로컬 개발 환경을 허용하고 있습니다.

#### fleaflea-postgres

서비스의 데이터를 저장하는 PostgreSQL 16 컨테이너입니다.

Spring Boot와 PostgreSQL은 Docker 내부 네트워크를 통해 통신합니다.

```text
fleaflea-app
     ↓
fleaflea-postgres:5432
```

PostgreSQL 데이터는 Docker Named Volume에 저장합니다.

```text
fleaflea_postgres_data
```

따라서 PostgreSQL 컨테이너가 재생성되더라도 기존 데이터는 유지됩니다.

데이터베이스 스키마는 Flyway를 이용해 관리하고 있으며, 애플리케이션 실행 시 필요한 마이그레이션이 자동으로 적용됩니다.

---

## 4. 이미지 저장

서비스에서 사용하는 이미지는 Amazon S3에 저장합니다.

현재 다음과 같은 이미지가 S3에 저장됩니다.

* 회원 프로필 이미지
* 플리마켓 이미지
* 도감 아이템 이미지

Spring Boot 애플리케이션에서 AWS SDK를 이용해 S3에 접근합니다.

AWS Access Key를 서버에 직접 저장하는 방식 대신 EC2에 IAM Role을 연결해 S3 접근 권한을 부여했습니다.

```text
Spring Boot
     ↓
EC2 IAM Role
     ↓
Amazon S3
```

이를 통해 애플리케이션에서 별도의 장기 AWS Access Key를 관리하지 않아도 S3를 사용할 수 있도록 구성했습니다.

---

## 5. CI/CD

백엔드 배포는 GitHub Actions를 이용해 자동화했습니다.

`main` 브랜치에 코드가 반영되면 다음 순서로 배포가 진행됩니다.

```text
main branch
     ↓
GitHub Actions
     ↓
Gradle Build / Test
     ↓
Docker Image Build
     ↓
GHCR Push
     ↓
AWS SSM
     ↓
EC2 deploy.sh 실행
     ↓
Docker Container 교체
     ↓
Health Check
```

GitHub Actions에서 Spring Boot 애플리케이션을 빌드한 뒤 Docker 이미지를 생성하고 GHCR에 업로드합니다.

이후 AWS Systems Manager의 `send-command`를 사용해 EC2의 배포 스크립트를 원격으로 실행합니다.

EC2에서는 새 이미지를 내려받은 후 기존 애플리케이션 컨테이너를 교체합니다.

배포 완료 후에는 Spring Boot Actuator를 이용해 서버 상태를 확인합니다.

```text
/actuator/health
```

정상적으로 배포된 경우 다음과 같이 `UP` 상태를 반환합니다.

```json
{
  "status": "UP"
}
```

---

## 6. 서버 환경 변수

EC2에서는 애플리케이션 설정과 계정 정보를 코드와 분리해 `/etc/fleaflea` 디렉토리에서 관리하고 있습니다.

```text
/etc/fleaflea/
├── fleaflea.env
├── postgres.env
├── deploy.env
└── ghcr.env
```

각 파일의 역할은 다음과 같습니다.

| 파일             | 용도                                     |
| :------------- | :------------------------------------- |
| `fleaflea.env` | Spring Boot 환경 변수, JWT Secret, S3 설정 등 |
| `postgres.env` | PostgreSQL DB 이름 및 계정 정보               |
| `deploy.env`   | 현재 배포할 Docker 이미지 정보                   |
| `ghcr.env`     | Private GHCR 이미지 Pull을 위한 인증 정보        |

민감한 값은 Git 저장소에 포함하지 않고 서버 환경에서 별도로 관리합니다.

---

## 7. API 문서

백엔드 API는 Springdoc OpenAPI를 이용해 문서화하고 있습니다.

배포된 서버의 Swagger UI에서 현재 제공하는 API를 직접 확인하고 테스트할 수 있습니다.

**Swagger UI**

https://api.fleaflea.app/swagger-ui/index.html
