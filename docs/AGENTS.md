# ForenShield — AI 에이전트 통합 컨텍스트

> **목적:** 프론트엔드 · 백엔드 · AI · 인프라 담당자와 **모든 AI 코딩 에이전트**가 이 `docs/` 폴더만 읽고 프로젝트 전체를 이해할 수 있게 하는 **단일 진입점**입니다.  
> **원본 명세:** `요구사항명세서_최종 (1).xlsx` · `기능명세서_최종.xlsx` (Excel이 최종 법적/기획 정본)

---

## 1. 30초 요약

| 항목 | 내용 |
| :--- | :--- |
| **서비스** | ForenShield AI — 내부망 디지털 포렌식·딥페이크 분석 |
| **사용자** | 수사/검찰 등 내부 직원 (일반 사용자 + 관리자) |
| **핵심 흐름** | 로그인 → 증거 업로드 → SHA-256 해시 → S3 WORM 보관 → RabbitMQ로 AI 분석 → 결과·CoC·리포트 |
| **요구사항** | RQ **169건** (Excel 자동 추출) |
| **기능(FN)** | **266건** (파트별: FE / BE / AI / INF) |
| **지원 미디어** | **영상(VIDEO)만** — MP4/MOV 업로드 · 음성·이미지 제외 |
| **본 레포** | Spring Boot 백엔드 (`backend-forensic`) |

---

## 2. 모노레포·레포지토리

| 파트 | 기술 | 경로 (팀 monorepo 기준) | 담당 문서 |
| :--- | :--- | :--- | :--- |
| **프론트엔드** | Next.js 16 · React 19 · Tailwind v4 | `frontend/frontend-deepfake/` | [teams/frontend.md](./teams/frontend.md) |
| **백엔드** | Spring Boot 3 · JPA · PostgreSQL | `backend/backend-forensic/` (**본 레포**) | [teams/backend.md](./teams/backend.md) |
| **AI** | FastAPI · Celery | `ai/ai-forensic/` | [teams/ai.md](./teams/ai.md) |
| **인프라** | AWS S3 · RabbitMQ · Redis · EKS | AWS / Terraform 등 | [teams/infrastructure.md](./teams/infrastructure.md) |

---

## 3. 문서 정본 우선순위 (충돌 시)

```
Excel 명세 (RQ/FN)
    ↓
database/erd.md (DB)
    ↓
api/specification.md (REST API — 구현 정본 + Gap)
    ↓
guides/implementation-standards.md (에러 JSON·예외·FE 계약)
    ↓
실제 코드
```

**금지:** Excel/ERD/API 문서 없이 기능 추가 · 필드명 임의 변경 · RQ-ID 없이 PR

---

## 4. 역할별 읽기 순서 (권장)

### 4.1 모든 사람 · 모든 AI (공통 15분)

1. [rule.md](./rule.md) — 협업 헌법·브랜치·RQ/FN ID
2. [PROJECT_STATUS.md](./PROJECT_STATUS.md) — **현재 진행률·Gap·다음 작업**
3. [architecture/system-overview.md](./architecture/system-overview.md) — 시스템 전체 흐름
4. [product/domain-glossary.md](./product/domain-glossary.md) — 용어·상태 Enum
5. [requirements/overview.md](./requirements/overview.md) — RQ/FN 체계·영역 지도
6. [requirements/index.md](./requirements/index.md) — RQ 169건 전문 색인

### 4.2 프론트엔드 AI / 개발자

1. 공통 15분
2. [teams/frontend.md](./teams/frontend.md)
3. [api/specification.md §2](./api/specification.md) — **현재 연동 API 정본**
4. [guides/implementation-standards.md §6·§11](./guides/implementation-standards.md) — 성공/에러 JSON·fetch 패턴
5. [api/convention.md](./api/convention.md) — URL·errorCode
6. 담당 화면 RQ → [requirements/index.md](./requirements/index.md)에서 검색

### 4.3 백엔드 AI / 개발자 (본 레포)

