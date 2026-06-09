# 📊 ERD Specification (ForenShield) — 보완판 v2

본 문서는 기존 ERD 초안(v1)을 기준으로, **회원가입 폼에서 실제 수집 중인 필드**와
**팀 예정 기능**(초대코드 발급·저장, 계정 관리/삭제, CoC 사용자별 구분, 설정/다크모드, 메인 메타데이터)을
반영하여 빠진 부분을 보완한 버전입니다.

> 표기: 🆕 = v1에 없던 신규 / ➕ = 기존 엔티티에 추가된 컬럼

---

## 0. v1 대비 보완 요약 (빠졌던 부분)

| 구분 | v1 상태 | 보완(v2) | 근거 기능 |
| :--- | :--- | :--- | :--- |
| 가입 승인 상태 | Users에 `status` 없음 | ➕ `Users.status (PENDING/APPROVED/…)` | 가입 신청 → 관리자 승인 흐름 |
| 로그인 식별자 | email만 | ➕ `Users.loginId` | 회원가입에서 아이디 별도 입력 |
| 소속 정보 | 없음 | ➕ `organizationType, department, position, phone` | 회원가입 소속/연락처 |
| 초대코드 | 없음 | 🆕 `InviteCodes` 테이블 | "유효 생성코드 생성 후 저장" |
| CoC 사용자 구분 | Admin_Logs(관리자 행위만) | 🆕 `CustodyLogs(actorId=User FK)` | "CoC 로그 사용자별 구분" |
| 파일 메타데이터 | Evidence 일부 컬럼 | 🆕 `EvidenceMetadata` | 메인 메타데이터 영역 |
| 사용자 설정 | 없음 | ➕ `Users.darkMode` (또는 UserSettings) | 설정/다크모드 |
| 보고서 | 없음 | 🆕 `Reports` (선택) | 법정 제출용 분석 보고서 |

---

## 1. 엔티티 명세 (Entity Specifications)

### 1.1 Users (사용자 및 관리자) — ➕ 확장
시스템을 이용하는 수사관(User) 및 관리자(Admin) 정보. 가입 승인제(Status) 적용.

| 컬럼명 | Java 타입 | JPA 어노테이션 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **`userId`** | Long | `@Id`, `@GeneratedValue` | PK | 사용자 고유 식별자 |
| **`loginId`** ➕ | String | `@Column(unique = true)` | Unique, Not Null | 로그인 아이디 |
| **`email`** | String | `@Column(unique = true)` | Unique, Not Null | 이메일(승인·연락용) |
| **`password`** | String | `@Column` | Not Null | 암호화된 비밀번호 |
| **`name`** | String | `@Column` | Not Null | 사용자 성명 |
| **`phone`** ➕ | String | `@Column` | - | 연락처 |
| **`organizationType`** ➕ | OrgType | `@Enumerated(STRING)` | Not Null | 기관 유형(POLICE 등) |
| **`department`** ➕ | String | `@Column` | Not Null | 소속 기관/부서 |
| **`position`** ➕ | String | `@Column` | - | 직책/담당 업무 |
| **`role`** | UserRole | `@Enumerated(STRING)` | Not Null | 권한(`ROLE_USER`, `ROLE_ADMIN`) — 승인 시 부여 |
| **`status`** ➕ | UserStatus | `@Enumerated(STRING)` | Not Null | 가입 상태(`PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`) |
| **`inviteCodeId`** ➕ | InviteCode | `@ManyToOne`, `@JoinColumn` | FK, Nullable | 가입 시 사용한 초대코드 |
| **`darkMode`** ➕ | Boolean | `@Column` | default false | 다크모드 설정(설정 기능) |
| **`createdAt`** | LocalDateTime | `@CreatedDate` | Not Null | 계정 생성 일시 |
| **`updatedAt`** | LocalDateTime | `@LastModifiedDate` | Not Null | 정보 수정 일시 |
| **`deletedAt`** ➕ | LocalDateTime | `@Column` | Nullable | 소프트 삭제(계정 삭제 기능) |

> 설정 항목이 많아지면 `darkMode`를 별도 **`UserSettings(userId FK, key, value)`** 테이블로 분리 가능.

### 1.2 InviteCodes (초대/생성 코드) — 미정 
관리자가 발급·저장하는 가입 게이트 코드. 코드별 기관 유형·만료·사용여부 관리.

| 컬럼명 | Java 타입 | JPA 어노테이션 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **`inviteCodeId`** | Long | `@Id`, `@GeneratedValue` | PK | 코드 식별자 |
| **`code`** | String | `@Column(unique = true)` | Unique, Not Null | 코드값(예: `FSAI-POLICE-2026`) |
| **`organizationType`** | OrgType | `@Enumerated(STRING)` | Not Null | 코드가 속한 기관 유형 |
| **`issuedBy`** | User | `@ManyToOne`, `@JoinColumn` | FK, Not Null | 발급 관리자 |
| **`status`** | InviteStatus | `@Enumerated(STRING)` | Not Null | `ACTIVE`, `USED`, `EXPIRED`, `REVOKED` |
| **`expiresAt`** | LocalDateTime | `@Column` | - | 만료 일시 |
| **`createdAt`** | LocalDateTime | `@CreatedDate` | Not Null | 생성 일시 |

