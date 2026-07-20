-- compare_verifications: 등록 증거 비교 시 후보 서명 상태 스냅샷
-- 운영 환경은 ddl-auto=validate이므로 backend 배포 전에 1회 적용합니다.

ALTER TABLE compare_verifications
    ADD COLUMN IF NOT EXISTS candidate_evidence_id BIGINT;

ALTER TABLE compare_verifications
    ADD COLUMN IF NOT EXISTS original_signature_status VARCHAR(20);

ALTER TABLE compare_verifications
    ADD COLUMN IF NOT EXISTS candidate_signature_status VARCHAR(20);
