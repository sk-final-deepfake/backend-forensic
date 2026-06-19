package com.example.demo.service;

import com.example.demo.config.EvidenceManifestSigningProperties;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RQ-REQ-050: 단일 플랫폼 CA — PKCS#8 개인키 + X.509 인증서 기반 Manifest 서명.
 */
@Slf4j
@Service
public class Pkcs8ManifestSignatureService implements ManifestSignatureService {

    public static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String signerCertificateSubject;

    public Pkcs8ManifestSignatureService(
            ManifestSigningKeyLoader keyLoader,
            EvidenceManifestSigningProperties signingProperties
    ) {
        ManifestSigningKeyMaterial material = keyLoader.load(signingProperties);
        X509Certificate certificate = material.certificate();
        this.privateKey = material.privateKey();
        this.publicKey = certificate.getPublicKey();
        this.signerCertificateSubject = certificate.getSubjectX500Principal().getName();
        log.info("Platform manifest signing ready subject={}", signerCertificateSubject);
    }

    @Override
    public String getSignatureAlgorithm() {
        return SIGNATURE_ALGORITHM;
    }

    @Override
    public String getSignerCertificateSubject() {
        return signerCertificateSubject;
    }

    @Override
    public String signManifest(String canonicalManifestJson) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(canonicalManifestJson.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("Manifest signing failed", ex);
        }
    }

    @Override
    public boolean verifyManifest(String canonicalManifestJson, String signatureBase64) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(canonicalManifestJson.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception ex) {
            return false;
        }
    }
}
