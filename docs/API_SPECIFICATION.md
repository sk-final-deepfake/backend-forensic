# 🚀 API Specification (ForenShield)

본 문서는 ForenShield 백엔드의 RESTful API 규격을 정의합니다. 모든 API는 `/api/v1`을 Base URL로 사용합니다.

---

## 1. 공통 사항 (Common)

- **Base URL**: `https://api.forenshield.com/api/v1`
- **Authentication**: JWT (JSON Web Token)
    - Header: `Authorization: Bearer {JWT_TOKEN}`
- **Response Format**: JSON
- **Status Codes**:
    - `200 OK`: 요청 성공
    - `201 Created`: 자원 생성 성공
    - `202 Accepted`: 요청 수락 (비동기 처리 시작)
    - `400 Bad Request`: 잘못된 요청 (파라미터 오류 등)
    - `401 Unauthorized`: 인증 실패
    - `403 Forbidden`: 권한 부족 (Admin 전용 등)
    - `500 Internal Server Error`: 서버 내부 오류

---

## 2. Evidence Controller (증거 관리)

### 2.1 증거 파일 업로드
파일을 서버에 업로드하고 무결성 해시를 생성합니다.

- **Method**: `POST`
- **URL**: `/api/v1/evidences/upload`
- **Content-Type**: `multipart/form-data`
- **Request Body**:
    - `file`: MultipartFile (증거 파일)
    - `caseId`: String (사건 번호)
- **Response (201)**:
    ```json
    {
      "evidenceId": 123,
      "fileName": "evidence_001.mp4",
      "hashValue": "sha256_hash_string...",
      "status": "PENDING",
      "uploadedAt": "2026-06-09T14:00:00Z"
    }
    ```

### 2.2 증거 목록 조회
본인이 업로드한 증거 목록을 조회합니다.

- **Method**: `GET`
- **URL**: `/api/v1/evidences`
- **Security**: `@PreAuthorize("hasRole('USER')")`
- **Response (200)**:
    ```json
    [
      {
        "evidenceId": 123,
        "fileName": "evidence_001.mp4",
        "status": "APPROVED",
        "riskLevel": "LOW"
      }
    ]
    ```

---

## 3. Analysis Controller (AI 분석)

### 3.1 분석 요청
특정 증거물에 대한 AI 분석을 요청합니다. (관리자 승인 후 가능)

- **Method**: `POST`
- **URL**: `/api/v1/analysis/request/{evidenceId}`
- **Response (202)**:
    ```json
    {
      "message": "Analysis request has been queued.",
      "evidenceId": 123,
      "queuedAt": "2026-06-09T14:05:00Z"
    }
    ```

### 3.2 분석 결과 상세 조회
특정 증거물의 AI 분석 상세 결과를 조회합니다.

- **Method**: `GET`
- **URL**: `/api/v1/analysis/result/{evidenceId}`
- **Response (200)**:
    ```json
    {
      "analysisId": 50,
      "evidenceId": 123,
      "riskScore": 85.5,
      "riskLevel": "HIGH",
      "reasons": ["Deepfake detected in face sequence", "Audio mismatch"],
      "analyzedAt": "2026-06-09T14:10:00Z"
    }
    ```

---

## 4. Admin Controller (관리자 기능)

### 4.1 증거 승인 및 반려
업로드된 증거의 사법적 가용성을 판단하여 상태를 변경합니다.

- **Method**: `PATCH`
- **URL**: `/api/v1/admin/evidences/{evidenceId}/status`
- **Security**: `@PreAuthorize("hasRole('ADMIN')")`
- **Request DTO**:
    ```json
    {
      "status": "APPROVED",
      "reason": "Internal verification completed."
    }
    ```
- **Response (200)**:
    ```json
    {
      "evidenceId": 123,
      "status": "APPROVED",
      "updatedAt": "2026-06-09T14:15:00Z"
    }
    ```

### 4.2 관리 행위 로그 조회
시스템 내에서 발생한 모든 관리적 행위 이력을 조회합니다.

- **Method**: `GET`
- **URL**: `/api/v1/admin/logs`
- **Security**: `@PreAuthorize("hasRole('ADMIN')")`
- **Response (200)**:
    ```json
    [
      {
        "logId": 1001,
        "adminName": "Admin_01",
        "actionType": "APPROVE",
        "evidenceId": 123,
        "timestamp": "2026-06-09T14:15:00Z"
      }
    ]
    ```
