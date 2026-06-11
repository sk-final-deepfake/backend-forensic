# CoC 로그 구현 1단계 프롬프트

아래 프롬프트는 구현 AI에게 전달하기 위한 1단계 작업 지시서입니다.

1단계 목표는 **파일 업로드나 분석 요청 흐름에 아직 연결하지 않고**, ERD 기준 `CustodyLogs`를 저장할 수 있는 공통 서비스 기반만 안정적으로 만드는 것입니다.

---

## 구현 프롬프트

```text
너는 Spring Boot 백엔드 개발자다.
현재 backend-forensic 프로젝트에서 ERD 기준 CoC 로그 저장 기능의 1단계를 구현해야 한다.

반드시 아래 문서를 먼저 확인해라.
- docs/ERD_SPECIFICATION.md
- docs/COC_LOG_IMPLEMENTATION_GUIDE.md

이번 1단계 범위:
파일 업로드, 해시 생성, 분석 요청 흐름에는 아직 연결하지 않는다.
우선 CustodyLogService를 ERD 기준으로 확장하고, 해시 체인 저장이 올바르게 동작하는지 테스트한다.

절대 하지 말 것:
- FileService.upload(...) 수정하지 말 것
- AnalysisService.startAnalysis(...) 수정하지 말 것
- RabbitMQ 연동하지 말 것
- CustodyLogs에 evidenceId, analysisRequestId 같은 개별 FK 추가하지 말 것
- ERD에 없는 새 컬럼 추가하지 말 것
- FILE_UPLOADED 같은 비ERD 이벤트명 추가하지 말 것

ERD 기준:
CustodyLogs는 targetType + targetId 조합으로 대상 엔티티를 참조한다.

CustodyLogs 필드:
- actorId
- targetType
- targetId
- actionType
- subjectHash
- storagePathAtEvent
- reason
- clientIp
- eventPayloadJson
- previousLogHash
- currentLogHash
- createdAt

subjectHash와 currentLogHash는 다른 개념이다.
- subjectHash: 이벤트 대상 파일의 SHA-256
- currentLogHash: 로그 레코드 자체의 체인 해시

구현 작업:

1. CustodyLogService 확인
현재 파일:
src/main/java/com/example/demo/service/CustodyLogService.java

기존 recordUserAction(...)은 삭제하지 말고 유지한다.
기존 관리자/사용자 로그 기능이 깨지면 안 된다.

2. 범용 record 메서드 추가
CustodyLogService에 ERD 기준 범용 메서드를 추가한다.

예시 시그니처:

public CustodyLog record(
    Long actorId,
    CustodyTargetType targetType,
    Long targetId,
    String actionType,
    String subjectHash,
    String storagePathAtEvent,
    String reason,
    String eventPayloadJson,
    String clientIp
)

반환값은 저장된 CustodyLog로 한다.

3. 필수 검증
record 메서드에서 아래 값은 필수로 검증한다.
- actorId
- targetType
- targetId
- actionType

필수값이 없으면 IllegalArgumentException을 던진다.

4. previousLogHash/currentLogHash 생성
마지막 로그를 조회한다.

custodyLogRepository.findTopByOrderByLogIdDesc()

마지막 로그가 있으면:
- previousLogHash = 마지막 로그 currentLogHash

마지막 로그가 없으면:
- previousLogHash = null

currentLogHash는 아래 값을 조합해 SHA-256으로 생성한다.
- previousLogHash
- actorId
- targetType
- targetId
- actionType
- subjectHash
- storagePathAtEvent
- reason
- eventPayloadJson
- clientIp
- createdAt

currentLogHash는 64자 lowercase hex 문자열이어야 한다.

5. recordUserAction 리팩터링
가능하면 기존 recordUserAction(...) 내부도 새 record(...) 메서드를 사용하도록 정리한다.

단, 기존 동작은 유지한다.
- targetType = USER
- targetId = target.userId
- actorId = actor.userId
- actionType = 기존 인자
- reason = 기존 인자

6. 테스트 추가
테스트 파일을 추가한다.

권장 위치:
src/test/java/com/example/demo/service/CustodyLogServiceTest.java

테스트 항목:

첫 번째 로그 저장:
- previousLogHash가 null인지 확인
- currentLogHash가 64자 hex인지 확인
- actorId, targetType, targetId, actionType이 저장되는지 확인

두 번째 로그 저장:
- 두 번째 로그 previousLogHash가 첫 번째 로그 currentLogHash와 같은지 확인
- 두 번째 로그 currentLogHash가 첫 번째 로그 currentLogHash와 다른지 확인

subjectHash 검증:
- subjectHash가 저장되는지 확인
- subjectHash와 currentLogHash가 별도 값으로 관리되는지 확인

조회 검증:
- CustodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(...)로 저장한 로그를 조회할 수 있는지 확인

필수값 검증:
- actorId가 null이면 IllegalArgumentException
- targetType이 null이면 IllegalArgumentException
- targetId가 null이면 IllegalArgumentException
- actionType이 blank면 IllegalArgumentException

7. 이벤트명
테스트에는 ERD 기준 actionType을 사용한다.
예:
- EVIDENCE_UPLOADED
- HASH_CREATED

FILE_UPLOADED는 사용하지 않는다.

8. 기존 테스트 유지
구현 후 전체 테스트를 실행한다.

sh gradlew test

테스트가 실패하면 원인을 수정한다.

완료 기준:
- CustodyLogService에 범용 record 메서드가 있다.
- previousLogHash/currentLogHash 체인이 동작한다.
- currentLogHash는 64자 SHA-256 hex 문자열이다.
- subjectHash와 currentLogHash 의미가 분리되어 있다.
- targetType + targetId 기준 조회가 가능하다.
- 기존 recordUserAction 기능이 깨지지 않는다.
- sh gradlew test가 통과한다.
```

---

## 1단계 체크리스트

```text
[ ] ERD의 CustodyLogs 구조 확인
[ ] CustodyLogService 기존 recordUserAction 유지
[ ] 범용 record(...) 메서드 추가
[ ] actorId, targetType, targetId, actionType 필수 검증
[ ] 마지막 로그 currentLogHash 조회
[ ] previousLogHash 연결
[ ] currentLogHash SHA-256 생성
[ ] recordUserAction이 새 record 메서드를 재사용하도록 정리
[ ] CustodyLogServiceTest 추가
[ ] 첫 로그 previousLogHash=null 검증
[ ] 두 번째 로그 previousLogHash 체인 검증
[ ] subjectHash/currentLogHash 분리 검증
[ ] targetType + targetId 조회 검증
[ ] 필수값 누락 테스트
[ ] sh gradlew test 통과
```

---

## 1단계 이후 작업

1단계가 끝나면 다음 단계에서 실제 처리 흐름에 연결합니다.

```text
2단계: FileService.upload에 EVIDENCE_UPLOADED, HASH_CREATED, METADATA_EXTRACTED 연결
3단계: AnalysisService.startAnalysis에 ANALYSIS_REQUESTED 연결
4단계: ERROR_OCCURRED 및 RabbitMQ publish 결과 연결
```

---

# CoC 로그 구현 2단계 프롬프트

아래 프롬프트는 구현 AI에게 전달하기 위한 2단계 작업 지시서입니다.

2단계 목표는 **1단계에서 만든 `CustodyLogService.record(...)`를 실제 파일 업로드 성공 흐름에 연결**하는 것입니다.

이번 단계에서는 `FileService.upload(...)`에 ERD 기준 CoC 로그를 저장합니다.

---

## 구현 프롬프트