1. 공통 15분
2. [teams/backend.md](./teams/backend.md)
3. [database/erd.md](./database/erd.md)
4. [api/specification.md](./api/specification.md) — §0 Gap · §2 구현 · §3 목표
5. [guides/implementation-standards.md](./guides/implementation-standards.md) — 예외·응답 표준
6. [guides/development.md](./guides/development.md) — 작업 절차·CoC 기록 시점
7. [requirements/traceability.md](./requirements/traceability.md) — BE FN 구현 상태 (**⚠️ [PROJECT_STATUS.md §6](./PROJECT_STATUS.md)와 교차 확인**)

### 4.4 AI 워커 팀

1. 공통 15분
2. [teams/ai.md](./teams/ai.md)
3. [integrations/ai-json.md](./integrations/ai-json.md) — Request/Response JSON **정본**
4. [integrations/rabbitmq.md](./integrations/rabbitmq.md) — Exchange·Routing Key
5. [integrations/s3.md](./integrations/s3.md) — `copy/`만 읽기, `original/` WORM 금지

### 4.5 인프라 팀

1. 공통 15분
2. [teams/infrastructure.md](./teams/infrastructure.md)
3. [integrations/s3.md](./integrations/s3.md) · [integrations/rabbitmq.md](./integrations/rabbitmq.md)
4. [requirements/index.md](./requirements/index.md) — `RQ-SEC-*`, `RQ-PER-*`, `RQ-NFR-*`

---

## 5. 전체 문서 카탈로그

