package stroom.receive.common;

import stroom.util.shared.NullSafe;

import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.Objects;

public class BCryptDataFeedKeyHasher implements DataFeedKeyHasher {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public HashOutput hash(final String dataFeedKey) {
        final String generatedSalt = BCrypt.gensalt(10, secureRandom);
        final String hash = BCrypt.hashpw(Objects.requireNonNull(dataFeedKey), generatedSalt);
        return new HashOutput(hash, generatedSalt);
    }

    @Override
    public boolean verify(final String dataFeedKey, final String hash, final String salt) {
        if (NullSafe.isEmptyString(dataFeedKey)) {
            return false;
        } else {
            return BCrypt.checkpw(dataFeedKey, hash);
        }
    }

    @Override
    public DataFeedKeyHashAlgorithm getAlgorithm() {
        return DataFeedKeyHashAlgorithm.BCRYPT_2A;
    }
}
