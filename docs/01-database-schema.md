# 🗄️ PostgreSQL 주요 테이블 명세서

사법적 무결성 검증과 비동기 처리 상태 관리에 최적화된 핵심 데이터베이스 스키마입니다.

## 1. files 테이블
업로드된 증거 파일의 메타데이터와 S3 저장 경로를 관리하는 마스터 테이블입니다.

| 컬럼명 | 데이터 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| **`file_id`** | UUID | PK | 증거 파일 고유 식별자 |
| **`case_id`** | UUID | FK | 해당 증거가 속한 사건(Case)의 고유 식별자 |
| **`sha256_hash`** | VARCHAR(64) | UNIQUE | 원본 파일의 SHA-256 해시값 (무결성 기준점) |
| **`file_name`** | VARCHAR(255) | NOT NULL | 원본 파일명 및 확장자 |
| **`s3_origin_key`** | VARCHAR(512) | NOT NULL | S3 `forenshield-evidence` 내 원본 경로 (Object Lock) |
| **`s3_copy_key`** | VARCHAR(512) | NOT NULL | AI 분석용 사본 S3 객체 경로 |
| **`file_size`** | BIGINT | NOT NULL | 파일 크기 (Byte) |
| **`mime_type`** | VARCHAR(100) | NOT NULL | 미디어 타입 (예: video/mp4) |
| **`created_at`** | TIMESTAMPTZ | DEFAULT NOW() | 시스템 등록 일시 |

## 2. analysis_requests 테이블
비동기 AI 분석 진행 상태와 모달리티별 결과 데이터를 저장합니다.

| 컬럼명 | 데이터 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| **`request_id`** | UUID | PK | 분석 요청 고유 식별자 |
| **`file_id`** | UUID | FK | 대상 파일 고유 식별자 |
| **`status`** | VARCHAR(20) | CHECK | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| **`video_result`** | JSONB | - | 영상 분석 워커 반환 데이터 |
| **`audio_result`** | JSONB | - | 음성 분석 워커 반환 데이터 |
| **`image_result`** | JSONB | - | 이미지 분석 워커 반환 데이터 |
| **`risk_score`** | INTEGER | - | 종합 위험도 점수 |
| **`created_at`** | TIMESTAMPTZ | DEFAULT NOW() | 요청 발행 일시 |
| **`updated_at`** | TIMESTAMPTZ | DEFAULT NOW() | 최종 업데이트 일시 |

## 3. coc_logs (보관 연속성 로그) 테이블
사법적 증거 능력을 위해 해시 체인 구조를 가지는 Append-Only 테이블입니다.

| 컬럼명 | 데이터 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| **`log_id`** | BIGSERIAL | PK | 로그 고유 식별자 |
| **`case_id`** | UUID | NOT NULL | 사건 고유 식별자 |
| **`file_id`** | UUID | NOT NULL | 파일 고유 식별자 |
| **`action_user`** | VARCHAR(100) | NOT NULL | 수행 작업자 |
| **`action_type`** | VARCHAR(20) | NOT NULL | 행위 유형 (`UPLOAD`, `ANALYZE` 등) |
| **`current_hash`** | VARCHAR(64) | NOT NULL | 행위 시점의 파일 해시값 |
| **`previous_log_hash`**| VARCHAR(64) | - | 이전 로그 레코드의 해시 (Chain) |
| **`client_ip`** | INET | NOT NULL | 클라이언트 IP 주소 |
| **`timestamp`** | TIMESTAMPTZ | DEFAULT NOW() | 로그 기록 일시 |
