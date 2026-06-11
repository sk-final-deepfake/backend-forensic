# CoC 로그 구현 기준

본 문서는 `docs/ERD_SPECIFICATION.md`의 `CustodyLogs` 설계를 기준으로, 현재 `backend-forensic` 코드에서 CoC 로그를 어떻게 연결해야 하는지 정리한 구현 기준 문서입니다.

이 문서는 과거 예시가 아니라 **현재 ERD v3 기준**을 따릅니다.

---

## 1. ERD 기준 요약

`CustodyLogs`는 CoC 및 감사 로그를 저장합니다. 초대코드 관련 이벤트는 CoC 범위에서 제외하고, 사용자/증거/분석/보고서 중심으로 기록합니다.

ERD 기준 컬럼:

```text
logId
actorId
targetType
targetId
actionType
subjectHash
storagePathAtEvent
reason
clientIp
eventPayloadJson
previousLogHash
currentLogHash
createdAt
```

중요 정책:

```text
evidenceId, analysisRequestId 같은 개별 nullable FK는 CustodyLogs에 두지 않음
targetType + targetId 조합으로 대상 엔티티를 참조
subjectHash는 이벤트 대상 파일의 SHA-256
previousLogHash/currentLogHash는 로그 레코드 자체의 해시 체인
초대코드 관련 로그는 CoC에 남기지 않음
```

---

## 2. 현재 코드 상태

현재 이미 존재하는 파일:

```text
src/main/java/com/example/demo/domain/CustodyLog.java
src/main/java/com/example/demo/repository/CustodyLogRepository.java
src/main/java/com/example/demo/service/CustodyLogService.java
src/main/java/com/example/demo/service/EvidenceDetailService.java
src/main/java/com/example/demo/service/FileService.java
src/main/java/com/example/demo/service/AnalysisService.java
```

현재 가능한 것:

```text
CustodyLog 엔티티 저장 가능
targetType + targetId 기준 조회 가능
마지막 로그의 currentLogHash 조회 가능
관리자 로그 조회에서 CustodyLog 사용
상세 페이지에서 CustodyLog 조회 후 표시 가능
```

현재 부족한 것:

```text
파일 업로드 성공 시 EVIDENCE_UPLOADED 자동 저장
SHA-256 해시 생성 후 HASH_CREATED 자동 저장
메타데이터 추출 후 METADATA_EXTRACTED 자동 저장
분석 요청 생성 후 ANALYSIS_REQUESTED 자동 저장
분석 복사본 생성/삭제 관련 CoC 저장
분석 완료/실패 관련 CoC 저장
실패 상황 ERROR_OCCURRED 저장
RabbitMQ 큐 등록 성공/실패 로그 저장
```

---

## 3. 이벤트 이름 기준

ERD 기준 `LogActionType`을 사용합니다.

증거 업로드 흐름:

```text
EVIDENCE_UPLOADED
HASH_CREATED
METADATA_EXTRACTED
```

AI 분석 흐름:

```text
ANALYSIS_COPY_CREATED
ANALYSIS_COPY_VERIFIED
ANALYSIS_REQUESTED
ANALYSIS_STARTED
ANALYSIS_COMPLETED
ANALYSIS_FAILED
ANALYSIS_COPY_DELETED
```

보고서 흐름:

```text
REPORT_CREATED
REPORT_DOWNLOADED
```

공통 실패:

```text
ERROR_OCCURRED
```

사용하지 않을 이름:

```text
FILE_UPLOADED
ORIGINAL_HASH_CREATED
INVITE_CODE_*
```

`FILE_UPLOADED`는 상세 페이지 fallback에 남아 있는 과거 표현입니다. 실제 DB 저장 이벤트는 ERD에 맞춰 `EVIDENCE_UPLOADED`를 사용해야 합니다.

---

## 4. targetType + targetId 기준

ERD에 따라 `CustodyLogs`는 개별 FK를 두지 않습니다.

대상 참조는 다음처럼 처리합니다.

| 이벤트 | targetType | targetId |
| :--- | :--- | :--- |
| 회원가입/승인/사용자 상태 변경 | `USER` | `userId` |
| 증거 업로드/해시/메타데이터/열람 | `EVIDENCE` | `evidenceId` |
| 분석 요청/시작/실패 | `ANALYSIS_REQUEST` | `analysisRequestId` |
| 분석 완료 결과 | `ANALYSIS_RESULT` | `analysisResultId` |
| 보고서 생성/다운로드 | `REPORT` | `reportId` |
| 시스템 오류 또는 대상 없음 | `SYSTEM` | 시스템용 ID |

주의:

```text
CustodyLog 엔티티에 Evidence, AnalysisRequest @ManyToOne 직접 매핑하지 않음
eventPayloadJson에 조회 보조용 evidenceId, analysisRequestId를 넣을 수는 있음
정식 참조 기준은 targetType + targetId
```

---

## 5. subjectHash 기준

`subjectHash`는 이벤트 대상 파일의 SHA-256입니다.