```text
너는 Spring Boot 백엔드 개발자다.
현재 backend-forensic 프로젝트에서 ERD 기준 CoC 로그 저장 기능의 2단계를 구현해야 한다.

반드시 아래 문서를 먼저 확인해라.
- docs/ERD_SPECIFICATION.md
- docs/COC_LOG_IMPLEMENTATION_GUIDE.md
- docs/COC_LOG_PHASE1_PROMPT.md

이번 2단계 범위:
1단계에서 구현된 CustodyLogService.record(...)를 사용해 FileService.upload(...) 성공 흐름에 CoC 로그를 연결한다.

이번 단계에서 저장할 CoC 이벤트:
- EVIDENCE_UPLOADED
- HASH_CREATED
- METADATA_EXTRACTED

절대 하지 말 것:
- AnalysisService.startAnalysis(...) 수정하지 말 것
- RabbitMQ 연동하지 말 것
- 분석 복사본 생성/삭제 로직 추가하지 말 것
- ANALYSIS_REQUESTED, ANALYSIS_STARTED, ANALYSIS_COMPLETED 이벤트 추가하지 말 것
- ERROR_OCCURRED 이벤트는 아직 연결하지 말 것
- CustodyLogs에 evidenceId, analysisRequestId 같은 개별 FK 추가하지 말 것
- ERD에 없는 새 컬럼 추가하지 말 것
- FILE_UPLOADED, ORIGINAL_HASH_CREATED 같은 비ERD 이벤트명 사용하지 말 것
- subjectHash와 currentLogHash를 혼동하지 말 것

ERD 기준:
CustodyLogs는 targetType + targetId 조합으로 대상 엔티티를 참조한다.

파일 업로드 흐름 기준:
1. 사용자가 증거 파일 업로드
2. 파일 검증
3. 로컬 임시 저장
4. SHA-256 해시 생성
5. 메타데이터 추출
6. S3 업로드
7. Evidences 저장
8. CustodyLogs에 아래 3개 이벤트 저장
   - EVIDENCE_UPLOADED
   - HASH_CREATED
   - METADATA_EXTRACTED

중요:
CoC 로그의 targetId는 Evidence 저장 후 생성된 evidenceId를 사용해야 한다.
따라서 CustodyLog 저장은 EvidenceRepository.save(...) 이후에 수행한다.

subjectHash 기준:
- EVIDENCE_UPLOADED: savedEvidence.originalHashValue
- HASH_CREATED: savedEvidence.originalHashValue
- METADATA_EXTRACTED: savedEvidence.originalHashValue

storagePathAtEvent 기준:
- savedEvidence.originalStoragePath

targetType / targetId 기준:
- targetType = EVIDENCE
- targetId = savedEvidence.evidenceId

actorId 기준:
- FileService.upload(...) 인자로 전달된 uploaderId

구현 작업:

1. FileService 의존성 추가
현재 파일:
src/main/java/com/example/demo/service/FileService.java

FileService에 CustodyLogService를 생성자 주입한다.
기존 생성자 주입 스타일을 유지한다.

2. Evidence 저장 이후 CoC 로그 저장
FileService.upload(...)에서 EvidenceRepository.save(...)로 Evidence를 저장한 후,
savedEvidence를 기준으로 CustodyLogService.record(...)를 3번 호출한다.

3. EVIDENCE_UPLOADED 로그 저장
저장값:
- actorId = uploaderId
- targetType = CustodyTargetType.EVIDENCE
- targetId = savedEvidence.getEvidenceId()
- actionType = "EVIDENCE_UPLOADED"
- subjectHash = savedEvidence.getOriginalHashValue()
- storagePathAtEvent = savedEvidence.getOriginalStoragePath()
- reason = "증거 파일 업로드 완료"
- eventPayloadJson = fileName, fileType, mimeType, fileSize, caseName 포함
- clientIp = null

eventPayloadJson 예시:
{
  "fileName": "...",
  "fileType": "IMAGE",
  "mimeType": "image/jpeg",
  "fileSize": 12345,
  "caseName": "..."
}

4. HASH_CREATED 로그 저장
저장값:
- actorId = uploaderId
- targetType = CustodyTargetType.EVIDENCE
- targetId = savedEvidence.getEvidenceId()
- actionType = "HASH_CREATED"
- subjectHash = savedEvidence.getOriginalHashValue()
- storagePathAtEvent = savedEvidence.getOriginalStoragePath()
- reason = "SHA-256 해시 생성 완료"
- eventPayloadJson = hashAlgorithm, hashValue 포함
- clientIp = null

eventPayloadJson 예시:
{
  "hashAlgorithm": "SHA-256",
  "hashValue": "..."
}

5. METADATA_EXTRACTED 로그 저장
저장값:
- actorId = uploaderId
- targetType = CustodyTargetType.EVIDENCE
- targetId = savedEvidence.getEvidenceId()
- actionType = "METADATA_EXTRACTED"
- subjectHash = savedEvidence.getOriginalHashValue()
- storagePathAtEvent = savedEvidence.getOriginalStoragePath()
- reason = "메타데이터 추출 완료"
- eventPayloadJson = extractionStatus 포함
- clientIp = null

metadata 추출 결과 기준:
- mediaService.extractMetadata(...)가 성공하면 extractionStatus = "SUCCESS"
- metadata 추출 예외가 발생해 metadata = "깨짐"으로 처리된 경우 extractionStatus = "FAILED"

eventPayloadJson 예시:
{
  "extractionStatus": "SUCCESS"
}

6. JSON 생성 방식
eventPayloadJson은 문자열 직접 이어붙이기보다 ObjectMapper 등 안전한 JSON 직렬화 방식을 우선 사용한다.

단, 새 컬럼이나 새 테이블은 만들지 않는다.

7. 트랜잭션 기준
FileService.upload(...)는 이미 @Transactional이다.
Evidence 저장과 CustodyLog 저장이 같은 트랜잭션 안에서 처리되도록 유지한다.

CoC 로그 저장 실패 시 업로드 트랜잭션이 롤백되는 것이 기본 동작이다.
이번 단계에서는 별도 보상 트랜잭션이나 비동기 저장을 구현하지 않는다.

8. 테스트 추가/수정
파일 업로드 성공 시 CustodyLogs에 3개 로그가 저장되는지 테스트한다.

권장 테스트 위치:
src/test/java/com/example/demo/controller/EvidenceControllerTest.java
또는
src/test/java/com/example/demo/service/FileServiceTest.java

테스트 항목:

업로드 성공 시 CoC 로그 3개 저장:
- targetType = EVIDENCE
- targetId = 업로드 응답의 evidenceId
- actionType 순서가 EVIDENCE_UPLOADED, HASH_CREATED, METADATA_EXTRACTED인지 확인
- subjectHash가 업로드 응답 hashValue와 같은지 확인
- storagePathAtEvent가 비어 있지 않은지 확인
- currentLogHash가 모두 64자 lowercase hex인지 확인
- 두 번째 로그 previousLogHash가 첫 번째 로그 currentLogHash와 같은지 확인
- 세 번째 로그 previousLogHash가 두 번째 로그 currentLogHash와 같은지 확인

HASH_CREATED 검증:
- HASH_CREATED 로그의 eventPayloadJson에 hashAlgorithm = SHA-256 포함
- HASH_CREATED 로그의 eventPayloadJson에 hashValue 포함

METADATA_EXTRACTED 검증:
- 정상 메타데이터 추출 또는 기존 테스트 환경의 동작에 맞춰 extractionStatus가 SUCCESS 또는 FAILED로 저장되는지 확인

이벤트명 검증:
- FILE_UPLOADED가 저장되지 않는지 확인
- ORIGINAL_HASH_CREATED가 저장되지 않는지 확인

기존 기능 유지:
- 기존 파일 업로드 응답 형식이 깨지면 안 된다.
- 기존 해시 생성 테스트가 깨지면 안 된다.
- 기존 Evidence 저장 로직이 깨지면 안 된다.

9. 테스트 실행
구현 후 전체 테스트를 실행한다.

sh gradlew test

테스트가 실패하면 원인을 수정한다.

완료 기준:
- FileService.upload(...) 성공 후 CustodyLogs에 EVIDENCE_UPLOADED 로그가 저장된다.
- FileService.upload(...) 성공 후 CustodyLogs에 HASH_CREATED 로그가 저장된다.
- FileService.upload(...) 성공 후 CustodyLogs에 METADATA_EXTRACTED 로그가 저장된다.
- 세 로그 모두 targetType = EVIDENCE, targetId = savedEvidence.evidenceId 기준이다.
- 세 로그 모두 subjectHash = savedEvidence.originalHashValue 기준이다.
- 세 로그의 previousLogHash/currentLogHash 체인이 순서대로 연결된다.
- FILE_UPLOADED, ORIGINAL_HASH_CREATED 같은 비ERD 이벤트명은 저장되지 않는다.
- AnalysisService, RabbitMQ, ERROR_OCCURRED는 아직 건드리지 않는다.
- sh gradlew test가 통과한다.
```

