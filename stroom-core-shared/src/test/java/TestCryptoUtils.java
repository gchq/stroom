import stroom.crypto.shared.CryptoUtils;
import stroom.util.test.StroomJUnit4ClassRunner;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.crypto.AEADBadTagException;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestCryptoUtils {
    @Test
    public void TestEncryption() throws Exception {
        final String plainText = "test message";
        final String password = "super secret p@ssword";

        final String encrypted = CryptoUtils.encrypt(plainText, password);
        Assert.assertTrue("Encrypted message length is > 0", encrypted.length() > 0);

        // Try with the correct password
        String decrypted = CryptoUtils.decrypt(encrypted, password);
        Assert.assertEquals("Decrypted text is same as original", decrypted, plainText);

        // Try with an incorrect password
        try {
            CryptoUtils.decrypt(encrypted, "wrong password");
        }
        catch (AEADBadTagException e) {
            // Successfully caught exception relating to inability to decrypt using the wrong password
        }
    }
}
