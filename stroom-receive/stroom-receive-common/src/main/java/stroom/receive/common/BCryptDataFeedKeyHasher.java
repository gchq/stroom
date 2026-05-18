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
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.util.Objects;

@Singleton // For thread safe SecureRandom
public class BCryptDataFeedKeyHasher implements DataFeedKeyHasher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BCryptDataFeedKeyHasher.class);

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateSalt() {
        final String salt = BCrypt.gensalt(10, secureRandom);
        LOGGER.debug("generateSalt() - salt: '{}'", salt);
        return salt;
    }

    @Override
    public HashOutput hash(final String dataFeedKey, final String salt) {
        final String hash = BCrypt.hashpw(Objects.requireNonNull(dataFeedKey), salt);
        final HashOutput hashOutput = new HashOutput(hash, salt);
        LOGGER.debug("hash() - salt: '{}', hash: '{}', dataFeedKey: '{}'", salt, hash, dataFeedKey);
        return hashOutput;
    }

    @Override
    public HashOutput hash(final String dataFeedKey) {
        final String generatedSalt = BCrypt.gensalt(10, secureRandom);
        final String hash = BCrypt.hashpw(Objects.requireNonNull(dataFeedKey), generatedSalt);
        final HashOutput hashOutput = new HashOutput(hash, generatedSalt);
        LOGGER.debug("hash() - generatedSalt: '{}', hash: '{}', dataFeedKey: '{}'", generatedSalt, hash, dataFeedKey);
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