---

## 2단계 체크리스트

```text
[ ] docs/COC_LOG_IMPLEMENTATION_GUIDE.md 파일 업로드 흐름 기준 확인
[ ] FileService에 CustodyLogService 생성자 주입
[ ] Evidence 저장 후 savedEvidence 기준으로 CoC 로그 저장
[ ] EVIDENCE_UPLOADED 로그 저장
[ ] HASH_CREATED 로그 저장
[ ] METADATA_EXTRACTED 로그 저장
[ ] targetType = EVIDENCE 적용
[ ] targetId = savedEvidence.evidenceId 적용
[ ] subjectHash = savedEvidence.originalHashValue 적용
[ ] storagePathAtEvent = savedEvidence.originalStoragePath 적용
[ ] eventPayloadJson을 안전한 JSON 직렬화 방식으로 생성
[ ] metadata 추출 성공/실패에 따른 extractionStatus 저장
[ ] previousLogHash/currentLogHash 체인 검증 테스트
[ ] FILE_UPLOADED 미사용 검증
[ ] ORIGINAL_HASH_CREATED 미사용 검증
[ ] 기존 업로드 응답/저장 기능 유지
[ ] AnalysisService 미수정 확인
[ ] RabbitMQ 미연동 확인
[ ] sh gradlew test 통과
```

---

## 2단계 이후 작업

2단계가 끝나면 다음 단계에서 분석 요청 흐름에 연결합니다.

```text
3단계: AnalysisService.startAnalysis에 ANALYSIS_REQUESTED 연결
4단계: ERROR_OCCURRED 및 RabbitMQ publish 결과 연결
```

---

# CoC 로그 구현 3단계 프롬프트

아래 프롬프트는 구현 AI에게 전달하기 위한 3단계 작업 지시서입니다.

3단계 목표는 **1단계에서 만든 `CustodyLogService.record(...)`를 분석 요청 생성 성공 흐름에 연결**하는 것입니다.

이번 단계에서는 `AnalysisService.startAnalysis(...)`에서 `AnalysisRequest`가 새로 생성될 때 ERD 기준 `ANALYSIS_REQUESTED` CoC 로그를 저장합니다.

---

## 구현 프롬프트

```text
너는 Spring Boot 백엔드 개발자다.
현재 backend-forensic 프로젝트에서 ERD 기준 CoC 로그 저장 기능의 3단계를 구현해야 한다.

반드시 아래 문서를 먼저 확인해라.
- docs/ERD_SPECIFICATION.md
- docs/COC_LOG_IMPLEMENTATION_GUIDE.md
- docs/COC_LOG_PHASE1_PROMPT.md

이번 3단계 범위:
1단계에서 구현된 CustodyLogService.record(...)를 사용해 AnalysisService.startAnalysis(...)의 분석 요청 생성 성공 흐름에 CoC 로그를 연결한다.

이번 단계에서 저장할 CoC 이벤트:
- ANALYSIS_REQUESTED

절대 하지 말 것:
- FileService.upload(...) 수정하지 말 것
- RabbitMQ 연동하지 말 것
- 분석 복사본 생성/삭제 로직 추가하지 말 것
- ANALYSIS_COPY_CREATED, ANALYSIS_COPY_VERIFIED, ANALYSIS_STARTED, ANALYSIS_COMPLETED, ANALYSIS_FAILED, ANALYSIS_COPY_DELETED 이벤트 추가하지 말 것
- ERROR_OCCURRED 이벤트는 아직 연결하지 말 것
- AnalysisRequests.status 변경 정책을 바꾸지 말 것
- 기존 중복 분석 요청 skip 동작을 바꾸지 말 것
- CustodyLogs에 evidenceId, analysisRequestId 같은 개별 FK 추가하지 말 것
- ERD에 없는 새 컬럼 추가하지 말 것
- FILE_UPLOADED, ORIGINAL_HASH_CREATED 같은 비ERD 이벤트명 사용하지 말 것
- subjectHash와 currentLogHash를 혼동하지 말 것

ERD 기준:
CustodyLogs는 targetType + targetId 조합으로 대상 엔티티를 참조한다.

분석 요청 흐름 기준:
1. 사용자가 분석 요청 버튼 클릭
2. AnalysisService.startAnalysis(...) 실행
3. 요청한 evidenceIds 중 로그인 사용자의 Evidence만 조회
4. 기존 AnalysisRequest가 있는 Evidence는 현재 코드처럼 skip
5. 신규 AnalysisRequest 생성
6. AnalysisRequests.status = QUEUED 저장
7. CustodyLogs에 ANALYSIS_REQUESTED 저장

중요:
CoC 로그의 targetId는 AnalysisRequest 저장 후 생성된 analysisRequestId를 사용해야 한다.
따라서 CustodyLog 저장은 AnalysisRequestRepository.save(...) 이후에 수행한다.

targetType / targetId 기준:
- targetType = ANALYSIS_REQUEST
- targetId = savedRequest.analysisRequestId

actorId 기준:
- user.getUserId()

subjectHash 기준:
- evidence.originalHashValue

storagePathAtEvent 기준:
- evidence.originalStoragePath

actionType 기준:
- ANALYSIS_REQUESTED

구현 작업:

1. AnalysisService 의존성 추가
현재 파일:
src/main/java/com/example/demo/service/AnalysisService.java

AnalysisService에 CustodyLogService를 생성자 주입한다.
eventPayloadJson 생성을 위해 ObjectMapper를 생성자 주입해도 된다.
기존 @RequiredArgsConstructor 방식과 필드 final 주입 스타일을 유지한다.

2. AnalysisRequest 저장 이후 CoC 로그 저장
AnalysisService.startAnalysis(...)에서 AnalysisRequestRepository.save(...)로 요청을 저장한 후,
savedRequest와 evidence를 기준으로 CustodyLogService.record(...)를 호출한다.

현재 코드의 중복 요청 skip 조건은 유지한다.

현재 동작:
if (analysisRequestRepository.existsByEvidenceId(evidence.getEvidenceId())) {
    continue;
}

위 조건으로 skip된 Evidence에는 ANALYSIS_REQUESTED 로그를 새로 남기지 않는다.
새 AnalysisRequest row가 생성된 경우에만 ANALYSIS_REQUESTED 로그를 남긴다.

3. ANALYSIS_REQUESTED 로그 저장
저장값:
- actorId = user.getUserId()
- targetType = CustodyTargetType.ANALYSIS_REQUEST
- targetId = savedRequest.getAnalysisRequestId()
- actionType = "ANALYSIS_REQUESTED"
- subjectHash = evidence.getOriginalHashValue()
- storagePathAtEvent = evidence.getOriginalStoragePath()
- reason = "AI 분석 요청 생성 완료"
- eventPayloadJson = evidenceId, analysisRequestId, status, caseName 포함
- clientIp = null

eventPayloadJson 예시:
{
  "evidenceId": 1,
  "analysisRequestId": 10,
  "status": "QUEUED",
  "caseName": "2026-서울-0123 딥페이크 유포 사건"
}

4. JSON 생성 방식
eventPayloadJson은 문자열 직접 이어붙이기보다 ObjectMapper 등 안전한 JSON 직렬화 방식을 우선 사용한다.

단, 새 컬럼이나 새 테이블은 만들지 않는다.

5. 트랜잭션 기준
AnalysisService.startAnalysis(...)는 이미 @Transactional이다.
AnalysisRequest 저장과 CustodyLog 저장이 같은 트랜잭션 안에서 처리되도록 유지한다.

CoC 로그 저장 실패 시 분석 요청 생성 트랜잭션이 롤백되는 것이 기본 동작이다.
이번 단계에서는 별도 보상 트랜잭션이나 비동기 저장을 구현하지 않는다.

6. 테스트 추가/수정
분석 요청 성공 시 CustodyLogs에 ANALYSIS_REQUESTED 로그가 저장되는지 테스트한다.

권장 테스트 위치:
src/test/java/com/example/demo/controller/EvidenceControllerTest.java
또는
src/test/java/com/example/demo/service/AnalysisServiceTest.java

기존 EvidenceControllerTest에는 파일 업로드와 분석 요청 API 테스트가 있으므로, 가능하면 기존 흐름을 활용한다.

테스트 항목:

분석 요청 성공 시 CoC 로그 저장:
- 먼저 파일 업로드 API로 Evidence를 생성한다.
- 분석 요청 API /api/evidences/analyze 를 호출한다.
- AnalysisRequestRepository.findTopByEvidenceIdOrderByRequestedAtDesc(...)로 생성된 AnalysisRequest를 조회한다.
- CustodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(ANALYSIS_REQUEST, analysisRequestId)로 로그를 조회한다.
- 로그가 1개인지 확인한다.
- actionType = ANALYSIS_REQUESTED인지 확인한다.
- targetType = ANALYSIS_REQUEST인지 확인한다.
- targetId = analysisRequestId인지 확인한다.
- actorId = 요청 사용자 userId인지 확인한다.
- subjectHash = evidence.originalHashValue인지 확인한다.
- storagePathAtEvent = evidence.originalStoragePath인지 확인한다.
- currentLogHash가 64자 lowercase hex인지 확인한다.
- previousLogHash가 직전 전체 CustodyLog.currentLogHash와 연결되는지 확인한다.

eventPayloadJson 검증:
- evidenceId 포함
- analysisRequestId 포함
- status = QUEUED 포함
- caseName 포함

중복 요청 skip 검증:
- 같은 Evidence에 대해 분석 요청을 한 번 더 호출했을 때 기존 로직처럼 새 AnalysisRequest를 만들지 않는지 확인한다.
- skip된 Evidence에 대해 ANALYSIS_REQUESTED 로그가 추가로 생기지 않는지 확인한다.

이벤트명 검증:
- ANALYSIS_STARTED가 저장되지 않는지 확인
- ANALYSIS_COMPLETED가 저장되지 않는지 확인
- ANALYSIS_FAILED가 저장되지 않는지 확인
- ERROR_OCCURRED가 저장되지 않는지 확인

기존 기능 유지:
- 기존 분석 요청 응답 형식이 깨지면 안 된다.
- startedCount 계산이 깨지면 안 된다.
- 기존 미디어별 분석 건수 테스트가 깨지면 안 된다.
- FileService 업로드 CoC 로그 3개 저장 기능이 깨지면 안 된다.

7. 테스트 실행
구현 후 전체 테스트를 실행한다.

sh gradlew test

테스트가 실패하면 원인을 수정한다.

완료 기준:
- AnalysisService.startAnalysis(...)에서 새 AnalysisRequest 생성 후 ANALYSIS_REQUESTED 로그가 저장된다.
- 로그의 targetType = ANALYSIS_REQUEST, targetId = savedRequest.analysisRequestId 기준이다.
- 로그의 subjectHash = evidence.originalHashValue 기준이다.
- 로그의 storagePathAtEvent = evidence.originalStoragePath 기준이다.
- eventPayloadJson에 evidenceId, analysisRequestId, status=QUEUED, caseName이 포함된다.
- 기존 중복 요청 skip 동작은 유지된다.
- skip된 Evidence에는 새 ANALYSIS_REQUESTED 로그가 추가되지 않는다.
- RabbitMQ, 분석 복사본, ERROR_OCCURRED는 아직 건드리지 않는다.
- sh gradlew test가 통과한다.
```

