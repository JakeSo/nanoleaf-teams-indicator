package com.cooldudes.nanoleaf.teams.indicator;

import com.nimbusds.oauth2.sdk.util.JSONUtils;
import net.minidev.json.JSONObject;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

public class EncryptedData {
    private final String data;
    private final String dataSignature;
    private final String dataKey;
    private final String encryptionCertificateId;
    private final String encryptionCertificateThumbprint;


    public EncryptedData(JSONObject json) {
        this.data = json.getAsString("data");
        this.dataSignature = json.getAsString("dataSignature");
        this.dataKey = json.getAsString("dataKey");
        this.encryptionCertificateId = json.getAsString("encryptionCertificateId");
        this.encryptionCertificateThumbprint = json.getAsString("encryptionCertificateThumbprint");
    }

    public Presence decryptData() throws Exception {
        try {
            byte[] symmetricKey = decryptSymmetricKey();
            if (checkSignature(symmetricKey)) {
                String resourceData = decryptResourceData(symmetricKey);
                JSONObject resourceObj = (JSONObject) JSONUtils.parseJSON(resourceData);
                return new Presence(resourceObj.getAsString("availability"), resourceObj.getAsString("activity"));
            }
            return null;
        } catch (Exception e) {
            throw new Exception("Error decrypting data: " + e.getMessage(), e);
        }
    }

    private byte[] decryptSymmetricKey() throws Exception {
        Properties properties = new Properties();
        String storepass; //password used to open the jks store
        try (FileInputStream is = new FileInputStream("src/main/resources/keystore.properties")) {
            properties.load(is);
            storepass = properties.getProperty("pass");
        } catch (Exception e) {
            throw new Exception("Could not read properties for keystore", e);
        }
        String storename = "src/main/resources/keystore.jks"; //name/path of the jks store
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(storename), storepass.toCharArray());
            Key asymmetricKey = ks.getKey(this.encryptionCertificateId, storepass.toCharArray());
            byte[] encryptedSymmetricKey = Base64.getDecoder().decode(this.dataKey);
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, asymmetricKey);
            return cipher.doFinal(encryptedSymmetricKey);
// Can now use decryptedSymmetricKey with the AES algorithm.
        } catch (KeyStoreException | FileNotFoundException e) {
            System.out.println("Error getting keystore: " + e.getMessage());
            return null;

        } catch (Exception e) {
            throw new Exception("Error symmetric key: " + e.getMessage(), e);
        }
    }

    private boolean checkSignature(byte[] symmetricKey) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] decodedEncryptedData = Base64.getDecoder().decode(this.data);
        SecretKey skey = new SecretKeySpec(symmetricKey, "HMACSHA256");
            Mac mac = Mac.getInstance("HMACSHA256");
            mac.init(skey);
            // Compute the HMAC on the decoded encrypted data
            byte[] computedHmac = mac.doFinal(decodedEncryptedData);
            // Encode the computed HMAC to Base64
            String encodedComputedHmac = Base64.getEncoder().encodeToString(computedHmac);
            return encodedComputedHmac.equals(this.dataSignature);

    }

    private String decryptResourceData(byte[] symmetricKey) throws InvalidAlgorithmParameterException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException {
            SecretKey skey = new SecretKeySpec(symmetricKey, "AES");
            IvParameterSpec ivspec = new IvParameterSpec(Arrays.copyOf(symmetricKey, 16));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skey, ivspec);
        return new String(cipher.doFinal(Base64.getDecoder().decode(this.data)));

    }


}
