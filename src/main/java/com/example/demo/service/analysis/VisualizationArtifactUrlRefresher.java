package com.example.demo.service.analysis;

import com.example.demo.dto.RepresentativeFrameDto;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Re-signs visualization artifact URLs stored in analysis module details.
 * AI uploads persist presigned URLs signed with short-lived session credentials;
 * detail API must issue fresh URLs from the backend IAM role on each read.
 */
@Component
@RequiredArgsConstructor
public class VisualizationArtifactUrlRefresher {

    private final S3AnalysisAccessService s3AnalysisAccessService;

    @Value("${aws.s3.evidence-bucket}")
    private String evidenceBucket;

    public String refresh(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return null;
        }
        String objectKey = resolveEvidenceObjectKey(storedUrl);
        if (objectKey == null) {
            return storedUrl;
        }
        String refreshed = s3AnalysisAccessService.createPresignedOriginalUrl(objectKey);
        return refreshed != null ? refreshed : storedUrl;
    }

    public List<RepresentativeFrameDto> refreshFrames(List<RepresentativeFrameDto> frames) {
        if (frames == null || frames.isEmpty()) {
            return frames == null ? List.of() : frames;
        }
        return frames.stream()
                .map(frame -> RepresentativeFrameDto.builder()
                        .timeSec(frame.getTimeSec())
                        .timestamp(frame.getTimestamp())
                        .frameNumber(frame.getFrameNumber())
                        .score(frame.getScore())
                        .imageUrl(refresh(frame.getImageUrl()))
                        .heatmapUrl(refresh(frame.getHeatmapUrl()))
                        .build())
                .toList();
    }

    String resolveEvidenceObjectKey(String storedUrl) {
        String trimmed = storedUrl.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith("s3://")) {
            return resolveS3UriKey(trimmed);
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return resolveHttpUrlKey(trimmed);
        }

        if (!trimmed.contains("://")) {
            return trimmed;
        }

        return null;
    }

    private String resolveS3UriKey(String s3Uri) {
        String withoutScheme = s3Uri.substring("s3://".length());
        int slash = withoutScheme.indexOf('/');
        if (slash <= 0 || slash == withoutScheme.length() - 1) {
            return null;
        }
        String bucket = withoutScheme.substring(0, slash);
        if (!evidenceBucket.equals(bucket)) {
            return null;
        }
        return decodePath(withoutScheme.substring(slash + 1));
    }

    private String resolveHttpUrlKey(String url) {
        try {
            String withoutQuery = url.split("\\?", 2)[0];
            URI uri = URI.create(withoutQuery);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null || path == null || path.isBlank()) {
                return null;
            }
            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
            if (host.startsWith(evidenceBucket + ".s3")) {
                return decodePath(normalizedPath);
            }
            if (host.startsWith("s3.") && normalizedPath.startsWith(evidenceBucket + "/")) {
                return decodePath(normalizedPath.substring(evidenceBucket.length() + 1));
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private String decodePath(String path) {
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }
}
