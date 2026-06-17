package com.example.demo.service;

import com.example.demo.domain.Evidence;
import com.example.demo.dto.FileUploadResponse;
import com.example.demo.dto.ValidatedFile;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.HashGenerationException;
import com.example.demo.repository.EvidenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class FileService {

    private final Path root;
    private final S3Client s3Client;
    private final String evidenceBucket;
    private final MediaService mediaService;
    private final HashService hashService;
    private final EvidenceRepository evidenceRepository;
    private final FileValidationService fileValidationService;

    public FileService(
            @Value("${file.upload-dir:uploads}") String uploadDir,
            @Value("${aws.s3.evidence-bucket}") String evidenceBucket,
            S3Client s3Client,
            MediaService mediaService,
            HashService hashService,
            EvidenceRepository evidenceRepository,
            FileValidationService fileValidationService
    ) {
        this.s3Client = s3Client;
        this.evidenceBucket = evidenceBucket;
        this.root = Paths.get(uploadDir);
        this.mediaService = mediaService;
        this.hashService = hashService;
        this.evidenceRepository = evidenceRepository;
        this.fileValidationService = fileValidationService;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize folder for upload!");
        }
    }

    @Transactional
    public FileUploadResponse upload(MultipartFile file, String caseName, Long uploaderId) {
        ValidatedFile validated = fileValidationService.validate(file);
        String originalFilename = validated.fileName();

        try {
            Files.createDirectories(this.root);
            String storedFileName = UUID.randomUUID() + "_" + originalFilename;
            Path savedPath = this.root.resolve(storedFileName);
            Files.copy(file.getInputStream(), savedPath);

            String hashValue = hashService.generateSha256(savedPath);

            Object metadata = null;
            try {
                metadata = mediaService.extractMetadata(savedPath);
            } catch (Exception e) {
                log.error("Metadata extraction failed for {}: {}", originalFilename, e.getMessage());
                metadata = "깨짐";
            }

            // 해시·메타데이터 추출이 끝난 로컬 파일을 S3에 업로드 (원본 보관소는 S3)
            // 로컬에 임시 저장된 파일을 S3 버킷에 올리는 코드
            String s3Key = "original/" + storedFileName;
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(evidenceBucket)
                            .key(s3Key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromFile(savedPath));

            Evidence evidence = Evidence.builder()
                    .uploaderId(uploaderId)
                    .caseName(caseName)
                    .fileName(originalFilename)
                    .fileType(validated.fileType())
                    .mimeType(validated.mimeType())
                    .fileSize(validated.fileSize())
                    .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                    .originalHashValue(hashValue)
                    .originalStoragePath(s3Key)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            Evidence savedEvidence = evidenceRepository.save(evidence);

            // 로컬 파일은 임시 작업용 — 보관은 S3가 담당하므로 정리
            Files.deleteIfExists(savedPath);

            return FileUploadResponse.builder()
                    .success(true)
                    .message("파일 업로드 완료")
                    .evidenceId(savedEvidence.getEvidenceId())
                    .fileName(originalFilename)
                    .caseName(savedEvidence.getCaseName())
                    .fileSize(validated.fileSize())
                    .hashAlgorithm(savedEvidence.getHashAlgorithm())
                    .hashValue(savedEvidence.getOriginalHashValue())
                    .metadata(metadata)
                    .build();
        } catch (HashGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("FileUpload Error: ", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_UPLOAD_FAILED", "파일 업로드에 실패했습니다.");
        }
    }
}
