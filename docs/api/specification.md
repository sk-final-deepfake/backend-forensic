# VeriForensics API Specification

> **버전:** v1.2 (기능명세서 v1.1 대비 Gap 분석 + **현재 Spring Boot 구현 정본**)  
> **기준:** `기능명세서_최종.xlsx` · `요구사항명세서_최종 (1).xlsx`  
> **관련:** [convention.md](./convention.md) · [../guides/implementation-standards.md](../guides/implementation-standards.md) · [signup.md](./signup.md)

---

## 0. 기능명세서 vs 현재 코드 — Gap 분석

### 0.1 한 줄 요약

| 구분 | 판정 |
| :--- | :--- |
| **인증·가입·마이페이지·관리자 CRUD** | ✅ 일치 (`/api/v1`) |
| **증거·분석 API** | ✅ v1 prefix + stats/analyze/auth **통일 완료** (legacy alias 유지) |
| **에러 JSON·예외 Handler** | ✅ `StandardErrorResponse` 단일 |
| **Admin 페이지네이션** | ✅ `content` / `totalElements` |
| **비교검증·PDF·알림·설정·블록체인** | ⬜ **미구현** |

> **FE 연동:** [§2 현재 구현 정본](#2-현재-구현-정본-controller-기준) 사용  
> **AI 에이전트:** [../AGENTS.md](../AGENTS.md) → 본 문서 §2

---

### 0.2 경로 불일치 (Prefix)

기능명세서 FE 시트「호출 API」와 코드 Controller `@RequestMapping` 비교:

| 기능 | 기능명세서 (목표) | 현재 코드 | 상태 |
| :--- | :--- | :--- | :--- |
| 대시보드 통계 | `GET /api/v1/evidences/stats` | 동일 (+ legacy `/api/evidences/stats`) | ✅ |
| 파일 업로드 | `POST /api/v1/evidences/upload` | 동일 (+ legacy) | ✅ |
| 분석 요청 | `POST /api/v1/evidences/analyze` | `{ evidenceId }` + `{ evidenceIds }` | ✅ |
| 로그인 PENDING | HTTP 401 + `ACCOUNT_PENDING` | HTTP 401 + `errorCode` | ✅ |
| 가입 에러 JSON | `errorCode` + `success` | `StandardErrorResponse` | ✅ |
| 로그인 | `POST /api/auth/login` | `POST /api/auth/login` | ✅ |
| 가입 | `POST /api/v1/auth/signup` | `POST /api/v1/auth/signup` | ✅ |
| 초대코드 검증 | `POST /api/v1/invite-codes/validate` | `POST /api/v1/invite-codes/validate` | ✅ |
| 분석 이력 | `GET /api/v1/mypage/analysis-history` | 동일 (+ alias `GET /api/v1/cases/me`) | ✅ |
| 사건/상세 | `GET /api/v1/cases/{caseId}` | 동일 | ✅ |
| 증거 상세 | *(명세: case API에 포함)* | `GET /api/evidences/{evidenceId}/detail` | ⚠️ 추가 엔드포인트 |
| 분석 상태 | *(명세에 경로 없음)* | `GET /api/evidences/{evidenceId}/analysis-status` | ➕ 코드만 |
| 프로필 | `GET/PATCH /api/v1/users/me` | 동일 | ✅ |
| 관리자 | `GET/POST/PATCH /api/v1/admin/*` | 세부 경로 구현됨 (§2.5) | ✅ |

---

### 0.3 요청·응답 Body 불일치

| API | 기능명세서 | 현재 코드 | Gap |
| :--- | :--- | :--- | :--- |
| **분석 요청** | `{ "evidenceId": 1 }` (단건) | `{ evidenceId }` 또는 `{ evidenceIds, caseName? }` | ✅ |
| **대시보드 stats** | 4카드 (RQ-DSH-043) | `totalAnalysisCount`, `deepfakeDetectedCount`, … | ✅ |
| **상세 조회** | `GET /api/v1/cases/{caseId}` + evidence detail | `CaseDetailResponse` + `EvidenceDetailResponse` | 🟡 분할 유지 |
| **로그인 실패 (PENDING)** | HTTP 401 + `ACCOUNT_PENDING` | HTTP 401 + `errorCode` | ✅ |
| **에러 JSON** | `errorCode` + `success` | `StandardErrorResponse` 전 API | ✅ |

---

### 0.4 기능명세서·RQ 기준 — API 미구현

아래는 요구사항/기능명세에 있으나 **백엔드 REST가 아직 없음**:

| 영역 | RQ (예) | 필요 API (목표) | 상태 |
| :--- | :--- | :--- | :--- |
| PDF 리포트 | RQ-DTL-082~086 | `GET /api/v1/evidences/{id}/reports/pdf` 등 | ⬜ |
| 비교 검증 | RQ-CMP-083~095 | `POST /api/v1/compare`, `GET .../result` | ⬜ |
| 알림 | RQ-COM-015~016 | `GET /api/v1/notifications` | ⬜ |
| 환경 설정 | RQ-COM-009 | `GET/PATCH /api/v1/users/me/settings` | ⬜ |
| 블록체인 앵커 | RQ-REQ-052, DTL-078 | 업로드 시 앵커 + 조회 API | ⬜ |
| X.509 사본 서명 | RQ-REQ-050 | 업로드 파이프라인 내부 | ⬜ |
| 대시보드 7일 차트 | RQ-DSH-044 | `GET /api/v1/dashboard/analysis-trend?days=7` | ⬜ |
| 최근 분석 목록 | RQ-DSH-045 | case/history API 확장 또는 전용 | 🟡 부분 (`mypage/analysis-history`) |
| 로그아웃 | RQ-COM-011 | 클라이언트 sessionStorage 삭제 (서버 API 불필요) | — |

---

### 0.5 코드에만 있음 (명세 FE 시트에 경로 미기재)

| Method | Path | 용도 |
| :--- | :--- | :--- |
| GET | `/api/v1/auth/username/check` | 아이디 중복 확인 ([signup.md](./signup.md)) |
| GET | `/api/v1/organizations/departments` | 부서 자동완성 |
| GET | `/api/v1/cases/me` | 분석 이력 alias |
| GET | `/api/evidences/{evidenceId}/detail` | 증거 상세 |
| GET | `/api/evidences/{evidenceId}/analysis-status` | 분석 진행률 polling |
| DELETE | `/api/evidences/{evidenceId}` | 증거 삭제 |
| DELETE | `/api/evidences/{evidenceId}/reset` | 증거+분석 초기화 |
| DELETE | `/api/evidences/{evidenceId}/analysis` | 분석 중단 |
| GET/DELETE | `/api/v1/admin/evidences/**` | 관리자 증거 관리 |
| DELETE | `/api/v1/admin/users/{userId}` | 관리자 계정 삭제 |

→ **유지 권장** (실사용·테스트 존재). 명세 Excel FE 시트에 경로 추가 반영 필요.

---

### 0.6 Gap 해소 우선순위 (팀 합의안)

| 순위 | 작업 | 담당 |
| :---: | :--- | :--- |
| 1~4 | Evidence v1 · stats · analyze · auth errorCode | ✅ 완료 |
| 5 | PDF / Compare API | ⬜ BE |
| 6 | 기능명세서 Excel FE「호출 API」열 갱신 | PM/문서 |

---

## 1. 공통

| 항목 | 값 |
| :--- | :--- |
| Base URL (표준) | `/api/v1` |
| Legacy | `/api/auth/login`, `/api/evidences/*` |
| 인증 | `Authorization: Bearer {JWT}` |
| Public | login, signup, username/check, invite validate, departments |
| Admin | `/api/v1/admin/**` + `ROLE_ADMIN` |
| 에러 형식 | [../guides/implementation-standards.md](../guides/implementation-standards.md) (`StandardErrorResponse`) |

---

## 2. 현재 구현 정본 (Controller 기준)

> **FE·BE는 아래 경로를 당분간 Source of Truth로 사용합니다.**

### 2.1 인증 · 가입

#### POST `/api/auth/login` — 로그인

| | |
|---|---|
| **RQ** | RQ-LOGIN-020 |
| **Auth** | Public |

**Request**

```json
{ "loginId": "hong123", "password": "Password123!" }
```

**Response 200**

```json
{
  "success": true,
  "token": "<JWT>",
  "userId": 1,
  "loginId": "hong123",
  "name": "홍길동",
  "role": "ROLE_USER"
}
```

**Errors:** 401 credentials / 403 account status (⚠️ 명세는 401+errorCode)

---

#### POST `/api/v1/auth/signup` · GET `/api/v1/auth/username/check` · POST `/api/v1/invite-codes/validate` · GET `/api/v1/organizations/departments`

→ **[signup.md](./signup.md)** 전문

---

### 2.2 사용자 · 마이페이지

#### GET `/api/v1/users/me`

**Auth:** User · **Response:** `UserProfileResponse`

#### PATCH `/api/v1/users/me`

**Auth:** User · **Body:** `UpdateUserProfileRequest`  
**Errors:** `INVALID_PASSWORD`, `DUPLICATE_LOGIN_ID`, `PASSWORD_TOO_SHORT`

#### GET `/api/v1/mypage/analysis-history`

**Auth:** User  
**Query:** `sort=newest|oldest`, `page`, `size`  
**Response:** `AnalysisHistoryPageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`)

