# 📜 AI 분석 인터페이스 JSON 명세 (JSON Spec)

백엔드와 AI 분석 워커 간의 메시지 교환 규격입니다.

## 1. Analysis Request (Backend → AI Worker)
분석 요청 시 `ai.analysis.exchange`로 전송되는 데이터 규격입니다.

| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `analysisId` | String (UUID) | 분석 요청 고유 ID |
| `evidenceId` | String (UUID) | 증거 파일 고유 ID |
| `caseId` | String (UUID) | 사건 고유 ID |
| `fileType` | String | `VIDEO`, `AUDIO`, `IMAGE` |
| `filePath` | String | S3 내 분석 대상 사본 경로 (copy/...) |
| `originalHash` | String | 원본 파일의 SHA-256 해시 |
| `uploadedAt` | ISO8601 | 파일 업로드 시각 |

## 2. Analysis Response (AI Worker → Backend)
분석 완료 후 `ai.result.exchange`로 전송되는 데이터 규격입니다.

| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `analysisId` | String (UUID) | 요청받은 분석 고유 ID |
| `evidenceId` | String (UUID) | 증거 파일 고유 ID |
| `status` | String | `COMPLETED` or `FAILED` |
| `riskScore` | Integer | 0-100 사이의 위험도 점수 |
| `confidenceScore`| Float | 분석 결과에 대한 신뢰도 (0.0~1.0) |
| `riskLevel` | String | `LOW`, `MEDIUM`, `HIGH` |
| `analysisReasons` | Array[String]| 위험도 판단 근거 목록 |
| `results` | Array[Object]| 모달리티별 상세 분석 데이터 객체 |
| `analyzedAt` | ISO8601 | 분석 수행 완료 시각 |
