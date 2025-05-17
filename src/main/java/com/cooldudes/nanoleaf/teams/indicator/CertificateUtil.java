package com.cooldudes.nanoleaf.teams.indicator;

import java.nio.file.Files;
import java.nio.file.Paths;

public class CertificateUtil {
    public static String getBase64EncodedCertificate(String certPath) throws Exception {
        byte[] certBytes = Files.readAllBytes(Paths.get(certPath));
        return new String(certBytes)
                .replaceAll("-----BEGIN CERTIFICATE-----", "")
                .replaceAll("-----END CERTIFICATE-----", "")
                .replaceAll("\\s+", "");
    }
}