**Alias:** `GET /api/v1/cases/me` (동일 handler)

---

### 2.3 증거 · 분석 (Legacy prefix `/api/evidences`)

#### GET `/api/evidences/stats`

| | |
|---|---|
| **RQ** | RQ-DSH-043 (⚠️ 응답 필드 불일치) |
| **Auth** | User |

**Response 200 (현재)**

```json
{
  "imageCount": 3,
  "videoCount": 12,
  "audioCount": 1
}
```

**명세 목표 (RQ-DSH-043, 미구현 필드)**

```json
{
  "totalAnalysisCount": 0,
  "deepfakeDetectedCount": 0,
  "completedCount": 0,
  "inProgressCount": 0
}
```

---

#### POST `/api/evidences/upload`

| | |
|---|---|
| **RQ** | RQ-REQ-047, RQ-REQ-048 |
| **Auth** | User |
| **Content-Type** | `multipart/form-data` |

| Field | Required |
| :--- | :---: |
| `file` | ✅ |
| `caseName` | ❌ |

**Response 200:** `FileUploadResponse` (`evidenceId`, `hashValue`, `metadata`, …)

**Errors:** `FILE_NOT_FOUND`, `UNSUPPORTED_FILE_TYPE`, `FILE_SIZE_EXCEEDED`, `HASH_GENERATION_FAILED`

