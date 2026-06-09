# ForenShield AI ERD Specification v3

본 문서는 ForenShield AI의 회원가입, 증거 업로드, 메타데이터 추출, AI 분석 요청, 분석 결과 저장, PDF 보고서 저장, CoC 로그 구조를 반영한 **정규화 ERD 보완판 v3**입니다.

> 기준: 초대코드는 유지하되, CoC 로그에서는 초대코드 관련 이벤트를 제외합니다.  
> PDF 보고서는 생성 후 저장하는 구조입니다.  
> AI 분석은 증거 업로드 직후 자동 실행하지 않고, 사용자가 분석 요청 버튼을 눌렀을 때 실행됩니다.

---

## 0. 최종 정책 요약

| 구분 | 최종 결정 |
| :--- | :--- |
| 초대코드 | 1회용 코드 사용 |
| 초대코드 테이블 | 유지 |
| CoC 로그에서 초대코드 | 제외 |
| 회원가입 후 상태 | `PENDING` |
| 관리자 승인 전 로그인 | 차단 |
| 사용자의 역할 선택 | 없음 |
| 내부 권한 role | DB에는 필요, 관리자가 승인 시 부여 |
| 사건 관리 | `caseNumber` 문자열로 유지 |
| 같은 증거 파일 중복 업로드 | 허용 |
| 해시값 Unique | 제거 |
| 메타데이터 추출 | 업로드 직후 자동 추출 |
| AI 분석 실행 | 사용자가 분석 요청 버튼을 눌러야 실행 |
| 재분석 | MVP에서는 불허 |
| 분석 실패 시 재시도 | 허용 |
| AI 분석 결과 | PostgreSQL 저장 |
| PDF 보고서 | 생성 후 저장 |
| PDF 저장 위치 | S3 저장 권장 |
| CoC 해시 체인 | `previousLogHash`, `currentLogHash` 사용 |
| CoC 로그 대상 | 사용자, 증거, 분석 요청/결과, 보고서 중심 |
| 분석 건수 통계 | 이미지/영상/음성 분석 건수는 DB 컬럼으로 저장하지 않고 집계 조회로 계산 |

---

## 1. 최종 테이블 목록

MVP 기준 최종 테이블은 다음과 같습니다.

```text
Users
InviteCodes
Evidences
EvidenceMetadata
AnalysisRequests
AnalysisResults
AnalysisModuleResults
Reports
CustodyLogs
```

분석 건수 통계는 별도 테이블 없이 `Evidences.fileType`, `AnalysisRequests.status`, `AnalysisResults`를 조인하여 계산합니다.

```text
이미지 분석 건수 = fileType = IMAGE 이면서 분석 완료된 건수
영상 분석 건수 = fileType = VIDEO 이면서 분석 완료된 건수
음성 분석 건수 = fileType = AUDIO 이면서 분석 완료된 건수
```

선택적으로 추후 확장 가능한 테이블은 다음과 같습니다.

```text
Cases
OrganizationUnits
UserSettings
AnalysisReasons
```

이번 MVP에서는 `Cases`를 별도 테이블로 분리하지 않고, `Evidences.caseNumber` 문자열로 관리합니다.

---

## 2. 핵심 설계 원칙

### 2.1 회원가입은 일반 가입이 아니라 계정 신청이다

ForenShield AI는 내부 사용자 전용 포렌식 분석 보조 시스템이므로, 사용자는 회원가입 직후 바로 시스템을 사용할 수 없습니다.

```text
회원가입 신청
→ Users.status = PENDING
→ 관리자 검토
→ APPROVED 처리
→ 로그인 가능
```

### 2.2 사용자는 역할을 직접 선택하지 않는다

회원가입 화면에서 `ROLE_USER`, `ROLE_ADMIN`, `ROLE_ANALYST` 같은 역할을 선택하지 않습니다.  
역할은 관리자가 승인 시 내부적으로 부여합니다.

단, DB에는 권한 분리를 위해 `role` 컬럼이 필요합니다.

```text
화면: 역할 선택 없음
DB: role 컬럼 필요
승인 시 관리자 부여
```

### 2.3 초대코드는 가입 게이트 역할이다

초대코드는 1회용으로 사용합니다.  
사용자가 회원가입 시 초대코드를 입력하고, 유효한 코드일 때 가입 신청이 가능합니다.