| 이벤트 | subjectHash |
| :--- | :--- |
| `EVIDENCE_UPLOADED` | `Evidences.originalHashValue` |
| `HASH_CREATED` | `Evidences.originalHashValue` |
| `METADATA_EXTRACTED` | `Evidences.originalHashValue` |
| `ANALYSIS_COPY_CREATED` | `Evidences.copyHashValue` |
| `ANALYSIS_COPY_VERIFIED` | `Evidences.copyHashValue` |
| `ANALYSIS_REQUESTED` | 분석 대상 증거의 `originalHashValue` 또는 `copyHashValue` |
| `ANALYSIS_COMPLETED` | 분석 대상 증거의 `copyHashValue` |
| `ANALYSIS_COPY_DELETED` | 삭제된 복사본의 `copyHashValue` |
| `REPORT_CREATED` | `Reports.reportHash` |
| `REPORT_DOWNLOADED` | `Reports.reportHash` |
| `ERROR_OCCURRED` | 관련 파일 해시가 있으면 해당 해시, 없으면 null |

주의:

```text
subjectHash = 파일 무결성 추적용 해시
currentLogHash = 로그 레코드 체인 해시
둘은 같은 값이 아님
```

---

## 6. 해시 체인 기준

각 CoC 로그는 이전 로그의 `currentLogHash`를 현재 로그의 `previousLogHash`로 연결합니다.

현재 코드의 기반:

```java
custodyLogRepository.findTopByOrderByLogIdDesc()
```

권장 해시 입력값:

```text
previousLogHash
actorId
targetType
targetId
actionType
subjectHash
storagePathAtEvent
reason
eventPayloadJson
createdAt
```

생성 방식:

```text
1. 마지막 CustodyLog.currentLogHash 조회
2. 없으면 previousLogHash = null
3. 새 로그 데이터 + previousLogHash를 문자열로 직렬화
4. SHA-256으로 currentLogHash 생성
5. CustodyLog 저장
```

운영 기준:

```text
로그는 append-only 성격으로 다룸
기존 로그 수정/삭제 금지
정정이 필요하면 새 로그로 보정 이벤트 기록
```

---

## 7. 파일 업로드 흐름 기준

ERD 기준 흐름:

```text
1. 사용자가 증거 파일 업로드
2. Evidences 저장 (status = UPLOADED)
3. 원본 SHA-256 → originalHashValue 저장
4. EvidenceMetadata 자동 추출 (extractionStatus 기록)
5. CustodyLogs: EVIDENCE_UPLOADED (subjectHash = originalHashValue)
6. CustodyLogs: HASH_CREATED (subjectHash = originalHashValue)
7. CustodyLogs: METADATA_EXTRACTED
```

현재 연결 대상:

```text
src/main/java/com/example/demo/service/FileService.java
```

저장 예시:

### 7.1 EVIDENCE_UPLOADED

```text
actorId: uploaderId
targetType: EVIDENCE
targetId: evidenceId
actionType: EVIDENCE_UPLOADED
subjectHash: originalHashValue
storagePathAtEvent: originalStoragePath
reason: 증거 파일 업로드 완료
eventPayloadJson:
  {
    "fileName": "...",
    "fileType": "VIDEO",
    "mimeType": "video/mp4",
    "fileSize": 12345,
    "caseName": "..."
  }
```

### 7.2 HASH_CREATED

```text
actorId: uploaderId
targetType: EVIDENCE
targetId: evidenceId
actionType: HASH_CREATED
subjectHash: originalHashValue
storagePathAtEvent: originalStoragePath
reason: SHA-256 해시 생성 완료
eventPayloadJson:
  {
    "hashAlgorithm": "SHA-256",
    "hashValue": "..."
  }
```

### 7.3 METADATA_EXTRACTED

```text
actorId: uploaderId
targetType: EVIDENCE
targetId: evidenceId
actionType: METADATA_EXTRACTED
subjectHash: originalHashValue
storagePathAtEvent: originalStoragePath
reason: 메타데이터 추출 완료
eventPayloadJson:
  {
    "extractionStatus": "SUCCESS"
  }
```

메타데이터 추출이 일부 실패하면 `extractionStatus = PARTIAL` 또는 `FAILED`를 기록하고, 업로드 자체는 성공 처리할 수 있습니다.

---

## 8. AI 분석 흐름 기준

ERD 기준 흐름:

```text
1. 사용자가 분석 요청 버튼 클릭
2. AnalysisRequests 생성 (status = QUEUED)
3. 원본 → 복사본 생성 (copyStoragePath, copyStatus = ACTIVE)
4. 복사본 SHA-256 → copyHashValue 저장
5. CustodyLogs: ANALYSIS_COPY_CREATED (subjectHash = copyHashValue)
6. 원본/복사본 해시 검증
7. CustodyLogs: ANALYSIS_COPY_VERIFIED
8. AI 서버 분석 시작 (status = ANALYZING)
9. AI 분석 완료 → AnalysisResults, AnalysisModuleResults 저장
10. AnalysisRequests.status = COMPLETED
11. CustodyLogs: ANALYSIS_COMPLETED
12. 복사본 삭제 → copyStatus = DELETED
13. CustodyLogs: ANALYSIS_COPY_DELETED
```