---

## 3단계 체크리스트

```text
[ ] docs/COC_LOG_IMPLEMENTATION_GUIDE.md AI 분석 흐름 기준 확인
[ ] AnalysisService에 CustodyLogService 생성자 주입
[ ] eventPayloadJson 생성을 위한 ObjectMapper 사용
[ ] AnalysisRequest 저장 후 savedRequest 기준으로 CoC 로그 저장
[ ] ANALYSIS_REQUESTED 로그 저장
[ ] targetType = ANALYSIS_REQUEST 적용
[ ] targetId = savedRequest.analysisRequestId 적용
[ ] actorId = user.userId 적용
[ ] subjectHash = evidence.originalHashValue 적용
[ ] storagePathAtEvent = evidence.originalStoragePath 적용
[ ] eventPayloadJson에 evidenceId 포함
[ ] eventPayloadJson에 analysisRequestId 포함
[ ] eventPayloadJson에 status = QUEUED 포함
[ ] eventPayloadJson에 caseName 포함
[ ] previousLogHash/currentLogHash 체인 검증 테스트
[ ] 중복 분석 요청 skip 시 추가 로그 미생성 검증
[ ] ANALYSIS_STARTED 미사용 검증
[ ] ANALYSIS_COMPLETED 미사용 검증
[ ] ANALYSIS_FAILED 미사용 검증
[ ] ERROR_OCCURRED 미사용 검증
[ ] FileService 미수정 확인
[ ] RabbitMQ 미연동 확인
[ ] sh gradlew test 통과
```

---

## 3단계 이후 작업

3단계가 끝나면 다음 단계에서 실패 로그와 메시지 큐 연동 결과를 연결합니다.

```text
4단계: ERROR_OCCURRED 및 RabbitMQ publish 결과 연결
```

---

# CoC 로그 구현 4단계 프롬프트

아래 프롬프트는 구현 AI에게 전달하기 위한 4단계 작업 지시서입니다.

4단계 목표는 **분석 요청 생성 이후 RabbitMQ 큐 등록 결과를 CoC 로그에 반영하고, 실패 상황을 ERD 기준 `ERROR_OCCURRED` 로그로 저장**하는 것입니다.

이번 단계에서는 분석 요청을 실제 AI 처리 큐에 등록하는 경계까지만 다룹니다. AI 분석 시작, 완료, 실패 처리, 분석 복사본 생성/검증/삭제는 아직 구현하지 않습니다.

---

## 구현 프롬프트

