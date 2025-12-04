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

import stroom.crypto.shared.CryptoUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;

public class TestCryptoUtils {

    @Test
    public void testEncryption() throws Exception {
        final String plainText = "test message";
        final String password = "super secret p@ssword";

        final String encrypted = CryptoUtils.encrypt(plainText, password);
        Assertions.assertTrue(encrypted.length() > 0, "Encrypted message length is > 0");

        // Try with the correct password
        final String decrypted = CryptoUtils.decrypt(encrypted, password);
        Assertions.assertEquals(decrypted, plainText, "Decrypted text is same as original");

        // Try with an incorrect password
        try {
            CryptoUtils.decrypt(encrypted, "wrong password");
        } catch (final AEADBadTagException e) {
            // Successfully caught exception relating to inability to decrypt using the wrong password
        }
    }
}
