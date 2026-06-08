# 🛡️ 증거 무결성 및 파일 처리 규격서 (Evidence Integrity & File Processing Spec)

본 문서는 **ForenShield 디지털 포렌식 시스템**에서 업로드된 증거 파일의 원본성을 보존하고, 분석 과정에서의 무결성을 유지하기 위한 파일 저장 및 처리 구조를 정의한다.

---

## 1. 저장소 경로 및 역할 정의

증거 파일의 생명주기에 따라 저장소를 **보존용**, **결과용**, **임시용**으로 엄격히 격리한다.

| 분류 | 경로 (S3/Local) | 상세 역할 | 비고 |
| :--- | :--- | :--- | :--- |
| **원본 보존** | `original-files/{caseId}/{fileId}/original.ext` | 업로드된 원본 증거 파일의 **영구 보존** | 수정/삭제 불가 (WORM) |
| **결과 보고서**| `reports/{caseId}/{fileId}/report.pdf` | 분석 완료 후 생성된 포렌식 결과 보고서 | 사법 제출용 |
| **임시 분석** | `/tmp/analysis/{caseId}/{fileId}/copy.ext` | FFmpeg 전처리, AI 분석, 메타데이터 추출용 | 분석 후 **즉시 삭제** |

---

## 2. 무결성 유지 및 물리적 격리 방침

원본 파일의 증거 능력을 유지하기 위해 다음과 같은 **물리적/논리적 격리 규칙**을 적용한다.

> 💡 **핵심 원칙: 원본 수정 금지 (Immutable Original)**
> - 모든 분석 도구(FFmpeg, AI Model, ExifTool)는 **`original-files` 경로에 직접 접근할 수 없다.**
> - 원본 파일에 대한 모든 읽기 작업은 **SHA-256 해시 검증** 목적 이외에는 허용되지 않는다.
> - 분석 작업은 반드시 **`/tmp/analysis`**에 생성된 복사본만을 대상으로 수행한다.

---

## 3. 분석 복사본 생명주기 (Lifecycle)

분석용 복사본은 필요한 시점에 생성되고, 목적 달성 시 즉시 파기되어 스토리지 효율성을 높인다.

1.  **생성 시점**: 사용자가 '분석 시작' 요청을 보낸 직후, 백엔드 서비스가 `original-files`의 객체를 `tmp/analysis` 경로로 복제하는 시점.
2.  **삭제 시점**: 
    - AI 분석 및 메타데이터 추출이 완료된 후.
    - PDF 보고서 생성이 완료되어 S3 업로드가 성공한 직후.
    - 분석 과정에서 치명적인 오류가 발생하여 작업을 중단할 때.

---

## 4. 데이터베이스 엔티티 설계 (PostgreSQL / JPA)

증거 파일의 메타데이터와 원본/보고서 경로를 관리하기 위한 엔티티 구조이다.

### [FileMetadata Entity]
| 필드명 | 타입 | 제약 조건 | 설명 |
| :--- | :--- | :--- | :--- |
| **`file_id`** | UUID | PK | 증거 파일 고유 식별자 |
| **`case_id`** | UUID | FK | 관련 사건 식별자 |
| **`user_id`** | Long | FK | 업로드 수행자 식별자 |
| **`original_file_name`** | String | NOT NULL | 사용자가 업로드한 원본 파일명 |
| **`file_type`** | String | NOT NULL | MIME Type (video, image, audio 등) |
| **`file_size`** | Long | NOT NULL | 파일 크기 (Bytes) |
| **`original_sha256`** | String | NOT NULL | 원본 파일의 SHA-256 해시값 |
| **`original_file_path`** | String | NOT NULL | S3 내 원본 저장 경로 (`original-files/...`) |
| **`report_file_path`** | String | - | 생성된 PDF 보고서 저장 경로 (`reports/...`) |
| **`uploaded_at`** | TIMESTAMPTZ | DEFAULT NOW() | 업로드 시각 (UTC ISO 8601) |

---

## 5. 저장소 디렉터리 구조 시각화

### [S3 Bucket: forenshield-evidence]
```text
forenshield-evidence/
├── original-files/
│   └── {caseId}/
│       └── {fileId}/
│           └── original.ext       <-- [Immutable]
├── reports/
│   └── {caseId}/
│       └── {fileId}/
│           └── report.pdf         <-- [Final Result]
└── .internal/ (Management logs)
```

### [Local/Ephemeral Storage: AI Worker]
```text
/tmp/analysis/
└── {caseId}/
    └── {fileId}/
        └── copy.ext              <-- [Temporary] Target for AI/FFmpeg
```

---

## 6. 파일 처리 및 CoC 흐름 가이드

디지털 증거의 **연쇄 보관 지침(Chain of Custody)**을 준수하는 표준 처리 흐름이다.

### [Step-by-Step Flow]
1.  **Upload**: 사용자가 MultipartFile 형식으로 파일 업로드.
2.  **Validation**: 백엔드에서 파일 매직 넘버(Magic Number) 및 크기 검증.
3.  **Store Original**: 원본 파일을 **`original-files`** 경로에 즉시 저장.
4.  **Hash Generation**: 저장된 원본 파일을 스트림으로 읽어 **SHA-256 해시** 생성 (CoC 로그: `ORIGINAL_HASH_CREATED`).
5.  **DB Record**: 해시값과 원본 경로를 포함한 메타데이터를 PostgreSQL에 저장.
6.  **Create Copy**: 분석 요청 시 원본을 **`/tmp/analysis`**로 복제 (CoC 로그: `ANALYSIS_COPY_CREATED`).
7.  **Processing**: 복사본을 대상으로 FFmpeg(프레임 추출), ExifTool(메타데이터), AI(추론) 수행.
8.  **Report & Store**: 분석 결과를 취합하여 PDF 보고서 생성 후 **`reports`** 경로에 저장.
9.  **Cleanup**: 분석에 사용된 **임시 복사본 삭제** (CoC 로그: `ANALYSIS_COPY_DELETED`).
10. **Finalize**: 최종 분석 상태를 `COMPLETED`로 업데이트하고 CoC 체인을 마감.

---

## 7. 구현 기술 스택 지침
- **Hash**: `java.security.MessageDigest` (SHA-256) 사용.
- **S3 Client**: `software.amazon.awssdk:s3` 라이브러리를 통한 `copyObject` 및 `putObject` 처리.
- **Transaction**: 파일 저장 성공 시에만 DB 커밋이 이루어지도록 **@Transactional** 범위 설정.
- **Logging**: 모든 파일 이동/삭제 이벤트는 `coc_logs` 테이블에 **`action_user`**, **`client_ip`**, **`timestamp`**와 함께 기록.