### 1.3 Evidences (증거 파일)
업로드된 증거의 메타데이터 및 저장 경로. SHA-256 해시로 무결성 증명.

| 컬럼명 | Java 타입 | JPA 어노테이션 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **`evidenceId`** | Long | `@Id`, `@GeneratedValue` | PK | 증거 고유 식별자 |
| **`uploaderId`** | User | `@ManyToOne`, `@JoinColumn` | FK, Not Null | 업로드 수행자 |
| **`fileName`** | String | `@Column` | Not Null | 원본 파일명 |
| **`hashValue`** | String | `@Column(length = 64)` | Unique, Not Null | 원본 SHA-256 해시 |
| **`storagePath`** | String | `@Column` | Not Null | S3 저장 경로(Origin) |
| **`fileSize`** | Long | `@Column` | Not Null | 파일 크기(Bytes) |
| **`mimeType`** | String | `@Column` | Not Null | 파일 타입(video/mp4 등) |
| **`caseId`** | String | `@Column` | Not Null | 관련 사건 번호/ID |
| **`status`** | EvidenceStatus | `@Enumerated(STRING)` | Not Null | `PENDING`, `APPROVED`, `REJECTED` |
| **`uploadedAt`** | LocalDateTime | `@Column` | Not Null | 시스템 등록 시각 |

### 1.4 EvidenceMetadata (파일 상세 메타데이터) — 🆕
메인 화면 "메타데이터" 영역용. Evidence와 1:1. (EXIF/코덱/해상도 등)

| 컬럼명 | Java 타입 | JPA 어노테이션 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **`metadataId`** | Long | `@Id`, `@GeneratedValue` | PK | 식별자 |
| **`evidenceId`** | Evidence | `@OneToOne`, `@JoinColumn` | FK, Unique, Not Null | 대상 증거(1:1) |
| **`width`** | Integer | `@Column` | - | 가로 해상도 |
| **`height`** | Integer | `@Column` | - | 세로 해상도 |
| **`durationSec`** | Integer | `@Column` | - | 영상/음성 길이(초) |
| **`codec`** | String | `@Column` | - | 코덱 정보 |
| **`capturedAt`** | LocalDateTime | `@Column` | - | 촬영/생성 일시(EXIF) |
| **`deviceInfo`** | String | `@Column` | - | 촬영 기기 정보 |
| **`exifJson`** | String | `@Column(columnDefinition = "TEXT")` | - | 원본 EXIF 전체(JSON) |

### 1.5 Analysis_Results (AI 분석 결과)
비동기 처리된 AI 분석 데이터와 위험도. Evidence와 1:1.

| 컬럼명 | Java 타입 | JPA 어노테이션 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **`analysisId`** | Long | `@Id`, `@GeneratedValue` | PK | 분석 결과 식별자 |
| **`evidenceId`** | Evidence | `@OneToOne`, `@JoinColumn` | FK, Unique, Not Null | 대상 증거(1:1) |
| **`riskScore`** | Double | `@Column` | - | 종합 위험도(0~100) |
| **`riskLevel`** | RiskLevel | `@Enumerated(STRING)` | - | `LOW`, `MEDIUM`, `HIGH` |
| **`modalityResults`** | String | `@Column(columnDefinition = "TEXT")` | - | 세부 분석 결과(JSON) |
| **`reasons`** | String | `@Column(columnDefinition = "TEXT")` | - | 분석 근거 요약 |
| **`analyzedAt`** | LocalDateTime | `@Column` | Not Null | 분석 완료 시각 |

### 1.6 CustodyLogs (CoC / 감사 로그) — 🆕 (기존 Admin_Logs 일반화)
증거 생애주기 전체의 행위 기록. **행위자(actor)를 User FK로** 두어 사용자별 구분/대시보드 지원.
(관리자 승인·반려·삭제뿐 아니라 사용자 업로드·조회·분석요청까지 포함)

| 컬럼명 | Java 타입 | JPA 어노테이션 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **`logId`** | Long | `@Id`, `@GeneratedValue` | PK | 로그 식별자 |
| **`actorId`** | User | `@ManyToOne`, `@JoinColumn` | FK, Not Null | 행위 수행자(사용자/관리자) |
| **`evidenceId`** | Evidence | `@ManyToOne`, `@JoinColumn` | FK, Nullable | 대상 증거물 |
| **`actionType`** | LogActionType | `@Enumerated(STRING)` | Not Null | `UPLOAD`, `VIEW`, `ANALYZE_REQUEST`, `ANALYZE_COMPLETE`, `APPROVE`, `REJECT`, `DELETE`, `LOGIN` … |
| **`reason`** | String | `@Column` | - | 사유(반려 시 필수) |
| **`clientIp`** | String | `@Column` | Not Null | 접속 IP |
| **`timestamp`** | LocalDateTime | `@Column` | Not Null | 기록 시각 |

> 로그 대시보드는 이 테이블을 `actorId` / `actionType` / 기간으로 집계·필터링하면 됩니다.

