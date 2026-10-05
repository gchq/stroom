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

package stroom.receive.common;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Singleton;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

// There are subtle differences between this and BCryptHasher, e.g. how the salt is generated,
// so we can't delegate to it.
@Singleton // For thread safe SecureRandom
public class BCryptDataFeedKeyHasher implements DataFeedKeyHasher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BCryptDataFeedKeyHasher.class);
    private static final int SALT_LOG_ROUNDS = 10;
    private static final int MAX_KEY_LENGTH_BYTES = 72;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateSalt() {
        final String salt = BCrypt.gensalt(SALT_LOG_ROUNDS, secureRandom);
        LOGGER.debug("generateSalt() - salt: '{}'", salt);
        return salt;
    }

    @Override
    public HashOutput hash(final String dataFeedKey) {
        final String generatedSalt = BCrypt.gensalt(SALT_LOG_ROUNDS, secureRandom);
        return hash(dataFeedKey, generatedSalt);
    }

    @Override
    public HashOutput hash(final String dataFeedKey, final String salt) {
        Objects.requireNonNull(dataFeedKey);

        // Bcrypt can only handle 72 bytes of input. JBcrypt (that we used before spring-security-crypto)
        // would just ignore the rest of the bytes, but Spring throws an exception if the input is
        // too long. To preserve backwards compatibility, we truncate the input to 72 bytes.
        byte[] keyBytes = dataFeedKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length > MAX_KEY_LENGTH_BYTES) {
            keyBytes = Arrays.copyOfRange(keyBytes, 0, MAX_KEY_LENGTH_BYTES);
        }

        final String hash = BCrypt.hashpw(keyBytes, salt);
        final HashOutput hashOutput = new HashOutput(hash, salt);
        LOGGER.debug("hash() - salt: '{}', hash: '{}', dataFeedKey: '{}'", salt, hash, dataFeedKey);
        return hashOutput;
    }

    @Override
    public boolean verify(final String dataFeedKey, final String hash, final String ignoredSalt) {
        if (NullSafe.isEmptyString(dataFeedKey)) {
            return false;
        } else {
            final boolean isValid = BCrypt.checkpw(dataFeedKey, hash);
            LOGGER.debug("verify() - hash: '{}', dataFeedKey: '{}', isValid: {}", hash, dataFeedKey, isValid);
            return isValid;
        }
    }

    @Override
    public DataFeedKeyHashAlgorithm getAlgorithm() {
        return DataFeedKeyHashAlgorithm.BCRYPT_2A;
    }
}