```text
너는 Spring Boot 백엔드 개발자다.
현재 backend-forensic 프로젝트에서 ERD 기준 CoC 로그 저장 기능의 4단계를 구현해야 한다.

반드시 아래 문서를 먼저 확인해라.
- docs/ERD_SPECIFICATION.md
- docs/COC_LOG_IMPLEMENTATION_GUIDE.md
- docs/COC_LOG_PHASE1_PROMPT.md

이번 4단계 범위:
3단계에서 구현된 AnalysisService.startAnalysis(...)의 ANALYSIS_REQUESTED 저장 흐름에 RabbitMQ publish 결과를 반영한다.
RabbitMQ publish 실패 시 AnalysisRequest.status를 FAILED로 변경하고 ERROR_OCCURRED CoC 로그를 저장한다.

이번 단계에서 다룰 CoC 이벤트:
- ANALYSIS_REQUESTED
- ERROR_OCCURRED

절대 하지 말 것:
- FileService.upload(...)의 성공 CoC 로그 3개 저장 흐름을 바꾸지 말 것
- AI 분석 복사본 생성/검증/삭제 로직 추가하지 말 것
- ANALYSIS_COPY_CREATED, ANALYSIS_COPY_VERIFIED, ANALYSIS_COPY_DELETED 이벤트 추가하지 말 것
- ANALYSIS_STARTED, ANALYSIS_COMPLETED 이벤트 추가하지 말 것
- AI 서버 분석 결과 저장 로직 추가하지 말 것
- AnalysisResults, AnalysisModuleResults, Reports 저장 로직 추가하지 말 것
- CustodyLogs에 evidenceId, analysisRequestId 같은 개별 FK 추가하지 말 것
- ERD에 없는 새 컬럼 추가하지 말 것
- QUEUE_REGISTERED 같은 ERD에 없는 actionType을 추가하지 말 것
- FILE_UPLOADED, ORIGINAL_HASH_CREATED 같은 비ERD 이벤트명 사용하지 말 것
- subjectHash와 currentLogHash를 혼동하지 말 것
- password, token, secret, 민감한 원문 값을 eventPayloadJson이나 reason에 저장하지 말 것

ERD 기준:
CustodyLogs는 targetType + targetId 조합으로 대상 엔티티를 참조한다.

RabbitMQ publish 성공 기준:
- AnalysisRequest row가 생성된다.
- RabbitMQ publish가 성공한다.
- AnalysisRequests.status는 QUEUED를 유지한다.
- ANALYSIS_REQUESTED 로그를 저장한다.
- ANALYSIS_REQUESTED eventPayloadJson에 queueRegistered = true, queueName을 포함한다.

RabbitMQ publish 실패 기준:
- AnalysisRequest row는 생성된다.
- RabbitMQ publish가 실패한다.
- AnalysisRequests.status = FAILED 로 변경한다.
- AnalysisRequests.errorCode, errorMessage를 저장할 수 있으면 저장한다.
- ERROR_OCCURRED 로그를 저장한다.
- 실패한 요청은 기존 row를 수정해 FAILED로 남긴다.
- 추후 재시도는 기존 ERD 정책대로 새 AnalysisRequest row를 생성하는 별도 흐름으로 둔다.

중요:
현재 코드에 RabbitMQ publisher가 이미 있으면 기존 구조를 우선 사용한다.
현재 코드에 RabbitMQ publisher가 없으면 최소한의 경계만 추가한다.

권장 구조:
- AnalysisQueuePublisher 인터페이스 추가
- RabbitMqAnalysisQueuePublisher 구현체 추가
- AnalysisQueueMessage DTO 또는 record 추가

단, 과도한 큐 설정/컨슈머/AI 처리 로직은 만들지 않는다.
이번 단계는 publish 요청과 그 성공/실패 결과 기록까지만 구현한다.

구현 작업:

1. 현재 RabbitMQ 관련 코드 확인
아래 키워드로 기존 구현이 있는지 먼저 확인한다.
- RabbitTemplate
- RabbitMQ
- AmqpTemplate
- Queue
- Exchange
- RoutingKey

기존 publisher가 있으면 그것을 사용한다.
없으면 최소 publisher 인터페이스와 구현체를 추가한다.

2. 큐 publish 경계 추가
권장 인터페이스:

public interface AnalysisQueuePublisher {
    void publish(AnalysisQueueMessage message);
}

권장 메시지 필드:
- analysisRequestId
- evidenceId
- requestedBy
- caseName
- subjectHash
- storagePath

RabbitMQ 구현체는 RabbitTemplate을 사용한다.
큐 이름, exchange, routingKey는 application 설정에서 읽도록 한다.

권장 설정 키:
- forenshield.rabbitmq.analysis-exchange
- forenshield.rabbitmq.analysis-routing-key
- forenshield.rabbitmq.analysis-queue

테스트 환경에서는 publisher를 MockBean으로 대체할 수 있어야 한다.

3. AnalysisService.startAnalysis(...) 흐름 조정
현재 3단계 구현은 AnalysisRequest 저장 직후 ANALYSIS_REQUESTED 로그를 저장한다.
4단계에서는 RabbitMQ publish 결과를 ANALYSIS_REQUESTED payload에 반영해야 하므로 순서를 아래처럼 조정한다.

신규 AnalysisRequest 생성 흐름:
1. AnalysisRequest 생성
2. status = QUEUED
3. AnalysisRequest 저장
4. RabbitMQ publish 시도
5. publish 성공 시:
   - ANALYSIS_REQUESTED 로그 저장
   - eventPayloadJson에 queueRegistered = true, queueName 포함
   - startedEvidenceIds에 evidenceId 추가
6. publish 실패 시:
   - savedRequest.status = FAILED
   - savedRequest.errorCode = "RABBITMQ_PUBLISH_FAILED"
   - savedRequest.errorMessage = 사용자/운영자가 이해 가능한 요약 메시지
   - ERROR_OCCURRED 로그 저장
   - 해당 evidenceId는 startedEvidenceIds에 추가하지 않는다.

현재 중복 요청 skip 조건은 유지한다.

현재 동작:
if (analysisRequestRepository.existsByEvidenceId(evidence.getEvidenceId())) {
    continue;
}

위 조건으로 skip된 Evidence에는 ANALYSIS_REQUESTED 또는 ERROR_OCCURRED 로그를 새로 남기지 않는다.

4. ANALYSIS_REQUESTED 로그 저장
publish 성공 시 저장값:
- actorId = user.getUserId()
- targetType = CustodyTargetType.ANALYSIS_REQUEST
- targetId = savedRequest.getAnalysisRequestId()
- actionType = "ANALYSIS_REQUESTED"
- subjectHash = evidence.getOriginalHashValue()
- storagePathAtEvent = evidence.getOriginalStoragePath()
- reason = "AI 분석 요청 생성 및 큐 등록 완료"
- eventPayloadJson = evidenceId, analysisRequestId, status, caseName, queueRegistered, queueName 포함
- clientIp = null

eventPayloadJson 예시:
{
  "evidenceId": 1,
  "analysisRequestId": 10,
  "status": "QUEUED",
  "caseName": "2026-서울-0123 딥페이크 유포 사건",
  "queueRegistered": true,
  "queueName": "forenshield.analysis.requests"
}

5. ERROR_OCCURRED 로그 저장
publish 실패 시 저장값:
- actorId = user.getUserId()
- targetType = CustodyTargetType.ANALYSIS_REQUEST
- targetId = savedRequest.getAnalysisRequestId()
- actionType = "ERROR_OCCURRED"
- subjectHash = evidence.getOriginalHashValue()
- storagePathAtEvent = evidence.getOriginalStoragePath()
- reason = "분석 요청 큐 등록 실패"
- eventPayloadJson = step, errorCode, message, evidenceId, analysisRequestId, queueName 포함
- clientIp = null

eventPayloadJson 예시:
{
  "step": "RABBITMQ_PUBLISH",
  "errorCode": "RABBITMQ_PUBLISH_FAILED",
  "message": "분석 요청 큐 등록에 실패했습니다.",
  "evidenceId": 1,
  "analysisRequestId": 10,
  "queueName": "forenshield.analysis.requests"
}

주의:
- exception stack trace 전체를 eventPayloadJson에 저장하지 말 것
- AWS secret, RabbitMQ password, token 등 민감정보를 저장하지 말 것
- 외부 시스템 상세 오류는 운영 로그에는 남길 수 있지만 CustodyLogs에는 요약만 저장한다.

6. JSON 생성 방식
eventPayloadJson은 문자열 직접 이어붙이기보다 ObjectMapper 등 안전한 JSON 직렬화 방식을 사용한다.

단, 새 컬럼이나 새 테이블은 만들지 않는다.

7. 트랜잭션 기준
AnalysisService.startAnalysis(...)는 @Transactional이다.

publish 성공:
- AnalysisRequest 저장
- ANALYSIS_REQUESTED 로그 저장
- 같은 트랜잭션으로 커밋

publish 실패:
- AnalysisRequest는 FAILED 상태로 남긴다.
- ERROR_OCCURRED 로그도 같은 트랜잭션으로 저장한다.
- publish 실패를 컨트롤러까지 예외로 던져 전체 트랜잭션을 롤백하지 않는다.
- 응답은 기존 StartAnalysisResponse 형식을 유지한다.

startedCount 기준:
- publish 성공한 요청만 startedCount에 포함한다.
- publish 실패한 요청은 startedCount에 포함하지 않는다.

8. 테스트 추가/수정
분석 요청 큐 등록 성공/실패에 따른 CoC 로그를 테스트한다.

권장 테스트 위치:
src/test/java/com/example/demo/controller/EvidenceControllerTest.java
또는
src/test/java/com/example/demo/service/AnalysisServiceTest.java

테스트 환경에서는 AnalysisQueuePublisher를 MockBean으로 대체한다.

테스트 항목:

RabbitMQ publish 성공:
- 파일 업로드 API로 Evidence 생성
- AnalysisQueuePublisher.publish(...)가 성공하도록 mock 설정
- 분석 요청 API /api/evidences/analyze 호출
- AnalysisRequest.status = QUEUED 확인
- ANALYSIS_REQUESTED 로그 1개 확인
- actionType = ANALYSIS_REQUESTED
- targetType = ANALYSIS_REQUEST
- targetId = analysisRequestId
- subjectHash = evidence.originalHashValue
- storagePathAtEvent = evidence.originalStoragePath
- eventPayloadJson.queueRegistered = true 확인
- eventPayloadJson.queueName 확인
- currentLogHash가 64자 lowercase hex인지 확인
- ERROR_OCCURRED 로그가 저장되지 않았는지 확인

RabbitMQ publish 실패:
- 파일 업로드 API로 Evidence 생성
- AnalysisQueuePublisher.publish(...)가 RuntimeException을 던지도록 mock 설정
- 분석 요청 API /api/evidences/analyze 호출
- API 응답 형식은 기존 StartAnalysisResponse를 유지
- startedCount = 0 확인
- AnalysisRequest row가 생성됐는지 확인
- AnalysisRequest.status = FAILED 확인
- AnalysisRequest.errorCode = RABBITMQ_PUBLISH_FAILED 확인
- ERROR_OCCURRED 로그 1개 확인
- actionType = ERROR_OCCURRED
- targetType = ANALYSIS_REQUEST
- targetId = analysisRequestId
- subjectHash = evidence.originalHashValue
- storagePathAtEvent = evidence.originalStoragePath
- eventPayloadJson.step = RABBITMQ_PUBLISH 확인
- eventPayloadJson.errorCode = RABBITMQ_PUBLISH_FAILED 확인
- eventPayloadJson에 password, token, secret 같은 민감정보가 없는지 확인
- ANALYSIS_REQUESTED 로그가 실패 요청에 저장되지 않았는지 확인

중복 요청 skip 유지:
- 이미 AnalysisRequest가 있는 Evidence는 기존처럼 skip한다.
- skip된 Evidence에는 새 ANALYSIS_REQUESTED 또는 ERROR_OCCURRED 로그가 추가되지 않는다.

이벤트명 검증:
- QUEUE_REGISTERED 같은 비ERD actionType이 저장되지 않는지 확인
- ANALYSIS_STARTED가 저장되지 않는지 확인
- ANALYSIS_COMPLETED가 저장되지 않는지 확인
- ANALYSIS_COPY_CREATED가 저장되지 않는지 확인

기존 기능 유지:
- 파일 업로드 CoC 로그 3개 저장 기능이 깨지면 안 된다.
- 분석 요청 성공 응답 형식이 깨지면 안 된다.
- 기존 미디어별 분석 건수 테스트가 깨지면 안 된다.
- 기존 CustodyLogService 해시 체인 테스트가 깨지면 안 된다.

9. 테스트 실행
구현 후 전체 테스트를 실행한다.

sh gradlew test

테스트가 실패하면 원인을 수정한다.

완료 기준:
- RabbitMQ publish 성공 시 ANALYSIS_REQUESTED 로그에 queueRegistered=true가 저장된다.
- RabbitMQ publish 실패 시 AnalysisRequest.status=FAILED가 저장된다.
- RabbitMQ publish 실패 시 ERROR_OCCURRED 로그가 저장된다.
- 실패 요청에는 ANALYSIS_REQUESTED 성공 로그가 저장되지 않는다.
- startedCount는 publish 성공 요청만 집계한다.
- ERD에 없는 actionType을 저장하지 않는다.
- CustodyLogs에 ERD 외 컬럼/FK를 추가하지 않는다.
- AI 분석 시작/완료/복사본/결과 저장은 아직 구현하지 않는다.
- sh gradlew test가 통과한다.
```