### 1.7 Reports (분석 보고서) — 🆕 (선택)
법정 제출용 분석 보고서. 분석 결과 기반 생성.

| 컬럼명 | Java 타입 | JPA 어노테이션 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **`reportId`** | Long | `@Id`, `@GeneratedValue` | PK | 보고서 식별자 |
| **`analysisId`** | Analysis_Result | `@ManyToOne`, `@JoinColumn` | FK, Not Null | 근거 분석 결과 |
| **`createdBy`** | User | `@ManyToOne`, `@JoinColumn` | FK, Not Null | 작성/발행자 |
| **`storagePath`** | String | `@Column` | - | 보고서 파일 경로(PDF 등) |
| **`createdAt`** | LocalDateTime | `@CreatedDate` | Not Null | 생성 일시 |

---

## 2. Enum 정의

| Enum | 값 | 설명 |
| :--- | :--- | :--- |
| **UserRole** | `ROLE_USER`, `ROLE_ADMIN` | (필요 시 `ROLE_ANALYST` 추가) |
| **UserStatus** 🆕 | `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED` | 가입 승인/계정 상태 |
| **OrgType** 🆕 | `POLICE`, `PROSECUTION`, `NFS`, `PUBLIC_SECURITY`, `ETC` | 기관 유형(회원가입과 일치) |
| **InviteStatus** 🆕 | `ACTIVE`, `USED`, `EXPIRED`, `REVOKED` | 초대코드 상태 |
| **EvidenceStatus** | `PENDING`, `APPROVED`, `REJECTED` | 증거 승인 상태 |
| **RiskLevel** | `LOW`, `MEDIUM`, `HIGH` | 위험 단계 |
| **LogActionType** 🆕 | `UPLOAD`, `VIEW`, `ANALYZE_REQUEST`, `ANALYZE_COMPLETE`, `APPROVE`, `REJECT`, `DELETE`, `LOGIN`, `LOGOUT` | CoC/감사 행위 |

---

## 3. 관계 설정 (Logical Relationships)

- **User : Evidence (1:N)** — 한 사용자가 여러 증거 업로드
- **Evidence : Analysis_Result (1:1)** — 증거당 분석 결과 1세트(재분석 시 갱신/이력)
- **Evidence : EvidenceMetadata (1:1)** 🆕 — 증거당 상세 메타데이터 1건
- **User(actor) : CustodyLog (1:N)** 🆕 — 사용자/관리자가 남기는 로그 (사용자별 구분의 핵심)
- **Evidence : CustodyLog (1:N)** — 한 증거에 대한 다회 조치
- **User(admin) : InviteCode (1:N)** 🆕 — 관리자가 코드 다수 발급(issuedBy)
- **InviteCode : User (1:N)** 🆕 — 코드로 가입한 사용자(inviteCodeId)
- **Analysis_Result : Report (1:N)** 🆕 (선택) — 분석 결과 기반 보고서

---

## 4. 데이터 흐름 (보완)

1. **초대코드 발급** 🆕 — 관리자가 `InviteCodes`에 코드 생성·저장(`ACTIVE`).
2. **가입 신청(Join)** — 사용자가 초대코드 + 소속 정보로 신청 → `Users.status = PENDING`.
3. **관리자 승인(Approve)** — 관리자가 승인 → `status = APPROVED`, `role = ROLE_USER` 부여, 코드 `USED`. 모든 조치는 `CustodyLogs`에 기록.
4. **증거 업로드(Upload)** — `Evidences`에 `PENDING` 기록 + 즉시 해시 고정 + `EvidenceMetadata` 추출. (`UPLOAD` 로그)
5. **증거 승인 → AI 분석(Analysis)** — 승인된 증거를 RabbitMQ로 AI 워커 전달 → `Analysis_Results` 저장·위험도 판정.
6. **검토/보고(Review)** — 수사관이 결과·무결성 확인, 필요 시 `Reports` 발행.
7. **계정/로그 관리** 🆕 — 관리자가 `Users` 전체 관리(상태 변경·삭제 `deletedAt`), `CustodyLogs` 대시보드로 사용자별·행위별 조회.

---

## 5. 프론트 예정 기능 ↔ 엔티티 매핑

| 화면/기능 | 관련 엔티티·컬럼 |
| :--- | :--- |
| 마이페이지 설정 / 다크모드 | `Users.darkMode` (또는 UserSettings) |
| 개인정보 수정 페이지 | `Users` (name/phone/department/position/password) |
| 관리자 - 유저 아이디 삭제 | `Users.deletedAt`, `CustodyLogs(DELETE)` |
| 관리자 - 생성코드 발급·저장 | `InviteCodes` |
| 관리자 - 전체 계정 관리 | `Users.status`, `role` |
| 관리자 - 로그 대시보드 | `CustodyLogs` 집계 |
| CoC 로그 사용자별 구분 | `CustodyLogs.actorId(User FK)` |
| 메인 - 메타데이터 영역 | `EvidenceMetadata` |
| 메인 - 최근 분석 내역 | `Analysis_Results` + `Evidences` |