다만 초대코드 발급/사용 자체는 포렌식 증거 생애주기의 핵심이 아니므로, `CustodyLogs`에는 초대코드 관련 이벤트를 남기지 않습니다.

### 2.4 증거 파일은 중복 업로드를 허용한다

같은 파일이더라도 다른 사건 번호나 다른 맥락에서 다시 등록될 수 있으므로, `hashValue`에 Unique 제약을 걸지 않습니다.

```text
hashValue NOT NULL
hashValue UNIQUE 제거
```

### 2.5 메타데이터는 업로드 직후 자동 추출한다

증거 파일 업로드 후 자동으로 다음 작업을 수행합니다.

```text
파일 업로드
→ SHA-256 해시 생성
→ 메타데이터 자동 추출
→ EvidenceMetadata 저장
```

### 2.6 AI 분석은 사용자가 요청해야 실행된다

증거 업로드만으로 AI 분석을 자동 실행하지 않습니다.  
사용자가 분석 요청 버튼을 눌렀을 때 `AnalysisRequests`가 생성되고 AI 분석이 시작됩니다.

### 2.7 재분석은 MVP에서 허용하지 않는다

분석 완료 후 재분석을 허용하면 결과 불일치 문제가 발생할 수 있습니다.

예시:

```text
1차 분석: 위험도 82
2차 분석: 위험도 61
```

이 경우 어떤 결과를 보고서에 반영해야 하는지 애매해집니다.  
따라서 MVP에서는 재분석을 허용하지 않습니다.

정책은 다음과 같습니다.

```text
COMPLETED 상태: 재분석 불가
FAILED 상태: 재시도 가능
```

---

## 3. Entity Specifications

---

## 3.1 Users

사용자, 분석 담당자, 관리자 계정 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `userId` | Long | PK | 사용자 고유 ID |
| `loginId` | String | Unique, Not Null | 로그인 아이디 |
| `email` | String | Unique, Not Null | 이메일, 승인 및 연락용 |
| `password` | String | Not Null | 암호화된 비밀번호 |
| `name` | String | Not Null | 실명 |
| `phone` | String | Nullable | 관리자 확인 및 연락용 연락처 |
| `organizationType` | OrgType | Not Null | 기관 유형 |
| `department` | String | Not Null | 소속 기관/부서 |
| `position` | String | Nullable | 직책/담당 업무 |
| `role` | UserRole | Not Null | 내부 권한 |
| `status` | UserStatus | Not Null | 가입 승인 상태 |
| `darkMode` | Boolean | Default false | 다크모드 설정 |
| `inviteCodeId` | Long | FK, Nullable | 가입 시 사용한 초대코드 |
| `createdAt` | LocalDateTime | Not Null | 생성 일시 |
| `updatedAt` | LocalDateTime | Not Null | 수정 일시 |
| `deletedAt` | LocalDateTime | Nullable | 소프트 삭제 일시 |

### 정책

- 회원가입 직후 `status = PENDING`
- 승인 전 로그인 불가
- 사용자는 역할을 선택하지 않음
- 관리자가 승인 시 `role` 부여
- 계정 삭제는 실제 삭제가 아니라 `deletedAt` 기반 소프트 삭제 권장

---

## 3.2 InviteCodes

관리자가 발급하는 1회용 가입 코드입니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `inviteCodeId` | Long | PK | 초대코드 ID |
| `code` | String | Unique, Not Null | 초대코드 값 |
| `organizationType` | OrgType | Not Null | 코드가 속한 기관 유형 |
| `issuedBy` | Long | FK, Not Null | 발급 관리자 ID |
| `status` | InviteStatus | Not Null | 코드 상태 |
| `expiresAt` | LocalDateTime | Nullable | 만료 일시 |
| `createdAt` | LocalDateTime | Not Null | 생성 일시 |
| `usedAt` | LocalDateTime | Nullable | 사용 일시 |

### 정책

- 초대코드는 1회용
- 회원가입 신청 성공 시 `USED` 처리
- 초대코드 관련 이벤트는 `CustodyLogs`에 남기지 않음
- 초대코드 이력은 `InviteCodes.status`, `usedAt`, `issuedBy`로 관리

---

## 3.3 Evidences

