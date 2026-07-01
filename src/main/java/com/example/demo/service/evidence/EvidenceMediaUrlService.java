package com.example.demo.service.evidence;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.FileType;
import com.example.demo.service.analysis.S3AnalysisAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EvidenceMediaUrlService {

    private final S3AnalysisAccessService s3AnalysisAccessService;

    public MediaUrls resolve(Evidence evidence) {
        if (evidence.getFileType() != FileType.VIDEO) {
            return MediaUrls.empty();
        }

        String url = s3AnalysisAccessService.createPresignedOriginalUrl(evidence.getOriginalStoragePath());
        if (url == null) {
            return MediaUrls.empty();
        }
        return new MediaUrls(url, url, url);
    }

    public record MediaUrls(String previewUrl, String videoUrl, String fileUrl) {

        public static MediaUrls empty() {
            return new MediaUrls(null, null, null);
        }
    }
}
