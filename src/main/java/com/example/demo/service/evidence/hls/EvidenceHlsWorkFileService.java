package com.example.demo.service.evidence.hls;

import com.example.demo.domain.Evidence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceHlsWorkFileService {

    private final S3Client s3Client;

    @Value("${aws.s3.evidence-bucket}")
    private String evidenceBucket;

    @Value("${file.upload-dir:uploads/original}")
    private String uploadDir;

    public Path prepareWorkDir(Long evidenceId) throws IOException {
        Path workRoot = Paths.get(uploadDir).resolve("hls-packaging").resolve(String.valueOf(evidenceId));
        if (Files.exists(workRoot)) {
            deleteDirectoryQuietly(workRoot);
        }
        Files.createDirectories(workRoot);
        return workRoot;
    }

    public Path downloadOriginal(Evidence evidence, Path workDir) throws IOException {
        Path target = workDir.resolve(sanitizeFileName(evidence.getFileName()));
        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(evidenceBucket)
                        .key(evidence.getOriginalStoragePath())
                        .build())) {
            Files.copy(stream, target);
        }
        return target;
    }

    public void uploadPackagedFiles(Path workDir, String s3Prefix) throws IOException {
        try (Stream<Path> paths = Files.walk(workDir)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> uploadSingle(path, workDir, s3Prefix));
        }
    }

    private void uploadSingle(Path file, Path workDir, String s3Prefix) {
        String relative = workDir.relativize(file).toString().replace('\\', '/');
        if (relative.equals("enc.key") || relative.endsWith("/enc.key") || relative.equals("keyinfo.txt")) {
            return;
        }
        String key = s3Prefix + relative;
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(evidenceBucket)
                        .key(key)
                        .build(),
                RequestBody.fromFile(file));
        log.debug("Uploaded HLS object s3://{}/{}", evidenceBucket, key);
    }

    public void deleteDirectoryQuietly(Path workDir) {
        if (workDir == null || !Files.exists(workDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    log.warn("Failed to delete {}", path, ex);
                }
            });
        } catch (IOException ex) {
            log.warn("Failed to cleanup HLS work dir {}", workDir, ex);
        }
    }

    private String sanitizeFileName(String fileName) {
        String safe = Paths.get(fileName).getFileName().toString();
        return safe.isBlank() ? "evidence.mp4" : safe;
    }
}
