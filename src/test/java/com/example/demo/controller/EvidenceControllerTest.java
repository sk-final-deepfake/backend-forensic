package com.example.demo.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class EvidenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @AfterEach
    void cleanUp() throws Exception {
        // 테스트 후 생성된 파일 삭제
        Path root = Paths.get(uploadDir);
        if (Files.exists(root)) {
            FileSystemUtils.deleteRecursively(root);
        }
    }

    @Test
    void shouldUploadFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-file.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "Hello, World!".getBytes()
        );

        mockMvc.perform(multipart("/api/evidences/upload")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("파일 업로드 완료"))
                .andExpect(jsonPath("$.fileName").value("test-file.txt"))
                .andExpect(jsonPath("$.fileSize").value(file.getSize()));
    }

    @Test
    void shouldReturnErrorWhenFileIsEmpty() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/evidences/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FILE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("업로드된 파일이 없습니다."));
    }
}
