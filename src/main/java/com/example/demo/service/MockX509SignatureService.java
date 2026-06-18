package com.example.demo.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * RQ-REQ-050: 개발·시연용 기관 X.509 서명 mock.
 * 운영 환경에서는 HSM/실제 PKI 연동으로 교체한다.
 */
@Service
public class MockX509SignatureService {

    public static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private final KeyPair institutionKeyPair;

    public MockX509SignatureService() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.institutionKeyPair = generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Mock X.509 key pair initialization failed", ex);
        }
    }

    public String signManifest(String canonicalManifestJson) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(institutionKeyPair.getPrivate());
            signature.update(canonicalManifestJson.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("Manifest signing failed", ex);
        }
    }

    public boolean verifyManifest(String canonicalManifestJson, String signatureBase64) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(institutionKeyPair.getPublic());
            signature.update(canonicalManifestJson.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception ex) {
            return false;
        }
    }
}
