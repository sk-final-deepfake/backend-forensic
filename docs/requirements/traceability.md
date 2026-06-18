# VeriForensics 요구사항 추적 매트릭스 (RTM)

> **기준:** `기능명세서_최종.xlsx` v1.1 · 백엔드 파트 (BE)

## 1. ID 체계

| 구분 | 형식 | 예시 |
| :--- | :--- | :--- |
| 요구사항 | `RQ-{영역}-{번호}` | `RQ-LOGIN-020` |
| 기능(파트별) | `FN-{영역}-{번호}-{접미}` | `FN-LOGIN-020-BE` |

접미사: `FE`(프론트) · `BE`(백엔드) · `AI`(AI) · `INF`(인프라)

## 2. 백엔드 기능 ↔ RQ 매핑

| FN-ID | RQ-ID | 기능명 | API/컴포넌트 | 구현 상태 |
| :--- | :--- | :--- | :--- | :--- |
| `FN-LOGIN-017-BE` | `RQ-LOGIN-017` | 로그인 페이지 진입 |   A[백엔드 시작] | ✅ |
| `FN-LOGIN-018-BE` | `RQ-LOGIN-018` | 로그인 화면 레이아웃 제공 |   A[백엔드 시작] | ✅ |
| `FN-LOGIN-019-BE` | `RQ-LOGIN-019` | 로그인 정보 입력 |   A[백엔드 시작] | ✅ |
| `FN-LOGIN-020-BE` | `RQ-LOGIN-020` | 로그인 요청 처리 | `POST /api/auth/login`, `POST /api/auth/login`, `POST /api/auth/login`, `POST /a | ✅ |
| `FN-LOGIN-021-BE` | `RQ-LOGIN-021` | 승인된 계정만 로그인 허용 |   A[백엔드 시작] | ✅ |
| `FN-LOGIN-024-BE` | `RQ-LOGIN-024` | 로그인 세션 저장 |   A[백엔드 시작] | ✅ |
| `FN-LOGIN-026-BE` | `RQ-LOGIN-026` | 회원가입 화면 이동 |   A[백엔드 시작] | ✅ |
| `FN-LOGIN-029-BE` | `RQ-LOGIN-029` | 서버 오류 안내 |   A[백엔드 시작] | ✅ |
| `FN-LOGIN-031-BE` | `RQ-LOGIN-031` | 접속 기록 고지 |   A[백엔드 시작] | ✅ |
| `FN-SIGNUP-032-BE` | `RQ-SIGNUP-032` | 회원가입 페이지 진입 |   A[백엔드 시작] | ✅ |
| `FN-SIGNUP-034-BE` | `RQ-SIGNUP-034` | 초대코드 입력·검증 |   A[백엔드 시작] | ✅ |
| `FN-SIGNUP-035-BE` | `RQ-SIGNUP-035` | 필수 입력 검증 |   A[백엔드 시작] | ✅ |
| `FN-SIGNUP-036-BE` | `RQ-SIGNUP-036` | 비밀번호 확인 일치 검증 |   A[백엔드 시작] | ✅ |
| `FN-SIGNUP-037-BE` | `RQ-SIGNUP-037` | 회원가입 요청 처리 |   A[백엔드 시작] | ✅ |
| `FN-SIGNUP-039-BE` | `RQ-SIGNUP-039` | 가입 승인 대기 안내 |   A[백엔드 시작] | ✅ |
| `FN-SIGNUP-040-BE` | `RQ-SIGNUP-040` | 가입 완료 후 로그인 이동 |   A[백엔드 시작] | ✅ |
| `FN-DSH-041-BE` | `RQ-DSH-041` | 서비스 소개 및 바로가기 |   A[백엔드 시작] | — (FE 담당) |
| `FN-DSH-042-BE` | `RQ-DSH-042` | 프로세스 단계 시각화 |   A[백엔드 시작] | — (FE 담당) |
| `FN-DSH-043-BE` | `RQ-DSH-043` | 실시간 현황 통계 카드 |   A[백엔드 시작] | — (FE 담당) |
| `FN-REQ-047-BE` | `RQ-REQ-047` | 영상 파일 업로드 |   A[백엔드 시작] | ✅ |
| `FN-REQ-048-BE` | `RQ-REQ-048` | 원본 파일 WORM 봉인 |   A[백엔드 시작] | — (FE 담당) |
| `FN-REQ-049-BE` | `RQ-REQ-049` | 비동기 AI 분석 큐 연동 | `POST /api/v1/evidences/analyze`, `POST /api/v1/evidences/analyze` | ✅ |
| `FN-REQ-050-BE` | `RQ-REQ-050` | 사본 복제 및 디지털 서명 |   A[백엔드 시작] | ✅ |
| `FN-REQ-051-BE` | `RQ-REQ-051` | 감사 로그 해시 체이닝 |   A[백엔드 시작] | ✅ |
| `FN-REQ-052-BE` | `RQ-REQ-052` | 원본 해시 블록체인 앵커링 |   A[백엔드 시작] | ✅ |
| `FN-DTL-053-BE` | `RQ-DTL-053` | 분석 결과 상세페이지 접근 |   A[백엔드 시작] | ✅ |
| `FN-DTL-054-BE` | `RQ-DTL-054` | 파일 기본정보 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-055-BE` | `RQ-DTL-055` | 분석 상태 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-057-BE` | `RQ-DTL-057` | 최종 위험도 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-058-BE` | `RQ-DTL-058` | 위험 등급 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-059-BE` | `RQ-DTL-059` | 신뢰도 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-060-BE` | `RQ-DTL-060` | 프레임별 위험도 시각화 |   A[백엔드 시작] | ✅ |
| `FN-DTL-061-BE` | `RQ-DTL-061` | 의심 구간 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-062-BE` | `RQ-DTL-062` | 영상 딥페이크 탐지 근거 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-063-BE` | `RQ-DTL-063` | 립싱크 의심 결과 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-064-BE` | `RQ-DTL-064` | 프레임 편집 결과 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-065-BE` | `RQ-DTL-065` | 영상 편집 의심 결과 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-066-BE` | `RQ-DTL-066` | 재인코딩 흔적 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-067-BE` | `RQ-DTL-067` | 메타데이터 결과 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-068-BE` | `RQ-DTL-068` | 코덱·해상도·FPS 정보 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-069-BE` | `RQ-DTL-069` | EXIF 및 타임스탬프 정보 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-071-BE` | `RQ-DTL-071` | Recovery Score 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-072-BE` | `RQ-DTL-072` | 데이터 소실도 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-073-BE` | `RQ-DTL-073` | 분석 처리 타임라인 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-074-BE` | `RQ-DTL-074` | SHA-256 해시값 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-075-BE` | `RQ-DTL-075` | Evidence Manifest 정보 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-077-BE` | `RQ-DTL-077` | CoC 요약 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-078-BE` | `RQ-DTL-078` | 블록체인 등록 상태 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-079-BE` | `RQ-DTL-079` | 블록체인 무결성 검증 결과 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-081-BE` | `RQ-DTL-081` | 구간별 비교 분석 결과 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-082-BE` | `RQ-DTL-082` | 모델명·버전 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-083-BE` | `RQ-DTL-083` | 모델별 점수 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-084-BE` | `RQ-DTL-084` | PDF 리포트 다운로드 |   A[백엔드 시작] | ✅ |
| `FN-DTL-085-BE` | `RQ-DTL-085` | reportHash 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-086-BE` | `RQ-DTL-086` | 검증번호 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-087-BE` | `RQ-DTL-087` | QR 정보 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-089-BE` | `RQ-DTL-089` | 분석 근거 없음 상태 표시 |   A[백엔드 시작] | ✅ |
| `FN-DTL-090-BE` | `RQ-DTL-090` | 본인 분석 결과만 조회 |   A[백엔드 시작] | ✅ |
| `FN-CMP-091-BE` | `RQ-CMP-091` | 원본 증거 선택 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-092-BE` | `RQ-CMP-092` | 검증 대상 파일 업로드 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-093-BE` | `RQ-CMP-093` | 비교 검증 실행 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-094-BE` | `RQ-CMP-094` | SHA-256 해시 비교 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-095-BE` | `RQ-CMP-095` | 파일 크기 비교 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-096-BE` | `RQ-CMP-096` | 영상 길이 비교 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-097-BE` | `RQ-CMP-097` | 코덱 정보 비교 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-098-BE` | `RQ-CMP-098` | 메타데이터 타임스탬프 비교 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-099-BE` | `RQ-CMP-099` | GOP 구조 비교 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-100-BE` | `RQ-CMP-100` | 스트림 체크섬 비교 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-101-BE` | `RQ-CMP-101` | 비교 결과 요약 표시 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-102-BE` | `RQ-CMP-102` | 항목별 비교표 표시 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-103-BE` | `RQ-CMP-103` | 블록체인 등록 해시 대조 |   A[백엔드 시작] | — (FE 담당) |
| `FN-CMP-104-BE` | `RQ-CMP-104` | 비교검증 PDF 다운로드 |   A[백엔드 시작] | — (FE 담당) |
| `FN-HIS-106-BE` | `RQ-HIS-106` | 전체 분석 목록 조회 |   A[백엔드 시작] | — (FE 담당) |
| `FN-HIS-107-BE` | `RQ-HIS-107` | 감사 로그 체인 검증 |   A[백엔드 시작] | — (FE 담당) |
| `FN-MY-108-BE` | `RQ-MY-108` | 마이페이지 접근 |   A[백엔드 시작] | — (FE 담당) |
| `FN-MY-109-BE` | `RQ-MY-109` | 내 정보 조회 |   A[백엔드 시작] | — (FE 담당) |
| `FN-MY-111-BE` | `RQ-MY-111` | 비밀번호 변경 |   A[백엔드 시작] | — (FE 담당) |
| `FN-MY-112-BE` | `RQ-MY-112` | 환경 설정 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-114-BE` | `RQ-ADMIN-114` | 관리자 페이지 접근 제한 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-115-BE` | `RQ-ADMIN-115` | 미인증 사용자 차단 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-116-BE` | `RQ-ADMIN-116` | 일반 사용자 접근 차단 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-117-BE` | `RQ-ADMIN-117` | 관리자 공통 레이아웃 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-119-BE` | `RQ-ADMIN-119` | 관리자 API JWT 인증 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-120-BE` | `RQ-ADMIN-120` | 관리자 통계 조회 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-123-BE` | `RQ-ADMIN-123` | 통계 조회 실패 처리 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-124-BE` | `RQ-ADMIN-124` | 사용자 목록 조회 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-125-BE` | `RQ-ADMIN-125` | 사용자 검색 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-126-BE` | `RQ-ADMIN-126` | 상태별 필터링 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-127-BE` | `RQ-ADMIN-127` | 가입 승인 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-128-BE` | `RQ-ADMIN-128` | 가입 반려 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-130-BE` | `RQ-ADMIN-130` | 비밀번호 초기화 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-131-BE` | `RQ-ADMIN-131` | 사용자 삭제 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-132-BE` | `RQ-ADMIN-132` | 계정 관리 이력 기록 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-133-BE` | `RQ-ADMIN-133` | 초대코드 목록 조회 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-134-BE` | `RQ-ADMIN-134` | 초대코드 생성 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-135-BE` | `RQ-ADMIN-135` | 초대코드 복사 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-137-BE` | `RQ-ADMIN-137` | 시스템 로그 조회 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-138-BE` | `RQ-ADMIN-138` | CoC 로그 전용 조회 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-139-BE` | `RQ-ADMIN-139` | 부서별 로그 필터 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-140-BE` | `RQ-ADMIN-140` | 로그 검색 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-141-BE` | `RQ-ADMIN-141` | 로그 CSV 내보내기 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-143-BE` | `RQ-ADMIN-143` | 관리자 프로필 조회 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-145-BE` | `RQ-ADMIN-145` | 관리자 비밀번호 변경 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-146-BE` | `RQ-ADMIN-146` | 증거 목록 조회 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-147-BE` | `RQ-ADMIN-147` | 증거 검색·필터 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-148-BE` | `RQ-ADMIN-148` | 증거 상세 조회 |   A[백엔드 시작] | — (FE 담당) |
| `FN-ADMIN-149-BE` | `RQ-ADMIN-149` | 해시값 복사 |   A[백엔드 시작] | — (FE 담당) |
| `FN-SEC-151-BE` | `RQ-SEC-151` | 블록체인 머클 트리 앵커링 |   A[백엔드 시작] | — (FE 담당) |
| `FN-SEC-152-BE` | `RQ-SEC-152` | 블록체인 기록 불변성 보장 |   A[백엔드 시작] | — (FE 담당) |
| `FN-SEC-153-BE` | `RQ-SEC-153` | 서명 유효성 검증 실패 대응 |   A[백엔드 시작] | — (FE 담당) |
| `FN-PER-155-BE` | `RQ-PER-155` | 대시보드 로딩 속도 |   A[백엔드 시작] | — (FE 담당) |
| `FN-PER-156-BE` | `RQ-PER-156` | 목록 조회 응답 |   A[백엔드 시작] | — (FE 담당) |
| `FN-NFR-160-BE` | `RQ-NFR-160` | JWT 기반 인증 |   A[백엔드 시작] | — (FE 담당) |
| `FN-NFR-161-BE` | `RQ-NFR-161` | 세션 만료 처리 |   A[백엔드 시작] | — (FE 담당) |
| `FN-NFR-162-BE` | `RQ-NFR-162` | 관리자 API 권한 검증 |   A[백엔드 시작] | — (FE 담당) |

**상태 범례:** ✅ 구현됨 · 🟡 부분 구현 · ⬜ 미구현 · — 다른 파트 담당

## 3. AI / 인프라 FN 요약

### AI (`ai/ai-forensic/`)

| FN-ID | RQ-ID | 기능명 |
| :--- | :--- | :--- |
| `FN-REQ-049-AI` | `RQ-REQ-049` | 비동기 AI 분석 큐 연동 |
| `FN-DTL-053-AI` | `RQ-DTL-053` | 분석 결과 상세페이지 접근 |
| `FN-DTL-057-AI` | `RQ-DTL-057` | 최종 위험도 표시 |
| `FN-DTL-058-AI` | `RQ-DTL-058` | 위험 등급 표시 |
| `FN-DTL-059-AI` | `RQ-DTL-059` | 신뢰도 표시 |
| `FN-DTL-060-AI` | `RQ-DTL-060` | 프레임별 위험도 시각화 |
| `FN-DTL-061-AI` | `RQ-DTL-061` | 의심 구간 표시 |
| `FN-DTL-062-AI` | `RQ-DTL-062` | 영상 딥페이크 탐지 근거 표시 |
| `FN-DTL-063-AI` | `RQ-DTL-063` | 립싱크 의심 결과 표시 |
| `FN-DTL-064-AI` | `RQ-DTL-064` | 프레임 편집 결과 표시 |
| `FN-DTL-065-AI` | `RQ-DTL-065` | 영상 편집 의심 결과 표시 |
| `FN-DTL-067-AI` | `RQ-DTL-067` | 메타데이터 결과 표시 |
| `FN-DTL-070-AI` | `RQ-DTL-070` | 메타데이터 누락 항목 표시 |
| `FN-DTL-071-AI` | `RQ-DTL-071` | Recovery Score 표시 |
| `FN-DTL-072-AI` | `RQ-DTL-072` | 데이터 소실도 표시 |
| `FN-DTL-081-AI` | `RQ-DTL-081` | 구간별 비교 분석 결과 표시 |
| `FN-DTL-082-AI` | `RQ-DTL-082` | 모델명·버전 표시 |
| `FN-DTL-083-AI` | `RQ-DTL-083` | 모델별 점수 표시 |
| `FN-DTL-089-AI` | `RQ-DTL-089` | 분석 근거 없음 상태 표시 |
| `FN-DTL-090-AI` | `RQ-DTL-090` | 본인 분석 결과만 조회 |
| `FN-PER-154-AI` | `RQ-PER-154` | 비동기 분석 응답성 보장 |

### 인프라

| FN-ID | RQ-ID | 기능명 |
| :--- | :--- | :--- |
| `FN-REQ-047-INF` | `RQ-REQ-047` | 영상 파일 업로드 |
| `FN-REQ-048-INF` | `RQ-REQ-048` | 원본 파일 WORM 봉인 |
| `FN-REQ-049-INF` | `RQ-REQ-049` | 비동기 AI 분석 큐 연동 |
| `FN-REQ-050-INF` | `RQ-REQ-050` | 사본 복제 및 디지털 서명 |
| `FN-REQ-052-INF` | `RQ-REQ-052` | 원본 해시 블록체인 앵커링 |
| `FN-DTL-076-INF` | `RQ-DTL-076` | 전자서명 상태 표시 |
| `FN-DTL-080-INF` | `RQ-DTL-080` | 블록체인 트랜잭션 링크 제공 |
| `FN-CMP-103-INF` | `RQ-CMP-103` | 블록체인 등록 해시 대조 |
| `FN-SEC-150-INF` | `RQ-SEC-150` | WORM 스토리지 보존 규칙 |
| `FN-SEC-151-INF` | `RQ-SEC-151` | 블록체인 머클 트리 앵커링 |
| `FN-SEC-152-INF` | `RQ-SEC-152` | 블록체인 기록 불변성 보장 |
| `FN-PER-154-INF` | `RQ-PER-154` | 비동기 분석 응답성 보장 |