---

## 4단계 체크리스트

```text
[ ] docs/COC_LOG_IMPLEMENTATION_GUIDE.md ERROR_OCCURRED/RabbitMQ 기준 확인
[ ] 기존 RabbitMQ publisher 존재 여부 확인
[ ] 없으면 AnalysisQueuePublisher 인터페이스 추가
[ ] 없으면 RabbitMqAnalysisQueuePublisher 구현체 추가
[ ] AnalysisQueueMessage 추가
[ ] RabbitMQ exchange/routingKey/queue 설정값 정리
[ ] AnalysisService에서 publish 성공 후 ANALYSIS_REQUESTED 로그 저장
[ ] ANALYSIS_REQUESTED payload에 queueRegistered=true 포함
[ ] ANALYSIS_REQUESTED payload에 queueName 포함
[ ] publish 실패 시 AnalysisRequest.status=FAILED 저장
[ ] publish 실패 시 errorCode=RABBITMQ_PUBLISH_FAILED 저장
[ ] publish 실패 시 ERROR_OCCURRED 로그 저장
[ ] ERROR_OCCURRED payload에 step=RABBITMQ_PUBLISH 포함
[ ] ERROR_OCCURRED payload에 errorCode 포함
[ ] ERROR_OCCURRED payload에 민감정보 미포함
[ ] startedCount는 publish 성공 건만 반영
[ ] 중복 요청 skip 동작 유지
[ ] QUEUE_REGISTERED 같은 비ERD actionType 미사용 검증
[ ] ANALYSIS_STARTED 미사용 검증
[ ] ANALYSIS_COMPLETED 미사용 검증
[ ] ANALYSIS_COPY_CREATED 미사용 검증
[ ] FileService 업로드 CoC 로그 유지
[ ] sh gradlew test 통과
```

---

## 4단계 이후 작업

4단계가 끝나면 다음 단계에서 실제 AI 분석 처리 생명주기를 연결합니다.

```text
5단계: 분석 복사본 생성/검증/삭제 CoC 연결
6단계: ANALYSIS_STARTED, ANALYSIS_COMPLETED, ANALYSIS_FAILED 연결
7단계: REPORT_CREATED, REPORT_DOWNLOADED 연결
```

---

# CoC 로그 구현 5단계 프롬프트

아래 프롬프트는 구현 AI에게 전달하기 위한 5단계 작업 지시서입니다.

5단계 목표는 **분석 요청 시 원본 증거 파일의 분석용 복사본을 만들고, 복사본 생성/검증/삭제 과정을 ERD 기준 CoC 로그로 남기는 것**입니다.

이번 단계에서는 분석 복사본 생명주기까지만 다룹니다. AI 분석 시작, 완료, 실패 처리와 분석 결과 저장은 아직 구현하지 않습니다.

---

## 구현 프롬프트

