-- Store case number on case_profiles for S3 object naming (caseName-caseNumber).
ALTER TABLE case_profiles ADD COLUMN IF NOT EXISTS case_number VARCHAR(255);
