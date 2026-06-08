package com.example.demo.controller;

import com.example.demo.dto.ErrorResponse;
import com.example.demo.dto.FileUploadResponse;
import com.example.demo.exception.HashGenerationException;
import com.example.demo.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/evidences")
@RequiredArgsConstructor
public class EvidenceController {

	private final FileService fileService;

	@PostMapping("/upload")
	public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
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
		} catch (HashGenerationException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(ErrorResponse.builder()
							.success(false)
							.errorCode("HASH_GENERATION_FAILED")
							.message(e.getMessage())
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
