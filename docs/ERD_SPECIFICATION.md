# 📊 ERD Specification (ForenShield)

본 문서는 ForenShield 프로젝트의 데이터베이스 설계 및 엔티티 간 관계를 정의합니다. 사법적 무결성 유지와 비동기 분석 처리를 위한 구조로 설계되었습니다.

---

## 1. 뼈대 엔티티 명세 (Entity Specifications)

### 1.1 Users (사용자 및 관리자)
시스템을 이용하는 수사관(User) 및 관리자(Admin) 정보를 관리합니다.

| 컬럼명 | Java 타입 | JPA 어노테이션 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **`userId`** | Long | `@Id`, `@GeneratedValue` | PK, Not Null | 사용자 고유 식별자 |
| **`email`** | String | `@Column(unique = true)` | Unique, Not Null | 로그인용 이메일 |
| **`password`** | String | `@Column` | Not Null | 암호화된 비밀번호 |
| **`name`** | String | `@Column` | Not Null | 사용자 성명 |
| **`role`** | UserRole | `@Enumerated(STRING)` | Not Null | 권한 (`ROLE_USER`, `ROLE_ADMIN`) |
| **`createdAt`** | LocalDateTime | `@CreatedDate` | Not Null | 계정 생성 일시 |
| **`updatedAt`** | LocalDateTime | `@LastModifiedDate` | Not Null | 정보 수정 일시 |

### 1.2 Evidences (증거 파일)
업로드된 증거의 메타데이터 및 저장 경로를 관리하며, SHA-256 해시를 통해 무결성을 증명합니다.

| 컬럼명 | Java 타입 | JPA 어노테이션 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **`evidenceId`** | Long | `@Id`, `@GeneratedValue` | PK, Not Null | 증거 고유 식별자 |
| **`uploaderId`** | User | `@ManyToOne`, `@JoinColumn` | FK, Not Null | 업로드 수행자 |
| **`fileName`** | String | `@Column` | Not Null | 원본 파일명 |
| **`hashValue`** | String | `@Column(length = 64)` | Unique, Not Null | 원본 SHA-256 해시 |
| **`storagePath`** | String | `@Column` | Not Null | S3 저장 경로 (Origin) |
| **`fileSize`** | Long | `@Column` | Not Null | 파일 크기 (Bytes) |
| **`mimeType`** | String | `@Column` | Not Null | 파일 타입 (video/mp4 등) |
| **`caseId`** | String | `@Column` | Not Null | 관련 사건 번호/ID |
| **`status`** | EvidenceStatus | `@Enumerated(STRING)` | Not Null | 승인 상태 (`PENDING`, `APPROVED`, `REJECTED`) |
| **`uploadedAt`** | LocalDateTime | `@Column` | Not Null | 시스템 등록 시각 |

### 1.3 Analysis_Results (AI 분석 결과)
비동기로 처리된 AI 분석 데이터와 위험도 점수를 저장합니다.

| 컬럼명 | Java 타입 | JPA 어노테이션 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **`analysisId`** | Long | `@Id`, `@GeneratedValue` | PK, Not Null | 분석 결과 식별자 |
| **`evidenceId`** | Evidence | `@OneToOne`, `@JoinColumn`| FK, Unique, Not Null | 대상 증거 파일 (1:1) |
| **`riskScore`** | Double | `@Column` | - | 종합 위험도 (0~100) |
| **`riskLevel`** | RiskLevel | `@Enumerated(STRING)` | - | 위험 단계 (`LOW`, `MEDIUM`, `HIGH`) |
| **`modalityResults`**| String | `@Column(columnDefinition = "TEXT")` | - | 세부 분석 결과 (JSON String) |
| **`reasons`** | String | `@Column(columnDefinition = "TEXT")` | - | 분석 근거 요약 |
| **`analyzedAt`** | LocalDateTime | `@Column` | Not Null | 분석 완료 시각 |

### 1.4 Admin_Logs (관리자 행위 로그)
증거물에 대한 관리자의 승인/반려/삭제 등 주요 행위를 기록하는 감사 로그입니다.

| 컬럼명 | Java 타입 | JPA 어노테이션 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| **`logId`** | Long | `@Id`, `@GeneratedValue` | PK, Not Null | 로그 식별자 |
| **`adminId`** | User | `@ManyToOne`, `@JoinColumn` | FK, Not Null | 행위 수행 관리자 |
| **`evidenceId`** | Evidence | `@ManyToOne`, `@JoinColumn` | FK, Not Null | 대상 증거물 |
| **`actionType`** | ActionType | `@Enumerated(STRING)` | Not Null | 행위 유형 (`APPROVE`, `REJECT`, `DELETE`) |
| **`reason`** | String | `@Column` | - | 사유 (반려 시 필수) |
| **`clientIp`** | String | `@Column` | Not Null | 접속 IP 주소 |
| **`timestamp`** | LocalDateTime | `@Column` | Not Null | 로그 기록 시각 |

---

## 2. 관계 설정 (Logical Relationships)

- **User : Evidence (1:N)**: 한 명의 사용자는 여러 개의 증거를 업로드할 수 있습니다.
- **Evidence : Analysis_Result (1:1)**: 하나의 증거 파일은 최종적으로 하나의 분석 결과 세트를 가집니다. (재분석 시 결과 갱신 또는 이력 관리 가능)
- **User (Admin) : Admin_Log (1:N)**: 관리자는 여러 로그를 남깁니다.
- **Evidence : Admin_Log (1:N)**: 하나의 증거물에 대해 여러 번의 관리적 조치(예: 반려 후 재승인)가 발생할 수 있습니다.

---

## 3. 포렌식 시스템 데이터 흐름 (Data Flow)

1.  **가입 및 인증 (Join -> Login)**
    - 사용자는 가입 시 `ROLE_USER` 권한을 부여받으며, JWT를 통해 인증을 유지합니다.
2.  **증거 업로드 (Upload)**
    - 사용자가 파일을 업로드하면 `Evidences` 테이블에 `PENDING` 상태로 기록됩니다.
    - 서버는 즉시 해시값을 추출하여 무결성을 고정합니다.
3.  **관리자 승인 (Approve)**
    - 관리자가 `Admin_Logs`에 사유를 기록하며 증거를 승인(`APPROVED`)합니다.
4.  **AI 분석 수행 (Analysis)**
    - 승인된 증거는 RabbitMQ를 통해 AI 워커로 전달됩니다.
    - 분석 완료 후 `Analysis_Results`에 데이터가 저장되고 위험도가 판정됩니다.
5.  **최종 검토 (Review)**
    - 수사관은 분석 결과와 증거 무결성을 최종 확인합니다.
