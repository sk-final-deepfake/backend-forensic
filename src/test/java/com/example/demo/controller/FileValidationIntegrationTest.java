package com.example.demo.controller;

import com.example.demo.exception.FileSizeExceededException;
import com.example.demo.exception.UnsupportedFileTypeException;
import com.example.demo.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvidenceController.class)
class FileValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileService fileService;

    @Test
    @DisplayName("지원하지 않는 파일 형식 업로드 시 UNSUPPORTED_FILE_TYPE 오류 반환")
    void upload_UnsupportedFileType_ReturnsError() throws Exception {
        when(fileService.upload(any())).thenThrow(new UnsupportedFileTypeException("지원하지 않는 파일 형식입니다. 이미지, 영상, 음성 파일만 업로드할 수 있습니다."));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "unsupported content".getBytes());

        mockMvc.perform(multipart("/api/evidences/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_FILE_TYPE"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 파일 형식입니다. 이미지, 영상, 음성 파일만 업로드할 수 있습니다."));
    }

    @Test
    @DisplayName("파일 용량 초과 시 FILE_SIZE_EXCEEDED 오류 반환")
    void upload_FileSizeExceeded_ReturnsError() throws Exception {
        when(fileService.upload(any())).thenThrow(new FileSizeExceededException("IMAGE 파일의 최대 허용 용량은 20MB입니다."));

        MockMultipartFile file = new MockMultipartFile(
                "file", "large.jpg", "image/jpeg", new byte[1024]);

        mockMvc.perform(multipart("/api/evidences/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FILE_SIZE_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("IMAGE 파일의 최대 허용 용량은 20MB입니다."));
    }
}
