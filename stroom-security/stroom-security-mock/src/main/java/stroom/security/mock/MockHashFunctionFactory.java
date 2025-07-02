package stroom.security.mock;

import stroom.security.api.HashFunction;
import stroom.security.api.HashFunctionFactory;
import stroom.security.shared.HashAlgorithm;
import stroom.util.string.Base58;
import stroom.util.string.StringUtil;

import jakarta.inject.Singleton;
import org.apache.commons.codec.digest.DigestUtils;

import java.security.SecureRandom;

@Singleton
public class MockHashFunctionFactory implements HashFunctionFactory {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public HashFunction getHashFunction(final HashAlgorithm hashAlgorithm) {
        return new HashFunction() {

            @Override
            public String generateSalt() {
                return StringUtil.createRandomCode(secureRandom, 5);
            }

            @Override
            public String hash(final String value, final String salt) {
                final String saltedVal = salt != null
                        ? salt + value
                        : value;
                return Base58.encode(DigestUtils.md5(saltedVal));
            }

            @Override
            public HashAlgorithm getType() {
                return hashAlgorithm;
            }
        };
    }
}