업로드된 원본 증거 파일 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `evidenceId` | Long | PK | 증거 ID |
| `uploaderId` | Long | FK, Not Null | 업로드 사용자 ID |
| `caseNumber` | String | Nullable | 사건 번호 문자열 |
| `fileName` | String | Not Null | 원본 파일명 |
| `fileType` | FileType | Not Null | IMAGE, VIDEO, AUDIO |
| `mimeType` | String | Not Null | MIME 타입 |
| `fileSize` | Long | Not Null | 파일 크기 |
| `hashValue` | String | Not Null | 원본 SHA-256 해시 |
| `storagePath` | String | Not Null | S3 원본 파일 경로 |
| `status` | EvidenceStatus | Not Null | 증거 상태 |
| `uploadedAt` | LocalDateTime | Not Null | 업로드 일시 |
| `deletedAt` | LocalDateTime | Nullable | 소프트 삭제 일시 |

### 중요 변경

같은 증거 파일의 여러 번 업로드를 허용하므로 `hashValue`에는 Unique를 걸지 않습니다.

```text
기존: hashValue UNIQUE
변경: hashValue NOT NULL
```

---

## 3.4 EvidenceMetadata

증거 파일의 상세 메타데이터를 저장합니다.  
Evidence와 1:1 관계입니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `metadataId` | Long | PK | 메타데이터 ID |
| `evidenceId` | Long | FK, Unique, Not Null | 대상 증거 ID |
| `width` | Integer | Nullable | 가로 해상도 |
| `height` | Integer | Nullable | 세로 해상도 |
| `durationSec` | Integer | Nullable | 영상/음성 길이 |
| `fps` | Double | Nullable | 프레임레이트 |
| `codec` | String | Nullable | 코덱 정보 |
| `sampleRate` | Integer | Nullable | 오디오 샘플레이트 |
| `channels` | Integer | Nullable | 오디오 채널 수 |
| `capturedAt` | LocalDateTime | Nullable | 촬영/생성 시각 |
| `deviceInfo` | String | Nullable | 촬영 기기 정보 |
| `exifJson` | JSONB/Text | Nullable | EXIF 전체 |
| `ffprobeJson` | JSONB/Text | Nullable | ffprobe 결과 전체 |
| `createdAt` | LocalDateTime | Not Null | 생성 일시 |

### 정책

- 업로드 직후 자동 추출
- 추출 실패 시에도 업로드 자체는 성공 처리 가능
- 실패 정보는 로그 또는 메타데이터 일부 컬럼으로 관리 가능

---

## 3.5 AnalysisRequests

AI 분석 요청 상태를 관리합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `analysisRequestId` | Long | PK | 분석 요청 ID |
| `evidenceId` | Long | FK, Not Null | 분석 대상 증거 |
| `requestedBy` | Long | FK, Not Null | 분석 요청 사용자 |
| `status` | AnalysisStatus | Not Null | 요청 상태 |
| `requestedAt` | LocalDateTime | Not Null | 요청 시각 |
| `startedAt` | LocalDateTime | Nullable | 분석 시작 시각 |
| `completedAt` | LocalDateTime | Nullable | 분석 완료 시각 |
| `errorCode` | String | Nullable | 실패 코드 |
| `errorMessage` | String | Nullable | 실패 메시지 |

### 정책

- 증거 업로드만으로 자동 분석하지 않음
- 사용자가 분석 요청 버튼 클릭 시 생성
- `COMPLETED` 상태가 이미 있으면 재분석 불가
- `FAILED` 상태는 재시도 가능

---

## 3.6 AnalysisResults

AI 분석 결과의 최종 요약 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `analysisResultId` | Long | PK | 분석 결과 ID |
| `analysisRequestId` | Long | FK, Unique, Not Null | 분석 요청 ID |
| `riskScore` | Double | Nullable | 종합 위험도 |
| `confidenceScore` | Double | Nullable | 결과 신뢰도 |
| `riskLevel` | RiskLevel | Nullable | LOW, MEDIUM, HIGH |
| `summary` | Text/String | Nullable | 분석 요약 |
| `analyzedAt` | LocalDateTime | Not Null | 분석 완료 시각 |

### 위험도 기준

```text
LOW: 0 ~ 39
MEDIUM: 40 ~ 69
HIGH: 70 ~ 100
```

---

## 3.7 AnalysisModuleResults

