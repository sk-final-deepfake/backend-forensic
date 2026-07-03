package com.example.demo.service.manifest;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

record ManifestSigningKeyMaterial(PrivateKey privateKey, X509Certificate certificate) {
}
