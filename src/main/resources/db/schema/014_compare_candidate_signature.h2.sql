-- 로컬 H2는 ddl-auto=update로 자동 반영되지만 수동 초기화에도 사용합니다.

ALTER TABLE compare_verifications ADD COLUMN IF NOT EXISTS candidate_evidence_id BIGINT;
ALTER TABLE compare_verifications ADD COLUMN IF NOT EXISTS original_signature_status VARCHAR(20);
ALTER TABLE compare_verifications ADD COLUMN IF NOT EXISTS candidate_signature_status VARCHAR(20);