**명세 목표 path:** `POST /api/v1/evidences/upload`

---

#### POST `/api/evidences/analyze`

| | |
|---|---|
| **RQ** | RQ-REQ-049 |
| **Auth** | User |

**Request (현재)**

```json
{
  "evidenceIds": [1, 2],
  "caseName": "2026-서울-0123 사건"
}
```

**Response 200:** `StartAnalysisResponse` (`success`, `startedCount`, `evidenceIds`, …)

**명세 목표 Request**

```json
{ "evidenceId": 1 }
```

**명세 목표 path:** `POST /api/v1/evidences/analyze`

---

#### GET `/api/evidences/{evidenceId}/analysis-status`

**Auth:** User · **Response:** `AnalysisStatusResponse`  
**Errors:** `ANALYSIS_NOT_FOUND` (404)

---

#### GET `/api/evidences/{evidenceId}/detail`

| | |
|---|---|
| **RQ** | RQ-DTL-053~ (일부) |
| **Auth** | User |

**Response:** `EvidenceDetailResponse`

```json
{
  "evidenceInfo": { },
  "integrityInfo": { },
  "analysisInfo": { },
  "cocLogs": [ ]
}
```

---

#### DELETE `/api/evidences/{evidenceId}`

증거 소프트 삭제 · **204** 또는 error body

