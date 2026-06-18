# 📜 AI 분석 인터페이스 JSON 명세 (AI JSON Spec)

본 문서는 **ForenShield** 시스템의 백엔드(Spring Boot)와 AI 분석 서버(FastAPI) 간에 RabbitMQ를 통해 교환되는 비동기 데이터 규격을 정의합니다.

## 1. 개요
*   **통신 방식**: RabbitMQ (AMQP) 비동기 메시징
*   **교환기 (Exchange)**: `ai.analysis.exchange` (Topic) / `ai.result.exchange` (Topic)
*   **데이터 형식**: JSON (UTF-8)

## 2. Analysis Request (백엔드 → AI 워커)
백엔드에서 분석을 요청할 때 발행하는 메시지 규격입니다.

### [필드 명세]
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| **`analysisId`** | Long | 분석 요청 고유 식별자 (PK) |
| **`evidenceId`** | Long | 증거 파일 고유 식별자 |
| **`caseId`** | Long | 사건 고유 식별자 |
| **`fileType`** | String | 파일 유형 (`image`, `video`, `audio`) |
| **`filePath`** | String | S3 내 분석 대상 파일 경로 (사본) |
| **`originalHash`** | String | 원본 파일의 SHA-256 해시값 |
| **`uploadedAt`** | String | 파일 업로드 시각 (ISO 8601) |

### [JSON 예시]
```json
{
  "analysisId": 1024,
  "evidenceId": 512,
  "caseId": 128,
  "fileType": "video",
  "filePath": "cases/128/512/copy/evidence_video.mp4",
  "originalHash": "a3f12e8c9b... (중략) ...7d0e1f",
  "uploadedAt": "2026-06-08T10:00:00Z"
}
```

---

## 3. Analysis Response (AI 워커 → 백엔드)
AI 분석 서버에서 분석 완료 후 백엔드로 반환하는 공통 응답 규격입니다.

### [공통 응답 필드 명세]
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| **`analysisId`** | Long | 분석 요청 고유 식별자 |
| **`evidenceId`** | Long | 증거 파일 고유 식별자 |
| **`status`** | String | 분석 진행 상태 (`COMPLETED`, `FAILED`) |
| **`riskScore`** | Double | 최종 종합 위험도 점수 (0.0 ~ 100.0) |
| **`confidenceScore`**| Double | 분석 결과에 대한 최종 신뢰도 (0.0 ~ 1.0) |
| **`analyzedAt`** | String | 분석 완료 시각 (ISO 8601) |
| **`results`** | Array | 모달리티별 상세 분석 결과 배열 |

---

## 4. 상세 분석 결과 구조 (results 배열 내부)

### A. 음성 (Audio) 분석 결과
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `deepfakeDetected` | Boolean | 딥페이크(합성음) 탐지 여부 |
| `deepfakeScore` | Double | 딥페이크 탐지 점수 |
| `externalManipulationDetected` | Boolean | 외부 변조(노이즈 삽입 등) 탐지 여부 |
| `externalManipulationScore` | Double | 외부 변조 탐지 점수 |
| `editingDetected` | Boolean | 편집(잘라붙이기) 흔적 탐지 여부 |
| `editingScore` | Double | 편집 흔적 탐지 점수 |
| `speakerVerified` | Boolean | 화자 동일인 여부 (DB 대조 시) |
| `speakerSimilarity` | Double | 화자 유사도 점수 |

### B. 영상 (Video) 분석 결과
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `lipSyncDetected` | Boolean | 입모양 불일치(Lip-Sync) 탐지 여부 |
| `lipSyncScore` | Double | 입모양 불일치 점수 |
| `frameEditDetected` | Boolean | 프레임 삭제/수정 탐지 여부 |
| `frameEditScore` | Double | 프레임 수정 점수 |
| `deepfakeDetected` | Boolean | 얼굴 합성(Deepfake) 탐지 여부 |
| `deepfakeScore` | Double | 얼굴 합성 탐지 점수 |
| `splicingDetected` | Boolean | 영상 이어붙이기(Splicing) 탐지 여부 |
| `splicingScore` | Double | 영상 이어붙이기 점수 |
| `reEncodingDetected` | Boolean | 재인코딩(Re-encoding) 흔적 탐지 여부 |
| `reEncodingScore` | Double | 재인코딩 흔적 점수 |