```text
너는 Spring Boot 백엔드 개발자다.
현재 backend-forensic 프로젝트에서 ERD 기준 CoC 로그 저장 기능의 5단계를 구현해야 한다.

반드시 아래 문서를 먼저 확인해라.
- docs/ERD_SPECIFICATION.md
- docs/COC_LOG_IMPLEMENTATION_GUIDE.md
- docs/COC_LOG_PHASE1_PROMPT.md

이번 5단계 범위:
AnalysisService.startAnalysis(...)에서 분석 요청을 큐에 등록하기 전에 분석용 복사본을 생성한다.
복사본 생성 후 해시를 계산하고 원본 해시와 검증한다.
복사본 생성/검증/삭제 상태를 Evidences.copy* 필드에 기록한다.
복사본 관련 CoC 로그를 ERD 이벤트명으로 저장한다.

이번 단계에서 저장할 CoC 이벤트:
- ANALYSIS_COPY_CREATED
- ANALYSIS_COPY_VERIFIED
- ANALYSIS_COPY_DELETED
- ERROR_OCCURRED

기존 단계 이벤트 유지:
- EVIDENCE_UPLOADED
- HASH_CREATED
- METADATA_EXTRACTED
- ANALYSIS_REQUESTED

절대 하지 말 것:
- FileService.upload(...)의 성공 CoC 로그 3개 저장 흐름을 바꾸지 말 것
- RabbitMQ publish 성공/실패 CoC 흐름을 깨지 말 것
- AI 서버 분석 시작 로직 추가하지 말 것
- ANALYSIS_STARTED 이벤트 추가하지 말 것
- ANALYSIS_COMPLETED 이벤트 추가하지 말 것
- ANALYSIS_FAILED 이벤트 추가하지 말 것
- AnalysisResults, AnalysisModuleResults, Reports 저장 로직 추가하지 말 것
- REPORT_CREATED, REPORT_DOWNLOADED 이벤트 추가하지 말 것
- CustodyLogs에 evidenceId, analysisRequestId 같은 개별 FK 추가하지 말 것
- ERD에 없는 새 컬럼 추가하지 말 것
- COPY_CREATED, COPY_VERIFIED, COPY_DELETED 같은 비ERD 이벤트명 사용하지 말 것
- QUEUE_REGISTERED 같은 ERD에 없는 actionType 사용하지 말 것
- subjectHash와 currentLogHash를 혼동하지 말 것
- password, token, secret, S3 credential 등 민감정보를 eventPayloadJson이나 reason에 저장하지 말 것

ERD 기준:
CustodyLogs는 targetType + targetId 조합으로 대상 엔티티를 참조한다.

Evidences 복사본 필드:
- copyHashValue
- copyStoragePath
- copyStatus
- copyCreatedAt
- copyDeletedAt

CopyStatus 기준:
- NONE
- ACTIVE
- DELETED

복사본 정책:
- 분석 요청 시 원본 파일에서 분석용 복사본을 생성한다.
- 복사본 생성 후 copyStatus = ACTIVE, copyCreatedAt = now 로 저장한다.
- 복사본 SHA-256을 계산해 copyHashValue에 저장한다.
- byte copy라면 originalHashValue와 copyHashValue가 같아야 한다.
- 분석 완료 후 삭제가 원칙이지만, 이번 단계에서는 삭제 기능을 별도 메서드로 구현하고 테스트에서 호출해 검증한다.
- 삭제 후 copyStatus = DELETED, copyDeletedAt = now 로 저장한다.
- copyHashValue는 삭제 후에도 DB에 유지한다.

중요:
현재 Evidence 엔티티에 copy* 필드 갱신 메서드가 없으면 도메인 메서드를 추가한다.
권장 메서드:
- markCopyActive(String copyHashValue, String copyStoragePath, LocalDateTime copyCreatedAt)
- markCopyDeleted(LocalDateTime copyDeletedAt)

무분별한 public setter를 추가하지 말고, 의미 있는 도메인 메서드로 상태를 변경한다.

구현 작업:

1. 현재 파일 저장 구조 확인
아래 파일을 먼저 확인한다.
- src/main/java/com/example/demo/service/FileService.java
- src/main/java/com/example/demo/service/HashService.java
- src/main/java/com/example/demo/service/AnalysisService.java
- src/main/java/com/example/demo/domain/Evidence.java

현재 FileService가 원본 파일을 S3에 저장하고 로컬 임시 파일을 삭제한다.
따라서 복사본 생성 방식은 현재 저장 구조에 맞춰 선택한다.

권장 선택지:
- S3 원본 객체를 분석용 S3 key로 copyObject 한다.
- 또는 로컬 테스트에서는 mock 가능한 AnalysisCopyService 경계를 둔다.

테스트 가능성을 위해 복사본 생성/검증/삭제는 별도 서비스로 분리한다.

권장 서비스:
- AnalysisCopyService

권장 메서드:
public AnalysisCopyResult createAndVerifyCopy(Evidence evidence, Long analysisRequestId)
public void deleteCopy(Evidence evidence, Long analysisRequestId)

권장 결과 타입:
public record AnalysisCopyResult(
    String copyHashValue,
    String copyStoragePath,
    boolean verified
)

2. AnalysisCopyService 추가
AnalysisCopyService는 아래 책임을 가진다.
- 원본 저장 경로 확인
- 분석용 복사본 생성
- 복사본 SHA-256 계산
- 원본 해시와 복사본 해시 비교
- Evidence.copyHashValue, copyStoragePath, copyStatus, copyCreatedAt 갱신
- 필요 시 복사본 삭제 및 Evidence.copyStatus, copyDeletedAt 갱신

구현 방식:
- S3Client를 사용할 수 있으면 copyObject/deleteObject 기반으로 구현한다.
- 로컬 테스트에서는 S3Client를 MockBean으로 대체할 수 있어야 한다.
- 복사본 key는 예측 가능한 형식을 사용한다.

권장 copyStoragePath:
analysis-copies/{analysisRequestId}/{originalFileNameOrEvidenceId}

주의:
- 이번 단계에서 분석 서버로 복사본을 실제 전달하지 않는다.
- 이번 단계에서 컨슈머를 만들지 않는다.
- 이번 단계에서 분석 결과를 저장하지 않는다.

3. AnalysisService.startAnalysis(...) 흐름 조정
4단계 흐름에서 RabbitMQ publish 전에 복사본 생성/검증을 수행한다.

신규 AnalysisRequest 생성 흐름:
1. AnalysisRequest 생성
2. status = QUEUED
3. AnalysisRequest 저장
4. 분석용 복사본 생성
5. copyStatus = ACTIVE 저장
6. copyHashValue, copyStoragePath, copyCreatedAt 저장
7. ANALYSIS_COPY_CREATED 로그 저장
8. 원본/복사본 해시 검증
9. ANALYSIS_COPY_VERIFIED 로그 저장
10. RabbitMQ publish 시도
11. publish 성공 시 ANALYSIS_REQUESTED 로그 저장
12. publish 실패 시 AnalysisRequest.status = FAILED, ERROR_OCCURRED 로그 저장

중요:
- RabbitMQ publish는 복사본 생성/검증 성공 후에만 시도한다.
- 복사본 생성/검증 실패 시 RabbitMQ publish를 시도하지 않는다.
- 복사본 생성/검증 실패 시 AnalysisRequest.status = FAILED 로 저장한다.
- 복사본 생성/검증 실패 시 ERROR_OCCURRED 로그를 저장한다.
- startedCount는 RabbitMQ publish까지 성공한 요청만 포함한다.

4. ANALYSIS_COPY_CREATED 로그 저장
복사본 생성 성공 시 저장값:
- actorId = user.getUserId()
- targetType = CustodyTargetType.EVIDENCE
- targetId = evidence.getEvidenceId()
- actionType = "ANALYSIS_COPY_CREATED"
- subjectHash = evidence.getCopyHashValue()
- storagePathAtEvent = evidence.getCopyStoragePath()
- reason = "분석용 복사본 생성 완료"
- eventPayloadJson = evidenceId, analysisRequestId, copyStoragePath, copyHashValue, copyStatus 포함
- clientIp = null

eventPayloadJson 예시:
{
  "evidenceId": 1,
  "analysisRequestId": 10,
  "copyStoragePath": "analysis-copies/10/1",
  "copyHashValue": "...",
  "copyStatus": "ACTIVE"
}

5. ANALYSIS_COPY_VERIFIED 로그 저장
복사본 해시 검증 성공 시 저장값:
- actorId = user.getUserId()
- targetType = CustodyTargetType.EVIDENCE
- targetId = evidence.getEvidenceId()
- actionType = "ANALYSIS_COPY_VERIFIED"
- subjectHash = evidence.getCopyHashValue()
- storagePathAtEvent = evidence.getCopyStoragePath()
- reason = "분석용 복사본 해시 검증 완료"
- eventPayloadJson = evidenceId, analysisRequestId, originalHashValue, copyHashValue, verified 포함
- clientIp = null

eventPayloadJson 예시:
{
  "evidenceId": 1,
  "analysisRequestId": 10,
  "originalHashValue": "...",
  "copyHashValue": "...",
  "verified": true
}

6. ANALYSIS_COPY_DELETED 로그 저장
복사본 삭제 성공 시 저장값:
- actorId = 요청 사용자 ID 또는 시스템 사용자 ID
- targetType = CustodyTargetType.EVIDENCE
- targetId = evidence.getEvidenceId()
- actionType = "ANALYSIS_COPY_DELETED"
- subjectHash = evidence.getCopyHashValue()
- storagePathAtEvent = 삭제된 copyStoragePath
- reason = "분석용 복사본 삭제 완료"
- eventPayloadJson = evidenceId, analysisRequestId, copyStoragePath, copyHashValue, copyStatus 포함
- clientIp = null

eventPayloadJson 예시:
{
  "evidenceId": 1,
  "analysisRequestId": 10,
  "copyStoragePath": "analysis-copies/10/1",
  "copyHashValue": "...",
  "copyStatus": "DELETED"
}

주의:
- 삭제 후에도 subjectHash는 삭제된 복사본의 copyHashValue를 유지한다.
- 삭제 후에도 eventPayloadJson에 삭제된 copyStoragePath를 남긴다.

7. ERROR_OCCURRED 로그 저장
복사본 생성/검증/삭제 실패 시 저장값:
- actorId = user.getUserId() 또는 시스템 사용자 ID
- targetType = CustodyTargetType.EVIDENCE 또는 ANALYSIS_REQUEST
- targetId = 관련 evidenceId 또는 analysisRequestId
- actionType = "ERROR_OCCURRED"
- subjectHash = 가능한 경우 originalHashValue 또는 copyHashValue
- storagePathAtEvent = 가능한 경우 originalStoragePath 또는 copyStoragePath
- reason = 실패 요약
- eventPayloadJson = step, errorCode, message, evidenceId, analysisRequestId 포함
- clientIp = null

권장 step:
- ANALYSIS_COPY_CREATED
- ANALYSIS_COPY_VERIFIED
- ANALYSIS_COPY_DELETED

권장 errorCode:
- ANALYSIS_COPY_CREATE_FAILED
- ANALYSIS_COPY_VERIFY_FAILED
- ANALYSIS_COPY_DELETE_FAILED

주의:
- exception stack trace 전체를 eventPayloadJson에 저장하지 말 것
- S3 key는 저장해도 되지만 credential, token, secret은 저장하지 말 것

8. JSON 생성 방식
eventPayloadJson은 문자열 직접 이어붙이기보다 ObjectMapper 등 안전한 JSON 직렬화 방식을 사용한다.

단, 새 컬럼이나 새 테이블은 만들지 않는다.

9. 트랜잭션 기준
AnalysisService.startAnalysis(...)는 @Transactional이다.

복사본 생성/검증 성공:
- Evidence.copy* 필드 저장
- ANALYSIS_COPY_CREATED 저장
- ANALYSIS_COPY_VERIFIED 저장
- RabbitMQ publish 성공 시 ANALYSIS_REQUESTED 저장
- 같은 트랜잭션으로 커밋

복사본 생성/검증 실패:
- AnalysisRequest.status = FAILED 저장
- ERROR_OCCURRED 저장
- RabbitMQ publish는 시도하지 않음
- startedCount에 포함하지 않음

복사본 삭제:
- 삭제 메서드는 별도 트랜잭션 메서드로 구현해도 된다.
- 삭제 성공 시 Evidence.copyStatus = DELETED, copyDeletedAt 저장
- ANALYSIS_COPY_DELETED 로그 저장

10. 테스트 추가/수정
분석 복사본 생성/검증/삭제 CoC 로그를 테스트한다.

권장 테스트 위치:
src/test/java/com/example/demo/controller/EvidenceControllerTest.java
또는
src/test/java/com/example/demo/service/AnalysisCopyServiceTest.java
또는
src/test/java/com/example/demo/service/AnalysisServiceTest.java

테스트 항목:

복사본 생성/검증 성공:
- 파일 업로드 API로 Evidence 생성
- AnalysisQueuePublisher.publish(...)는 성공하도록 mock 설정
- 분석 요청 API /api/evidences/analyze 호출
- Evidence.copyStatus = ACTIVE 확인
- Evidence.copyHashValue가 64자 lowercase hex인지 확인
- Evidence.copyStoragePath가 비어 있지 않은지 확인
- Evidence.copyCreatedAt이 null이 아닌지 확인
- ANALYSIS_COPY_CREATED 로그 1개 확인
- ANALYSIS_COPY_VERIFIED 로그 1개 확인
- 두 로그 모두 targetType = EVIDENCE, targetId = evidenceId 확인
- 두 로그 모두 subjectHash = evidence.copyHashValue 확인
- 두 로그 모두 storagePathAtEvent = evidence.copyStoragePath 확인
- ANALYSIS_COPY_VERIFIED payload.verified = true 확인
- RabbitMQ publish 성공 후 ANALYSIS_REQUESTED 로그도 유지되는지 확인
- 로그 해시 체인이 순서대로 연결되는지 확인

복사본 생성/검증 실패:
- AnalysisCopyService 또는 S3Client를 mock해 복사본 생성/검증 실패를 유도
- 분석 요청 API 호출
- AnalysisRequest.status = FAILED 확인
- RabbitMQ publish가 호출되지 않았는지 확인
- ERROR_OCCURRED 로그 저장 확인
- ERROR_OCCURRED payload.step이 ANALYSIS_COPY_CREATED 또는 ANALYSIS_COPY_VERIFIED인지 확인
- ANALYSIS_REQUESTED 로그가 저장되지 않았는지 확인

복사본 삭제 성공:
- 복사본 ACTIVE 상태 Evidence 준비
- deleteCopy(...) 호출
- Evidence.copyStatus = DELETED 확인
- Evidence.copyDeletedAt이 null이 아닌지 확인
- copyHashValue는 유지되는지 확인
- ANALYSIS_COPY_DELETED 로그 1개 확인
- subjectHash = 삭제된 copyHashValue 확인
- storagePathAtEvent = 삭제된 copyStoragePath 확인

이벤트명 검증:
- COPY_CREATED, COPY_VERIFIED, COPY_DELETED 같은 비ERD actionType이 저장되지 않는지 확인
- ANALYSIS_STARTED가 저장되지 않는지 확인
- ANALYSIS_COMPLETED가 저장되지 않는지 확인
- ANALYSIS_FAILED가 저장되지 않는지 확인

기존 기능 유지:
- 파일 업로드 CoC 로그 3개 저장 기능이 깨지면 안 된다.
- RabbitMQ publish 성공/실패 CoC 흐름이 깨지면 안 된다.
- 기존 분석 요청 응답 형식이 깨지면 안 된다.
- 기존 CustodyLogService 해시 체인 테스트가 깨지면 안 된다.

11. 테스트 실행
구현 후 전체 테스트를 실행한다.

sh gradlew test

테스트가 실패하면 원인을 수정한다.

완료 기준:
- 분석 요청 시 분석용 복사본이 생성된다.
- Evidence.copyStatus = ACTIVE, copyHashValue, copyStoragePath, copyCreatedAt이 저장된다.
- ANALYSIS_COPY_CREATED 로그가 저장된다.
- ANALYSIS_COPY_VERIFIED 로그가 저장된다.
- 복사본 삭제 시 Evidence.copyStatus = DELETED, copyDeletedAt이 저장된다.
- ANALYSIS_COPY_DELETED 로그가 저장된다.
- 복사본 생성/검증 실패 시 ERROR_OCCURRED 로그가 저장된다.
- 복사본 생성/검증 실패 시 RabbitMQ publish를 시도하지 않는다.
- AI 분석 시작/완료/실패 이벤트는 아직 저장하지 않는다.
- ERD에 없는 actionType을 저장하지 않는다.
- sh gradlew test가 통과한다.
```

