package com.example.demo.service;

import com.example.demo.exception.HashGenerationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class HashService {

	private static final String SHA_256 = "SHA-256";
	private static final int BUFFER_SIZE = 8192;

	public String generateSha256(byte[] fileBytes) {
		try {
			MessageDigest digest = MessageDigest.getInstance(SHA_256);
			byte[] hashBytes = digest.digest(fileBytes);
			return HexFormat.of().formatHex(hashBytes);
		} catch (NoSuchAlgorithmException e) {
			throw new HashGenerationException("SHA-256 생성에 실패했습니다.", e);
		}
	}

	public String generateSha256(InputStream inputStream) {
		try {
			MessageDigest digest = MessageDigest.getInstance(SHA_256);
			byte[] buffer = new byte[BUFFER_SIZE];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				digest.update(buffer, 0, bytesRead);
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException e) {
			throw new HashGenerationException("SHA-256 생성에 실패했습니다.", e);
		} catch (IOException e) {
			throw new HashGenerationException("파일 읽기 중 SHA-256 생성에 실패했습니다.", e);
		}
	}

	public String generateSha256(MultipartFile file) {
		try (InputStream inputStream = file.getInputStream()) {
			return generateSha256(inputStream);
		} catch (IOException e) {
			throw new HashGenerationException("업로드 파일 SHA-256 생성에 실패했습니다.", e);
		}
	}

	public String generateSha256(Path filePath) {
		try (InputStream inputStream = Files.newInputStream(filePath)) {
			return generateSha256(inputStream);
		} catch (IOException e) {
			throw new HashGenerationException("저장된 원본 파일 SHA-256 생성에 실패했습니다.", e);
		}
	}
}
