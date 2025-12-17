/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.crypto.shared;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    private static final String CRYPTO_ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int TAG_LENGTH = 128; // Must be one of: {128, 120, 112, 104, 96}
    private static final int IV_LENGTH = 12;
    private static final int SALT_LENGTH = 16;

    /**
     * Generate an initialisation vector
     * @param length Length in bytes of the IV
     */
    public static byte[] getRandomNonce(final int length) {
        final byte[] nonce = new byte[length];
        new SecureRandom().nextBytes(nonce);

        return nonce;
    }

    /**
     * Generate an AES key from a secret
     */
    public static SecretKey getAESKeyFromPassword(final String password, final byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        final SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        final KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, KEY_ITERATION_COUNT, KEY_LENGTH);

        return new SecretKeySpec(keyFactory.generateSecret(keySpec).getEncoded(), "AES");
    }

    /**
     * Encrypt a plain-text string and encode the result in base-64
     * @return Base-64 encoded cipher text
     */
    public static String encrypt(final String plainText, final String password) throws Exception {
        if (plainText == null) {
            return null;
        }

        final byte[] salt = getRandomNonce(SALT_LENGTH);
        final byte[] iv = getRandomNonce(IV_LENGTH);
        final SecretKey aesKey = getAESKeyFromPassword(password, salt);
        final Cipher cipher = Cipher.getInstance(CRYPTO_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_LENGTH, iv));

        final byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        final byte[] cipherTextWithIvSalt = ByteBuffer.allocate(iv.length + salt.length + cipherText.length)
            .put(iv)
            .put(salt)
            .put(cipherText)
            .array();

        return Base64.getEncoder().encodeToString(cipherTextWithIvSalt);
    }

    /**
     * Decrypt a base-64 encoded cipher-text string
     * @return Plain text
     */
    public static String decrypt(final String cipherText, final String password) throws Exception {
        if (cipherText == null) {
            return null;
        }

        final byte[] decodedCipherText = Base64.getDecoder().decode(cipherText.getBytes(StandardCharsets.UTF_8));
        final ByteBuffer byteBuffer = ByteBuffer.wrap(decodedCipherText);

        // Extract the IV, salt and original cipher-text
        final byte[] iv = new byte[IV_LENGTH];
        byteBuffer.get(iv);
        final byte[] salt = new byte[SALT_LENGTH];
        byteBuffer.get(salt);
        final byte[] originalCipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(originalCipherText);

        // Reconstruct AES key using the password and salt
        final SecretKey aesKey = getAESKeyFromPassword(password, salt);

        final Cipher cipher = Cipher.getInstance(CRYPTO_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_LENGTH, iv));
        final byte[] plainText = cipher.doFinal(originalCipherText);

        return new String(plainText, StandardCharsets.UTF_8);
    }
}
