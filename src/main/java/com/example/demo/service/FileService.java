package com.example.demo.service;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.dto.FileUploadResponse;
import com.example.demo.exception.HashGenerationException;
import com.example.demo.repository.EvidenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final MediaService mediaService;
    private final HashService hashService;
    private final EvidenceRepository evidenceRepository;
    private final FileValidationService fileValidationService;

    public FileService(
            @Value("${file.upload-dir:uploads}") String uploadDir,
            MediaService mediaService,
            HashService hashService,
            EvidenceRepository evidenceRepository,
            FileValidationService fileValidationService
    ) {
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
    public FileUploadResponse upload(MultipartFile file) {
        fileValidationService.validate(file);

        try {
            Files.createDirectories(this.root);
            String originalFilename = file.getOriginalFilename();
            String storedFileName = UUID.randomUUID() + "_" + originalFilename;
            Path savedPath = this.root.resolve(storedFileName);
            Files.copy(file.getInputStream(), savedPath);

            String hashValue = hashService.generateSha256(savedPath);

            Object metadata = null;
            // FileValidationService handles supported types, so we can assume it's one of them
            try {
                metadata = mediaService.extractMetadata(savedPath);
            } catch (Exception e) {
                log.error("Metadata extraction failed for {}: {}", originalFilename, e.getMessage());
                metadata = "깨짐";
            }

            Evidence evidence = Evidence.builder()
                    .fileName(originalFilename)
                    .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                    .hashValue(hashValue)
                    .originalStoragePath(savedPath.toString())
                    .status(EvidenceStatus.UPLOADED)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            Evidence savedEvidence = evidenceRepository.save(evidence);

            return FileUploadResponse.builder()
                    .success(true)
                    .message("파일 업로드 완료")
                    .evidenceId(savedEvidence.getEvidenceId())
                    .fileName(originalFilename)
                    .fileSize(file.getSize())
                    .hashAlgorithm(savedEvidence.getHashAlgorithm())
                    .hashValue(savedEvidence.getHashValue())
                    .metadata(metadata)
                    .build();
        } catch (HashGenerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("FileUpload Error: ", e);
            throw new RuntimeException("FILE_UPLOAD_FAILED");
        }
    }
}