영상, 음성, 이미지 등 세부 모듈별 분석 결과를 저장합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `moduleResultId` | Long | PK | 모듈 결과 ID |
| `analysisResultId` | Long | FK, Not Null | 분석 결과 ID |
| `fileType` | FileType | Nullable | IMAGE, VIDEO, AUDIO |
| `moduleName` | String | Not Null | 분석 모듈명 |
| `detected` | Boolean | Nullable | 탐지 여부 |
| `score` | Double | Nullable | 모듈 점수 |
| `confidence` | Double | Nullable | 모듈 신뢰도 |
| `modelName` | String | Nullable | 모델명 |
| `modelVersion` | String | Nullable | 모델 버전 |
| `evidenceText` | Text/String | Nullable | 분석 근거 문장 |
| `detailsJson` | JSONB/Text | Nullable | 세부 결과 JSON |
| `createdAt` | LocalDateTime | Not Null | 생성 일시 |

### moduleName 예시

```text
face_swap_detection
lip_sync_analysis
synthetic_voice_detection
generated_image_detection
metadata_analysis
```

---

## 3.8 Reports

AI 분석 결과를 기반으로 생성된 PDF 보고서 정보를 저장합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `reportId` | Long | PK | 보고서 ID |
| `analysisResultId` | Long | FK, Not Null | 기반 분석 결과 |
| `evidenceId` | Long | FK, Not Null | 대상 증거 |
| `createdBy` | Long | FK, Not Null | 보고서 생성 사용자 |
| `reportFileName` | String | Nullable | PDF 파일명 |
| `storagePath` | String | Not Null | PDF 저장 경로 |
| `reportHash` | String | Nullable | PDF 파일 SHA-256 |
| `fileSize` | Long | Nullable | PDF 파일 크기 |
| `createdAt` | LocalDateTime | Not Null | 생성 일시 |

### 정책

- AI 분석 결과는 PostgreSQL에 저장
- PDF 파일은 S3에 저장
- Reports 테이블에는 PDF 경로, 파일명, 해시, 크기 저장
- 보고서에는 원본 파일 해시와 PDF 보고서 해시를 함께 표시

---

## 3.9 CustodyLogs

CoC 및 감사 로그를 저장합니다.  
초대코드 관련 이벤트는 제외하고, 사용자/증거/분석/보고서 중심으로 기록합니다.

| 컬럼명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| `logId` | Long | PK | 로그 ID |
| `actorId` | Long | FK, Not Null | 행위자 ID |
| `evidenceId` | Long | FK, Nullable | 관련 증거 ID |
| `analysisRequestId` | Long | FK, Nullable | 관련 분석 요청 ID |
| `targetType` | LogTargetType | Not Null | 로그 대상 유형 |
| `targetId` | Long | Not Null | 로그 대상 ID |
| `actionType` | LogActionType | Not Null | 행위 유형 |
| `reason` | String | Nullable | 사유 |
| `clientIp` | String | Nullable | 접속 IP |
| `eventPayloadJson` | JSONB/Text | Nullable | 변경 내용/이벤트 상세 |
| `previousLogHash` | String | Nullable | 이전 로그 해시 |
| `currentLogHash` | String | Not Null | 현재 로그 해시 |
| `createdAt` | LocalDateTime | Not Null | 로그 생성 시각 |

### targetType

```text
USER
EVIDENCE
ANALYSIS_REQUEST
ANALYSIS_RESULT
REPORT
SYSTEM
```

`INVITE_CODE`는 사용하지 않습니다.

### actionType

```text
LOGIN
LOGOUT

SIGNUP_REQUEST
USER_APPROVED
USER_REJECTED
USER_SUSPENDED
USER_DELETED

EVIDENCE_UPLOADED
HASH_CREATED
METADATA_EXTRACTED
EVIDENCE_VIEWED

ANALYSIS_REQUESTED
ANALYSIS_STARTED
ANALYSIS_COMPLETED
ANALYSIS_FAILED

REPORT_CREATED
REPORT_DOWNLOADED

ERROR_OCCURRED
```

초대코드 관련 actionType은 사용하지 않습니다.

---

## 4. Enum 정의