| 경로 | 한 줄 설명 |
| :--- | :--- |
| **PROJECT_STATUS.md** | 진행 상황·Gap·다음 스프린트 (현황판) |
| **AGENTS.md** | (본 파일) AI·팀 통합 진입점 |
| [README.md](./README.md) | 폴더 구조·「무엇을 찾을 때」표 |
| [rule.md](./rule.md) | 브랜치·커밋·PR·ID 체계 |
| **architecture/** | |
| [system-overview.md](./architecture/system-overview.md) | 컴포넌트·데이터 흐름·배포 관점 |
| **product/** | |
| [domain-glossary.md](./product/domain-glossary.md) | Evidence·Case·CoC·RiskLevel 등 용어 |
| **requirements/** | |
| [overview.md](./requirements/overview.md) | RQ/FN 구조·화면/API 영역 지도 |
| [index.md](./requirements/index.md) | **RQ 169건** 전문 (Excel 추출) |
| [traceability.md](./requirements/traceability.md) | RQ ↔ FN ↔ 구현 RTM (BE 중심) |
| **api/** | |
| [specification.md](./api/specification.md) | REST API 정본 + 명세 Gap |
| [convention.md](./api/convention.md) | URL·HTTP·errorCode 목록 |
| [signup.md](./api/signup.md) | 회원가입·초대코드·Rate limit |
| **database/** | |
| [erd.md](./database/erd.md) | PostgreSQL ERD v3 **정본** |
| **guides/** | |
| [development.md](./guides/development.md) | 기능 하나 구현 절차 |
| [implementation-standards.md](./guides/implementation-standards.md) | 에러 JSON·예외·페이지네이션·FE 계약 |
| [naming.md](./guides/naming.md) | camelCase·Enum·Git 네이밍 |
| **integrations/** | |
| [s3.md](./integrations/s3.md) | S3 버킷·WORM·경로 |
| [rabbitmq.md](./integrations/rabbitmq.md) | 큐·Exchange·재시도 |
| [ai-json.md](./integrations/ai-json.md) | BE ↔ AI 메시지 JSON |
| **teams/** | |
| [frontend.md](./teams/frontend.md) | FE 라우트·API·체크리스트 |
| [backend.md](./teams/backend.md) | BE 패키지·레이어·테스트 |
| [ai.md](./teams/ai.md) | 워커 입출력·모델·실패 처리 |
| [infrastructure.md](./teams/infrastructure.md) | AWS·네트워크·시크릿 |

---

## 6. 구현 상태 스냅샷

**상세 현황판:** [PROJECT_STATUS.md](./PROJECT_STATUS.md) (영역별·API 목록·P0/P1 백로그)

요약:

| 영역 | 상태 | 참고 |
| :--- | :---: | :--- |
| 로그인·가입·JWT · step-up | ✅ | `POST /api/auth/login`, `/api/v1/auth/signup`, step-up |
| 관리자 CRUD·로그·증거·검토자 | ✅ | `/api/v1/admin/**`, `/admin/reviewers` |
| 마이페이지·프로필·설정 | ✅ | `/api/v1/users/me`, `/settings`, `/mypage/**` |
| 증거 업로드·분석·상태·readiness | ✅ | `/api/v1/evidences/**` (v1 only) |
| 대시보드 stats · 7일 trend | ✅ | RQ-DSH-043/044 — 분석관 `uploaderId` 기준 |
| 검토 RBAC 플로우 | ✅ | review-request / reviewer / review-decision (§0.12) |
| 에러 JSON·예외 Handler | ✅ | `BusinessException` + `StandardErrorResponse` |
| Admin 페이지네이션 | ✅ | `content` / `totalElements` / `totalPages` |
| PDF 리포트 · 공개 검증 | ✅ | `/reports`, `/evidences/.../reports/pdf`, `/public/reports` |
| 비교 검증 (Compare) | ✅ | `/api/v1/compare/**` |
| 알림 API | ✅ | `/api/v1/notifications` (FE 연동) |
| HLS 암호화 스트림 | ✅ | streamToken + step-up (§0.14) |
| 블록체인 앵커 (운영 배포) | 🟡 | API·코드 ✅ · 운영 CA/네트워크 환경 의존 |

상세 Gap·엔드포인트 목록: [api/specification.md](./api/specification.md) (§0 · **§6 Quick Reference**)

---

## 7. 다른 AI 에이전트에 넘길 때 (프롬프트 템플릿)

아래 블록을 복사해 **역할에 맞는 md 파일 경로**를 `@` 또는 파일 첨부로 함께 전달하세요.

```markdown
## 프로젝트
ForenShield — 내부망 디지털 포렌식·딥페이크 분석 플랫폼

## 당신의 역할
[프론트엔드 | 백엔드 | AI | 인프라] 담당

## 반드시 따를 문서 (순서대로)
1. docs/AGENTS.md
2. docs/rule.md
3. (역할별) docs/teams/{role}.md
4. (해당 시) docs/api/specification.md, docs/database/erd.md, docs/integrations/ai-json.md

## 작업 RQ-ID
RQ-XXXX-NNN — requirements/index.md에서 내용 확인

## 제약
- RQ/FN 없이 기능 추가 금지
- API 필드 camelCase, 에러는 success + errorCode + message
- Excel 명세와 충돌 시 Excel → ERD → API 문서 순으로 따름
- 추측으로 API/DB 스키마 만들지 말 것 — specification.md / erd.md 확인

## 이번 작업
(구체적 요청 작성)
```

---

## 8. 문서 갱신 규칙

| 변경 종류 | 갱신할 문서 |
| :--- | :--- |
| API 추가/수정 | `api/specification.md`, `api/convention.md` |
| DB 테이블/컬럼 | `database/erd.md` |
| BE↔AI JSON | `integrations/ai-json.md` |
| 에러/응답 규칙 | `guides/implementation-standards.md` |
| RQ/FN 추가 (Excel) | Excel → `requirements/index.md` 재추출 |
| 구현 완료 | `requirements/traceability.md` 상태 emoji |

---

## 9. 관련 파일 (레포 루트)

| 파일 | 용도 |
| :--- | :--- |
| [../AGENTS.md](../AGENTS.md) | 본 문서로 리다이렉트 |
| [../GEMINI.md](../GEMINI.md) | Gemini용 짧은 요약 |
| `.env` | 로컬 시크릿 (**커밋 금지**) |

---

## 10. 변경 이력

| 날짜 | 내용 |
| :--- | :--- |
| 2026-07-14 | 프로젝트명 표기 **ForenShield**로 통일 (구 VeriForensics) |
| 2026-07-14 | 구현 스냅샷 갱신 — PDF/Compare/알림/설정/검토/HLS ✅ · API 정본 §6 동기화 |
| 2026-06-17 | AI·팀 통합 진입점 초안 — architecture/teams/product/requirements/overview 추가 |
