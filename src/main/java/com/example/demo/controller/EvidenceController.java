package com.example.demo.controller;

import com.example.demo.dto.ErrorResponse;
import com.example.demo.dto.FileUploadResponse;
import com.example.demo.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Evidence", description = "증거 관련 API")
@RestController
@RequestMapping("/api/evidences")
@RequiredArgsConstructor
public class EvidenceController {

    private final FileService fileService;

    @Operation(summary = "파일 업로드", description = "파일을 서버에 업로드합니다.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @Parameter(description = "업로드할 파일") @RequestParam("file") MultipartFile file
    ) {
        try {
            FileUploadResponse response = fileService.upload(file);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("FILE_NOT_FOUND")
                            .message("업로드된 파일이 없습니다.")
                            .build());
        } catch (RuntimeException e) {
            if ("CORRUPTED_MEDIA_FILE".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ErrorResponse.builder()
                                .success(false)
                                .errorCode("CORRUPTED_MEDIA_FILE")
                                .message("손상된 미디어 파일입니다.")
                                .build());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("FILE_UPLOAD_FAILED")
                            .message("파일 업로드에 실패했습니다.")
                            .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("FILE_UPLOAD_FAILED")
                            .message("파일 업로드에 실패했습니다.")
                            .build());
        }
    }
}
