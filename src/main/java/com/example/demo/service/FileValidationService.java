package com.example.demo.service;

import com.example.demo.exception.FileSizeExceededException;
import com.example.demo.exception.UnsupportedFileTypeException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class FileValidationService {

    private static final Map<String, List<String>> ALLOWED_EXTENSIONS = Map.of(
            "IMAGE", Arrays.asList("jpg", "jpeg", "png"),
            "VIDEO", Arrays.asList("mp4", "mov"),
            "AUDIO", Arrays.asList("wav", "mp3", "m4a")
    );

    private static final Map<String, List<String>> ALLOWED_MIME_TYPES = Map.of(
            "IMAGE", Arrays.asList("image/jpeg", "image/png"),
            "VIDEO", Arrays.asList("video/mp4", "video/quicktime"),
            "AUDIO", Arrays.asList("audio/wav", "audio/mpeg", "audio/x-wav", "audio/mp4", "audio/x-m4a")
    );

    private static final Map<String, Long> MAX_FILE_SIZES = Map.of(
            "IMAGE", 20 * 1024 * 1024L, // 20MB
            "VIDEO", 500 * 1024 * 1024L, // 500MB
            "AUDIO", 100 * 1024 * 1024L // 100MB
    );

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일이 없습니다.");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("파일명이 존재하지 않습니다.");
        }

        String extension = getExtension(fileName);
        String mimeType = file.getContentType();

        String fileType = determineFileType(extension, mimeType);
        if (fileType == null) {
            throw new UnsupportedFileTypeException("지원하지 않는 파일 형식입니다. 이미지, 영상, 음성 파일만 업로드할 수 있습니다.");
        }

        validateSize(file.getSize(), fileType);
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1).toLowerCase();
    }

    private String determineFileType(String extension, String mimeType) {
        if (mimeType == null) return null;

        for (Map.Entry<String, List<String>> entry : ALLOWED_EXTENSIONS.entrySet()) {
            String type = entry.getKey();
            if (entry.getValue().contains(extension) && ALLOWED_MIME_TYPES.get(type).contains(mimeType)) {
                return type;
            }
        }
        return null;
    }

    private void validateSize(long size, String fileType) {
        long maxSize = MAX_FILE_SIZES.get(fileType);
        if (size > maxSize) {
            throw new FileSizeExceededException(String.format("%s 파일의 최대 허용 용량은 %dMB입니다.", 
                    fileType, maxSize / (1024 * 1024)));
        }
    }
}
