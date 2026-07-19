package com.hana.omniconnect.portal;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.hana.omniconnect.common.exception.BusinessException;
import com.hana.omniconnect.common.exception.ErrorCode;

@Component
public class PortalSecretCipher {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final PortalProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PortalSecretCipher(PortalProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Portal secret encryption failed", exception);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encrypted);
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted value");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, Arrays.copyOf(payload, IV_BYTES)));
            return new String(cipher.doFinal(Arrays.copyOfRange(payload, IV_BYTES, payload.length)), StandardCharsets.UTF_8);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Portal secret decryption failed", exception);
        }
    }

    private SecretKeySpec key() {
        if (!StringUtils.hasText(properties.apiKeyEncryptionKey())) {
            throw new BusinessException(ErrorCode.PORTAL_SECURITY_NOT_CONFIGURED);
        }
        byte[] material;
        try {
            material = Base64.getDecoder().decode(properties.apiKeyEncryptionKey());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PORTAL_SECURITY_NOT_CONFIGURED);
        }
        if (material.length != 32) {
            throw new BusinessException(ErrorCode.PORTAL_SECURITY_NOT_CONFIGURED);
        }
        return new SecretKeySpec(material, "AES");
    }
}
