package com.cooldudes.nanoleaf.teams.indicator;

import java.io.InputStream;
import java.util.Base64;

public class CertificateUtil {
    public static String getBase64EncodedCertificate() throws Exception {
        try (InputStream is = CertificateUtil.class.getResourceAsStream("/public-cert.pem")) {
            if (is == null) {
                throw new IllegalStateException("Resource /public-cert.pem not found");
            }
            byte[] certBytes = is.readAllBytes(); // Java 9+, or use Streams if older
            return Base64.getEncoder()
                    .encodeToString(certBytes)
                    .replaceAll("\\s+", ""); // strip whitespace if needed
        }
    }
}