### C. 이미지 (Image) 분석 결과
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| `photoshopDetected` | Boolean | 포토샵 변조 흔적 탐지 여부 |
| `photoshopScore` | Double | 포토샵 변조 점수 |
| `deepfakeDetected` | Boolean | 얼굴 합성 탐지 여부 |
| `deepfakeScore` | Double | 얼굴 합성 점수 |
| `elaScore` | Double | 오류 수준 분석(ELA) 불일치 점수 |
| `prnuConsistency` | Double | 센서 패턴 잡음(PRNU) 일관성 점수 |

---

## 5. 종합 평가 및 위험도 기준

### [종합 평가 필드]
| 필드명 | 타입 | 설명 |
| :--- | :--- | :--- |
| **`riskScore`** | Double | 0.0 ~ 100.0 |
| **`confidenceScore`**| Double | 0.0 ~ 1.0 |
| **`riskLevel`** | String | `LOW`, `MEDIUM`, `HIGH` |
| **`analysisReasons`** | Array[String]| 위험도 판단 근거 (예: "프레임 불일치 80% 발견") |

### [위험도(Risk Level) 판정 기준]
| 레벨 (Risk Level) | 점수 구간 | 비고 |
| :--- | :--- | :--- |
| **`LOW`** | 0.0 ~ 39.9 | 변조 흔적 미미함 (무결성 신뢰도 높음) |
| **`MEDIUM`** | 40.0 ~ 69.9 | 변조 의심 정황 발견 (추가 분석 필요) |
| **`HIGH`** | 70.0 ~ 100.0 | **변조 확실시** (증거 능력 상실 위험 높음) |

---

## 6. 통합 JSON 예시 (최종 응답 형태)
AI 워커에서 백엔드로 전송하는 실제 메시지 형태입니다.

```json
{
  "analysisId": 1024,
  "evidenceId": 512,
  "status": "COMPLETED",
  "riskScore": 85.5,
  "confidenceScore": 0.94,
  "riskLevel": "HIGH",
  "analysisReasons": [
    "Deepfake face detection score is exceptionally high (0.92)",
    "Frame editing traces found in sequence 0:12 - 0:15",
    "Audio-Video sync mismatch detected"
  ],
  "results": [
    {
      "type": "video",
      "lipSyncDetected": true,
      "lipSyncScore": 0.78,
      "frameEditDetected": true,
      "frameEditScore": 0.85,
      "deepfakeDetected": true,
      "deepfakeScore": 0.92,
      "splicingDetected": false,
      "splicingScore": 0.12,
      "reEncodingDetected": true,
      "reEncodingScore": 0.65
    }
  ],
  "analyzedAt": "2026-06-08T10:05:00Z"
}
```

---

## 7. 구현 지침
*   **백엔드 (Java/Spring)**:
    *   `com.fasterxml.jackson.databind.ObjectMapper`를 사용하여 JSON 파싱 및 직렬화를 수행합니다.
    *   Dojo 객체(DTO) 정의 시 `@JsonProperty`를 활용하여 명세서의 필드명과 매핑합니다.
*   **AI 워커 (Python/FastAPI)**:
    *   `pydantic.BaseModel`을 상속받은 스키마 모델을 정의하여 엄격한 타입 체킹과 자동 유효성 검증을 수행합니다.
*   **공통**:
    *   시각 정보(`uploadedAt`, `analyzedAt`)는 항상 **UTC ISO 8601** 형식을 사용합니다.
    *   모든 점수(Score)는 소수점 둘째 자리까지의 정밀도를 권장합니다.
