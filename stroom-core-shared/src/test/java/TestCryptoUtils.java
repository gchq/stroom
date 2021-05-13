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
        String decrypted = CryptoUtils.decrypt(encrypted, password);
        Assertions.assertEquals(decrypted, plainText, "Decrypted text is same as original");

        // Try with an incorrect password
        try {
            CryptoUtils.decrypt(encrypted, "wrong password");
        } catch (AEADBadTagException e) {
            // Successfully caught exception relating to inability to decrypt using the wrong password
        }
    }
}
