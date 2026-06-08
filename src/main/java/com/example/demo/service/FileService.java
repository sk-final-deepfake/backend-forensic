package com.example.demo.service;

import com.example.demo.dto.FileUploadResponse;
import com.example.demo.dto.MediaMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class FileService {

    private final Path root;
    private final MediaService mediaService;

    public FileService(@Value("${file.upload-dir:uploads}") String uploadDir, MediaService mediaService) {
        this.root = Paths.get(uploadDir);
        this.mediaService = mediaService;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize folder for upload!");
        }
    }

    public FileUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("FILE_NOT_FOUND");
        }

        try {
            Files.createDirectories(this.root);
            String originalFilename = file.getOriginalFilename();
            String storedFileName = UUID.randomUUID().toString() + "_" + originalFilename;
            Path targetPath = this.root.resolve(storedFileName);
            Files.copy(file.getInputStream(), targetPath);

            Object metadata = null;
            // 미디어 파일인 경우에만 메타데이터 추출 시도
            if (isMediaFile(originalFilename)) {
                try {
                    metadata = mediaService.extractMetadata(targetPath);
                } catch (Exception e) {
                    log.error("Metadata extraction failed for {}: {}", originalFilename, e.getMessage());
                    // 손상된 파일인 경우 "깨짐"으로 표시
                    metadata = "깨짐";
                }
            }

            return FileUploadResponse.builder()
                    .success(true)
                    .message("파일 업로드 완료")
                    .fileName(originalFilename)
                    .fileSize(file.getSize())
                    .metadata(metadata)
                    .build();
        } catch (Exception e) {
            log.error("FileUpload Error: ", e);
            throw new RuntimeException("FILE_UPLOAD_FAILED");
        }
    }

    private boolean isMediaFile(String fileName) {
        if (fileName == null) return false;
        String ext = fileName.toLowerCase();
        return ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".png") ||
               ext.endsWith(".mp4") || ext.endsWith(".mov") ||
               ext.endsWith(".wav") || ext.endsWith(".mp3");
    }
}