| Enum | 값 |
| :--- | :--- |
| `UserRole` | `ROLE_USER`, `ROLE_ADMIN` |
| `UserStatus` | `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED` |
| `OrgType` | `POLICE`, `PROSECUTION`, `NFS`, `PUBLIC_SECURITY`, `ETC` |
| `InviteStatus` | `ACTIVE`, `USED`, `EXPIRED`, `REVOKED` |
| `FileType` | `IMAGE`, `VIDEO`, `AUDIO` |
| `EvidenceStatus` | `UPLOADED`, `ANALYSIS_REQUESTED`, `ANALYZING`, `ANALYZED`, `FAILED`, `DELETED` |
| `AnalysisStatus` | `QUEUED`, `ANALYZING`, `COMPLETED`, `FAILED` |
| `RiskLevel` | `LOW`, `MEDIUM`, `HIGH` |
| `LogTargetType` | `USER`, `EVIDENCE`, `ANALYSIS_REQUEST`, `ANALYSIS_RESULT`, `REPORT`, `SYSTEM` |
| `LogActionType` | `LOGIN`, `LOGOUT`, `SIGNUP_REQUEST`, `USER_APPROVED`, `USER_REJECTED`, `USER_SUSPENDED`, `USER_DELETED`, `EVIDENCE_UPLOADED`, `HASH_CREATED`, `METADATA_EXTRACTED`, `EVIDENCE_VIEWED`, `ANALYSIS_REQUESTED`, `ANALYSIS_STARTED`, `ANALYSIS_COMPLETED`, `ANALYSIS_FAILED`, `REPORT_CREATED`, `REPORT_DOWNLOADED`, `ERROR_OCCURRED` |

---

## 5. 관계 설정

```text
Users 1:N Evidences
Users 1:N AnalysisRequests
Users 1:N Reports
Users 1:N CustodyLogs

InviteCodes 1:N Users
Users 1:N InviteCodes issuedBy 기준

Evidences 1:1 EvidenceMetadata
Evidences 1:N AnalysisRequests
Evidences 1:N Reports
Evidences 1:N CustodyLogs

AnalysisRequests 1:1 AnalysisResults
AnalysisRequests 1:N CustodyLogs

AnalysisResults 1:N AnalysisModuleResults
AnalysisResults 1:N Reports
AnalysisResults 1:N CustodyLogs

Reports 1:N CustodyLogs
```

---

## 6. 최종 데이터 흐름

### 6.1 회원가입 및 승인 흐름

```text
1. 관리자가 1회용 초대코드 생성
2. 사용자가 초대코드로 회원가입 신청
3. Users.status = PENDING
4. 관리자 승인
5. Users.status = APPROVED
6. 관리자 승인 전까지 로그인 불가
7. 승인 후 로그인 가능
```

### 6.2 증거 업로드 및 메타데이터 흐름

```text
1. 사용자가 증거 파일 업로드
2. Evidences 저장
3. SHA-256 해시 생성
4. EvidenceMetadata 자동 추출
5. CustodyLogs에 EVIDENCE_UPLOADED 기록
6. CustodyLogs에 HASH_CREATED 기록
7. CustodyLogs에 METADATA_EXTRACTED 기록
```

### 6.3 AI 분석 흐름

```text
1. 사용자가 분석 요청 버튼 클릭
2. AnalysisRequests 생성
3. status = QUEUED
4. AI 서버 분석 시작
5. status = ANALYZING
6. AI 분석 완료
7. AnalysisResults 저장
8. AnalysisModuleResults 저장
9. status = COMPLETED
10. CustodyLogs에 ANALYSIS_COMPLETED 기록
```

### 6.4 PDF 보고서 흐름

```text
1. 사용자가 PDF 다운로드 클릭
2. 분석 결과 기반 PDF 생성
3. PDF 파일 S3 저장
4. Reports 저장
5. PDF SHA-256 해시 생성
6. CustodyLogs에 REPORT_CREATED 기록
7. 다운로드 시 REPORT_DOWNLOADED 기록
```

---

## 7. 재분석 정책

MVP에서는 재분석을 허용하지 않습니다.

```text
COMPLETED 상태의 분석 결과가 존재하면 분석 요청 버튼 비활성화
FAILED 상태의 분석 요청은 재시도 가능
```

### 재분석을 허용하지 않는 이유

재분석 결과가 이전 결과와 다르게 나올 수 있기 때문입니다.

```text
1차 분석: 위험도 82
2차 분석: 위험도 61
```