#### DELETE `/api/evidences/{evidenceId}/reset`

증거+분석 전체 제거 · **204**

#### DELETE `/api/evidences/{evidenceId}/analysis`

진행 중 분석만 중단 · **204**

---

### 2.4 사건 상세

#### GET `/api/v1/cases/{caseId}`

| | |
|---|---|
| **RQ** | RQ-DTL-053 + 다수 DTL (명세는 이 API로 상세 전체 제공 가정) |
| **Auth** | User |
| **Path** | `caseId` = **String** (사건명/식별자) |

**Response:** `CaseDetailResponse`  
**Errors:** `CASE_NOT_FOUND` (404)

> 명세 FE: `app/cases/[id]/page.tsx` → 이 API 호출.  
> 코드는 **case + evidence detail API 이중 구조** — FE는 화면 설계에 맞게 선택.

---

### 2.5 관리자 (`/api/v1/admin/**`)

**공통:** `@PreAuthorize("hasRole('ADMIN')")` · **RQ:** RQ-ADMIN-119~141

| Method | Path | 설명 |
| :--- | :--- | :--- |
| GET | `/api/v1/admin/dashboard/stats` | `AdminDashboardStatsResponse` |
| GET | `/api/v1/admin/users` | 목록 (`search`, `status`, `page`, `size`) |
| POST | `/api/v1/admin/users/{userId}/approve` | 가입 승인 |
| POST | `/api/v1/admin/users/{userId}/reject` | 가입 반려 |
| PATCH | `/api/v1/admin/users/{userId}` | 정보 수정 |
| PATCH | `/api/v1/admin/users/{userId}/password` | 비밀번호 재설정 |
| DELETE | `/api/v1/admin/users/{userId}` | 계정 삭제 |
| GET | `/api/v1/admin/invite-codes` | 초대코드 목록 |
| POST | `/api/v1/admin/invite-codes` | 초대코드 발급 |
| GET | `/api/v1/admin/evidences` | 증거 목록 (page) |
| GET | `/api/v1/admin/evidences/{evidenceId}` | 증거 상세 |
| DELETE | `/api/v1/admin/evidences/{evidenceId}` | 증거 삭제 |
| GET | `/api/v1/admin/logs` | 감사 로그 (`category`, `from`, `to`, …) |
| GET | `/api/v1/admin/logs/export?format=csv` | CSV 다운로드 |
| GET | `/api/v1/admin/me` | 관리자 프로필 |
| PATCH | `/api/v1/admin/me` | 프로필 수정 |
| PATCH | `/api/v1/admin/me/password` | 비밀번호 변경 |

**Admin 페이지 Response (페이지네이션):** `content`, `page`, `size`, `totalElements`, `totalPages` ([implementation-standards.md §7](../guides/implementation-standards.md))

---

## 3. 기능명세서 목표 API (향후 정렬)

기능명세서 FE「호출 API」+ RQ에서 **명시·유도**되는 REST 계약입니다. §2와 다른 항목은 §0 Gap 표 참조.

| Method | Path (목표) | RQ | 구현 |
| :---: | :--- | :--- | :---: |
| POST | `/api/auth/login` | LOGIN-020 | ✅ |
| POST | `/api/v1/auth/signup` | SIGNUP-037 | ✅ |
| POST | `/api/v1/invite-codes/validate` | SIGNUP-034 | ✅ |
| GET | `/api/v1/evidences/stats` | DSH-043 | 🟡 path+body |
| GET | `/api/v1/mypage/analysis-history` | DSH-045, HIS | ✅ |
| POST | `/api/v1/evidences/upload` | REQ-047~048 | 🟡 path |
| POST | `/api/v1/evidences/analyze` | REQ-049 | 🟡 path+body |
| GET | `/api/v1/cases/{caseId}` | DTL-* | ✅ |
| GET/PATCH | `/api/v1/users/me` | MYP | ✅ |
| * | `/api/v1/admin/*` | ADMIN-* | ✅ |
| GET | `/api/v1/evidences/{evidenceId}/reports/pdf` | DTL-082 | ⬜ |
| POST | `/api/v1/compare/verify` | CMP-* | ⬜ |
| GET | `/api/v1/notifications` | COM-015 | ⬜ |
| GET/PATCH | `/api/v1/users/me/settings` | COM-009 | ⬜ |

