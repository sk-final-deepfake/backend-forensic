package com.example.demo.service;

import com.example.demo.dto.FileUploadResponse;
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

    public FileService(@Value("${file.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir);
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
            String originalFilename = file.getOriginalFilename();
            // 실제 저장 시에는 중복 방지를 위해 UUID를 유지하는 것이 안전하므로 유지하되, 응답은 요구사항에 맞춰 작성 가능
            String storedFileName = UUID.randomUUID().toString() + "_" + originalFilename;
            Files.copy(file.getInputStream(), this.root.resolve(storedFileName));

            return FileUploadResponse.builder()
                    .success(true)
                    .message("파일 업로드 완료")
                    .fileName(originalFilename)
                    .fileSize(file.getSize())
                    .build();
        } catch (Exception e) {
            log.error("FileUpload Error: ", e);
            throw new RuntimeException("FILE_UPLOAD_FAILED");
        }
    }
}