이 경우 어떤 결과를 보고서에 반영해야 하는지 애매해집니다.  
따라서 MVP에서는 결과의 일관성과 보고서 신뢰성을 위해 완료된 분석의 재분석을 막습니다.

### 추후 재분석을 허용한다면

기존 결과를 수정하지 않고 새 분석 요청과 새 분석 결과를 생성해야 합니다.

```text
기존 AnalysisResult 수정 X
새 AnalysisRequest 생성 O
새 AnalysisResult 생성 O
modelName, modelVersion, analyzedAt 저장 O
```

---

## 8. 분석 건수 통계

메인 화면 또는 관리자 대시보드에서 보여줄 수 있는 이미지/영상/음성 분석 건수는 별도 컬럼으로 저장하지 않고, 기존 테이블을 기반으로 집계합니다.

### 8.1 통계 항목

| 항목 | 기준 |
| :--- | :--- |
| 이미지 분석 건수 | `Evidences.fileType = IMAGE` 이고 분석 완료된 건수 |
| 영상 분석 건수 | `Evidences.fileType = VIDEO` 이고 분석 완료된 건수 |
| 음성 분석 건수 | `Evidences.fileType = AUDIO` 이고 분석 완료된 건수 |
| 전체 분석 건수 | 분석 완료된 전체 건수 |

### 8.2 집계 기준

분석 건수는 단순 업로드 건수가 아니라 **AI 분석이 완료된 건수**를 기준으로 합니다.

```text
업로드만 된 파일 = 분석 건수에 포함하지 않음
분석 요청 후 COMPLETED 된 파일 = 분석 건수에 포함
FAILED 된 분석 요청 = 실패 건수로 별도 집계 가능
```

### 8.3 SQL 집계 예시

```sql
SELECT
    e.file_type,
    COUNT(*) AS completed_analysis_count
FROM analysis_requests ar
JOIN evidences e
    ON ar.evidence_id = e.evidence_id
JOIN analysis_results r
    ON ar.analysis_request_id = r.analysis_request_id
WHERE ar.status = 'COMPLETED'
GROUP BY e.file_type;
```

예상 결과 예시는 다음과 같습니다.

```text
IMAGE | 12
VIDEO | 8
AUDIO | 5
```

### 8.4 대시보드 표시 예시

```text
이미지 분석 12건
영상 분석 8건
음성 분석 5건
전체 분석 25건
```

### 8.5 설계 판단

이미지/영상/음성 분석 건수를 별도 컬럼으로 저장하지 않는 이유는 다음과 같습니다.

```text
분석 건수는 기존 데이터에서 계산 가능한 값
별도 저장 시 실제 분석 결과와 통계 값이 불일치할 수 있음
대시보드 조회 시 COUNT/GROUP BY로 계산하는 것이 더 안전함
```

따라서 ERD에는 별도 `imageAnalysisCount`, `videoAnalysisCount`, `audioAnalysisCount` 컬럼을 추가하지 않습니다.  
필요 시 추후 성능 최적화를 위해 `AnalysisStatistics` 또는 캐시 테이블을 추가할 수 있습니다.

---

## 9. 발표/문서용 요약 문장

ForenShield AI는 초대코드 기반 내부 사용자 계정 신청 구조를 사용합니다. 초대코드는 1회용으로 관리되며, 사용자는 가입 신청 후 `PENDING` 상태가 되고 관리자 승인 전까지 로그인이 제한됩니다. 사용자는 가입 시 역할을 선택하지 않으며, 관리자가 승인 시 내부 권한을 부여합니다.

증거 파일은 업로드 즉시 SHA-256 해시와 메타데이터를 자동 생성합니다. 단, AI 분석은 자동 실행하지 않고 사용자가 분석 요청 버튼을 눌렀을 때 수행됩니다. MVP에서는 분석 완료 후 재분석을 허용하지 않으며, 실패한 분석에 대해서만 재시도를 허용합니다.

AI 분석 결과는 PostgreSQL에 저장하고, 사용자는 분석 결과를 PDF 보고서로 다운로드할 수 있습니다. 생성된 PDF는 S3에 저장하며, Reports 테이블에는 보고서 경로, 파일명, 해시값을 저장합니다. CoC 로그는 사용자, 증거, 분석 요청/결과, 보고서 중심으로 기록하며 초대코드 관련 이벤트는 CoC 범위에서 제외합니다.
