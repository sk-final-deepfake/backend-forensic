package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "evidence.manifest")
public class EvidenceManifestProperties {

    /** Manifest 발급 기관 표시명 */
    private String issuer = "ForenShield Digital Forensics";

    /** Mock X.509 인증서 Subject (개발·시연용) */
    private String signerCertificateSubject = "CN=ForenShield Forensics CA,O=SK Project,C=KR";
}