현재 연결 대상:

```text
src/main/java/com/example/demo/service/AnalysisService.java
```

현재 코드는 `AnalysisRequest` 생성과 `QUEUED` 저장까지만 처리합니다. RabbitMQ publish, 분석 복사본 생성, 분석 시작/완료 저장은 아직 별도 구현이 필요합니다.

### 8.1 ANALYSIS_REQUESTED

```text
actorId: requestedBy
targetType: ANALYSIS_REQUEST
targetId: analysisRequestId
actionType: ANALYSIS_REQUESTED
subjectHash: evidence.originalHashValue
storagePathAtEvent: evidence.originalStoragePath
reason: AI 분석 요청 생성 완료
eventPayloadJson:
  {
    "evidenceId": 1,
    "analysisRequestId": 10,
    "status": "QUEUED"
  }
```

RabbitMQ 큐 등록이 추가되면 payload에 다음 값을 포함할 수 있습니다.

```text
queueRegistered: true
queueName: "..."
```

큐 등록 실패 시:

```text
AnalysisRequests.status = FAILED
CustodyLogs: ERROR_OCCURRED
```

---

## 9. ERROR_OCCURRED 기준

실패 로그도 ERD 기준으로 `targetType + targetId`를 사용합니다.

예시:

```text
actorId: 요청 사용자 ID 또는 시스템 사용자 ID
targetType: EVIDENCE / ANALYSIS_REQUEST / SYSTEM
targetId: 관련 대상 ID
actionType: ERROR_OCCURRED
subjectHash: 관련 파일 해시가 있으면 저장
storagePathAtEvent: 관련 경로가 있으면 저장
reason: 사용자/관리자에게 보여줄 수 있는 실패 요약
eventPayloadJson:
  {
    "step": "HASH_CREATED",
    "errorCode": "...",
    "message": "..."
  }
```

주의:

```text
password, token, secret, 전체 초대코드 원문은 eventPayloadJson이나 reason에 저장하지 않음
```

---

## 10. 조회 기준

현재 Repository:

```java
findByTargetTypeAndTargetIdOrderByCreatedAtAsc(...)
```

증거 기준 조회:

```text
targetType = EVIDENCE
targetId = evidenceId
```

분석 요청 기준 조회:

```text
targetType = ANALYSIS_REQUEST
targetId = analysisRequestId
```

상세 페이지에서는 `EvidenceDetailService`가 `CustodyLogRepository`를 사용합니다. 실제 로그가 저장되면 fallback 대신 DB 로그를 표시하는 방향으로 정리합니다.

---

## 11. 구현 우선순위

1차 구현:

```text
CustodyLogService에 범용 record 메서드 추가
FileService 업로드 성공 후 EVIDENCE_UPLOADED 저장
FileService 해시 생성 후 HASH_CREATED 저장
AnalysisService 요청 생성 후 ANALYSIS_REQUESTED 저장
previousLogHash/currentLogHash 체인 연결
EVIDENCE 기준 CoC 조회 테스트
```

2차 구현:

```text
METADATA_EXTRACTED 저장
ERROR_OCCURRED 저장
ANALYSIS_COPY_CREATED / VERIFIED / DELETED 저장
ANALYSIS_STARTED / COMPLETED / FAILED 저장
RabbitMQ publish 성공/실패 연동
REPORT_CREATED / REPORT_DOWNLOADED 저장
```

---

## 12. 테스트 기준

테스트 위치 예시:

```text
src/test/java/com/example/demo/service/CustodyLogServiceTest.java
src/test/java/com/example/demo/controller/EvidenceControllerTest.java
src/test/java/com/example/demo/controller/AuthControllerTest.java
```

검증 항목:

```text
파일 업로드 성공 시 EVIDENCE_UPLOADED 로그 저장
해시 생성 성공 시 HASH_CREATED 로그 저장
두 로그의 previousLogHash/currentLogHash 연결
분석 요청 성공 시 ANALYSIS_REQUESTED 로그 저장
targetType + targetId 기준 조회 가능
subjectHash와 currentLogHash가 다른 의미로 저장됨
실패 상황 발생 시 ERROR_OCCURRED 저장
```

---

## 13. 현재 상태 요약

```text
ERD 기준 CustodyLog 엔티티: 있음
targetType + targetId 구조: 있음
subjectHash/currentLogHash 컬럼 구분: 있음
CustodyLogRepository: 있음
관리자 로그 조회: 있음
상세 페이지 fallback 표시: 있음
파일 업로드 CoC 자동 저장: 없음
해시 생성 CoC 자동 저장: 없음
분석 요청 CoC 자동 저장: 없음
RabbitMQ 큐 등록 로그: 없음
```

따라서 구현은 신규 기능이지만, 새 테이블을 만들기보다 기존 `CustodyLog` 구조를 ERD 기준으로 재사용해서 연결하면 됩니다.
