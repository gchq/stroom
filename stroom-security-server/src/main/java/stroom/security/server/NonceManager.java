package stroom.security.server;

import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * A 'nonce' is a single use, cryptographically random string,
 * and it's use here is to prevent replay attacks.
 *
 * A nonce is used in the authentication flow - the hash is included in the original AuthenticationRequest
 * that Stroom makes to the Authentication Service. When Stroom subsequently receives an access code
 * it retrieves the ID token from the Authentication Service and expects to see
 * the hash of the nonce on the token. It can then compare the hashes.
 */
@Component
public class NonceManager {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(NonceManager.class);

    /** Session ID to nonce */
    private Map<String, byte[]> nonces = new HashMap<>();

    /**
     * Creates a nonce, associates it with a sessionId, and returns the hash of the nonce.
     */
    public String createNonce(String sessionId) {
        byte[] nonce = createNonce();
        nonces.put(sessionId, nonce);
        String nonceHash = getNonceHash(nonce);
        return nonceHash;
    }

    public boolean match(String sessionId, String incomingNonceHash){
        if(nonces.containsKey(sessionId)){
            byte[] nonce = nonces.get(sessionId);
            String nonceHash = getNonceHash(nonce);
            return incomingNonceHash.equals(nonceHash);
        }
        return false;
    }

    private static byte[] createNonce(){
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private static String getNonceHash(byte[] nonce) {
        // We don't want to reveal our nonce, so we send a hash instead.
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        digest.update(nonce);
        String nonceHash = Base64.getEncoder().encodeToString(nonce);
        return nonceHash;
    }

    public void forget(String sessionId) {
        nonces.remove(sessionId);
    }
}