---

## 5단계 체크리스트

```text
[ ] docs/ERD_SPECIFICATION.md Evidences copy* 필드 확인
[ ] docs/COC_LOG_IMPLEMENTATION_GUIDE.md 분석 복사본 흐름 기준 확인
[ ] Evidence에 copy ACTIVE 도메인 메서드 추가
[ ] Evidence에 copy DELETED 도메인 메서드 추가
[ ] AnalysisCopyService 추가
[ ] AnalysisCopyResult 추가
[ ] 분석용 복사본 생성 구현
[ ] 복사본 SHA-256 계산 구현
[ ] originalHashValue/copyHashValue 검증 구현
[ ] copyStatus = ACTIVE 저장
[ ] copyHashValue 저장
[ ] copyStoragePath 저장
[ ] copyCreatedAt 저장
[ ] ANALYSIS_COPY_CREATED 로그 저장
[ ] ANALYSIS_COPY_VERIFIED 로그 저장
[ ] RabbitMQ publish는 복사본 검증 성공 후에만 실행
[ ] 복사본 생성 실패 시 ERROR_OCCURRED 저장
[ ] 복사본 검증 실패 시 ERROR_OCCURRED 저장
[ ] 복사본 실패 시 AnalysisRequest.status = FAILED 저장
[ ] 복사본 실패 시 RabbitMQ publish 미호출 검증
[ ] 복사본 삭제 구현
[ ] copyStatus = DELETED 저장
[ ] copyDeletedAt 저장
[ ] ANALYSIS_COPY_DELETED 로그 저장
[ ] copyHashValue는 삭제 후에도 유지
[ ] COPY_CREATED 같은 비ERD actionType 미사용 검증
[ ] ANALYSIS_STARTED 미사용 검증
[ ] ANALYSIS_COMPLETED 미사용 검증
[ ] ANALYSIS_FAILED 미사용 검증
[ ] sh gradlew test 통과
```

---

## 5단계 이후 작업

5단계가 끝나면 다음 단계에서 실제 AI 분석 실행 상태와 결과 흐름을 연결합니다.

```text
6단계: ANALYSIS_STARTED, ANALYSIS_COMPLETED, ANALYSIS_FAILED 연결
7단계: REPORT_CREATED, REPORT_DOWNLOADED 연결
```