---

## 4. AI · 비동기 (REST 아님)

[RabbitMQ / JSON: ../integrations/ai-json.md](../integrations/ai-json.md) · [../integrations/rabbitmq.md](../integrations/rabbitmq.md)

---

## 5. Swagger

로컬: `http://localhost:8080/swagger-ui.html`

---

## 6. Quick Reference — 현재 구현 전체 (35 endpoints)

| # | Method | Path | Auth |
| :---: | :--- | :--- | :--- |
| 1 | POST | `/api/auth/login` | Public |
| 2 | POST | `/api/v1/auth/signup` | Public |
| 3 | GET | `/api/v1/auth/username/check` | Public |
| 4 | POST | `/api/v1/invite-codes/validate` | Public |
| 5 | GET | `/api/v1/organizations/departments` | Public |
| 6 | GET | `/api/v1/users/me` | User |
| 7 | PATCH | `/api/v1/users/me` | User |
| 8 | GET | `/api/v1/mypage/analysis-history` | User |
| 9 | GET | `/api/v1/cases/me` | User |
| 10 | GET | `/api/v1/cases/{caseId}` | User |
| 11 | GET | `/api/evidences/stats` | User |
| 12 | POST | `/api/evidences/upload` | User |
| 13 | POST | `/api/evidences/analyze` | User |
| 14 | GET | `/api/evidences/{evidenceId}/detail` | User |
| 15 | GET | `/api/evidences/{evidenceId}/analysis-status` | User |
| 16 | DELETE | `/api/evidences/{evidenceId}` | User |
| 17 | DELETE | `/api/evidences/{evidenceId}/reset` | User |
| 18 | DELETE | `/api/evidences/{evidenceId}/analysis` | User |
| 19 | GET | `/api/v1/admin/dashboard/stats` | Admin |
| 20 | GET | `/api/v1/admin/users` | Admin |
| 21 | POST | `/api/v1/admin/users/{userId}/approve` | Admin |
| 22 | POST | `/api/v1/admin/users/{userId}/reject` | Admin |
| 23 | PATCH | `/api/v1/admin/users/{userId}` | Admin |
| 24 | PATCH | `/api/v1/admin/users/{userId}/password` | Admin |
| 25 | DELETE | `/api/v1/admin/users/{userId}` | Admin |
| 26 | GET | `/api/v1/admin/invite-codes` | Admin |
| 27 | POST | `/api/v1/admin/invite-codes` | Admin |
| 28 | GET | `/api/v1/admin/evidences` | Admin |
| 29 | GET | `/api/v1/admin/evidences/{evidenceId}` | Admin |
| 30 | DELETE | `/api/v1/admin/evidences/{evidenceId}` | Admin |
| 31 | GET | `/api/v1/admin/logs` | Admin |
| 32 | GET | `/api/v1/admin/logs/export` | Admin |
| 33 | GET | `/api/v1/admin/me` | Admin |
| 34 | PATCH | `/api/v1/admin/me` | Admin |
| 35 | PATCH | `/api/v1/admin/me/password` | Admin |

---

## 7. 변경 이력

| 날짜 | 버전 | 내용 |
| :--- | :--- | :--- |
| 2026-06-17 | v1.2 | 기능명세서 대비 Gap 분석 + 현재 코드 정본 분리 |
| 2026-06-17 | v1.1 | Excel 명세 반영 초안 |
| 2026-06-09 | v1.0 | upload 중심 초안 |
