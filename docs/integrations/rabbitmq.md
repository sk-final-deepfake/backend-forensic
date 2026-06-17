# 🔄 RabbitMQ 분석 파이프라인 설계

메시지 유실 방지와 효율적인 워크로드 분산을 위한 토픽 기반 메시징 구조입니다.

## 1. Exchange 및 라우팅 구성

| 요소 | 이름 | 타입 | 설명 |
| :--- | :--- | :--- | :--- |
| **Analysis Exchange** | `ai.analysis.exchange` | Topic | 분석 요청 분배용 |
| **Result Exchange** | `ai.result.exchange` | Topic | 분석 결과 수집용 |
| **Dead Letter (DLX)** | `ai.dead.exchange` | Direct | 장애 메시지 격리용 |

### Routing Keys
*   `analyze.video`: 영상 분석 워커용
*   `analyze.audio`: 음성 분석 워커용
*   `analyze.image`: 이미지 분석 워커용

## 2. 메시지 신뢰성 및 소비 정책 (Reliability)

*   **Persistent Messages**: RabbitMQ 재시작 시에도 메시지가 유지되도록 Delivery Mode 2로 설정합니다.
*   **Prefetch Count (QoS)**: `prefetch_count=1`로 설정하여 AI 워커가 한 번에 한 개의 무거운 분석 작업만 처리하도록 제한합니다.
*   **Retry Policy**:
    1. 분석 실패 시 최대 3회 재시도 수행.
    2. 3회 초과 시 `ai.dead.exchange`로 메시지를 전송하여 분석 불능 상태로 격리(DLX).
*   **Result Collection**: 모든 워커는 분석 완료 후 결과를 `ai.result.exchange`로 발행하며, 백엔드는 `backend.ai.result.queue` 단일 큐를 통해 이를 수집합니다.
