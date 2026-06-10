# 🚀 API Specification (ForenShield)

본 문서는 ForenShield 백엔드의 RESTful API 규격을 정의합니다. 최신 구현 코드(Spring Boot)와 프론트엔드(Next.js) 호출 구조를 바탕으로 작성되었습니다.

---

## 1. 공통 사항 (Common)

- **Base URL**: `/api` (기본값)
- **Authentication**: JWT (JSON Web Token) - *구현 예정*
- **Response Format**: JSON
- **Status Codes**:
    - `200 OK`: 요청 성공
    - `201 Created`: 자원 생성 성공
    - `400 Bad Request`: 잘못된 요청 (파일 누락, 지원하지 않는 형식 등)
    - `401 Unauthorized`: 인증 실패
    - `403 Forbidden`: 권한 부족
    - `500 Internal Server Error`: 서버 내부 오류 (해시 생성 실패 등)

---

## 2. [메인 서비스]

### 2.1 증거 파일 업로드
파일을 서버에 업로드하고 SHA-256 무결성 해시를 생성하며 메타데이터를 추출합니다.

- **엔드포인트**: `POST /api/evidences/upload`
- **설명**: 멀티파트 파일을 받아 서버 저장소에 저장하고 DB에 기록합니다. 사건명을 함께 입력할 수 있습니다.
- **요청 데이터(Request)**:
    - `Content-Type`: `multipart/form-data`
    - `file`: MultipartFile (증거 파일, 최대 2GB)
    - `caseName`: String (사건명, 선택 사항)
- **응답 데이터(Response)**:
    ```json
    {
      "success": true,
      "message": "파일 업로드 완료",
      "evidenceId": 1,
      "fileName": "sample_video.mp4",
      "caseName": "2026-서울-0123 딥페이크 유포 사건",
      "fileSize": 1048576,
      "hashAlgorithm": "SHA-256",
      "hashValue": "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d",
      "metadata": {
        "type": "video",
        "duration": 12.5,
        "width": 1920,
        "height": 1080,
        "codec": "h264",
        "fps": 30.0
      }
    }
    ```
- **에러 코드**:
    - `400 FILE_NOT_FOUND`: 업로드된 파일이 없음
    - `400 UNSUPPORTED_FILE_TYPE`: 허용되지 않은 파일 확장자
    - `400 FILE_SIZE_EXCEEDED`: 파일 크기 제한 초과
    - `500 HASH_GENERATION_FAILED`: SHA-256 해시 생성 중 오류

### 2.2 증거 목록 조회 (Planned)
사용자가 업로드한 증거물 목록을 조회합니다. (현재 프론트엔드 Mock 데이터 사용 중)

- **엔드포인트**: `GET /api/evidences`
- **설명**: 대시보드 진입 시 전체 케이스 현황 요약 데이터 조회
- **요청 데이터(Request)**: 없음 (JWT 기반)
- **응답 데이터(Response)**:
    ```json
    [
      {
        "evidenceId": 1,
        "fileName": "evidence_001.mp4",
        "status": "APPROVED",
        "riskLevel": "LOW",
        "uploadedAt": "2026-06-09T14:00:00Z"
      }
    ]
    ```

### 2.3 분석 요청 (Planned)
업로드된 증거물에 대한 AI 딥페이크 분석을 요청합니다.

- **엔드포인트**: `POST /api/analysis/request/{evidenceId}`
- **설명**: 특정 증거물에 대한 비동기 분석 큐 등록
- **응답 데이터(Response)**:
    ```json
    {
      "message": "Analysis request has been queued.",
      "evidenceId": 1,
      "queuedAt": "2026-06-09T14:05:00Z"
    }
    ```

---

## 3. [관리자 전용]

### 3.1 증거 승인 및 반려 (Planned)
관리자가 증거물의 유효성을 검토하여 상태를 업데이트합니다.

- **엔드포인트**: `PATCH /api/admin/evidences/{evidenceId}/status`
- **설명**: 증거 상태 변경 (APPROVED, REJECTED)
- **요청 데이터(Request)**:
    ```json
    {
      "status": "APPROVED",
      "reason": "검증 완료"
    }
    ```
- **응답 데이터(Response)**:
    ```json
    {
      "evidenceId": 1,
      "status": "APPROVED",
      "updatedAt": "2026-06-09T14:15:00Z"
    }
    ```

### 3.2 관리 행위 로그 조회 (Planned)
시스템 내 관리자 작업 이력 및 CoC(Chain of Custody) 로그를 조회합니다.

- **엔드포인트**: `GET /api/admin/logs`
- **설명**: 감사용 로그 데이터 조회
- **응답 데이터(Response)**:
    ```json
    [
      {
        "logId": 1001,
        "actor": "admin_01",
        "category": "COC",
        "action": "UPLOAD",
        "timestamp": "2026-06-09T14:15:00Z"
      }
    ]
    ```

---

## 4. [개인정보/설정]

### 4.1 내 프로필 조회 (Planned)
현재 로그인한 사용자의 정보를 조회합니다.

- **엔드포인트**: `GET /api/me`
- **설명**: 마이페이지 진입 시 사용자 기본 정보 조회
- **응답 데이터(Response)**:
    ```json
    {
      "userId": "user_01",
      "name": "홍길동",
      "email": "user@example.com",
      "role": "USER"
    }
    ```
