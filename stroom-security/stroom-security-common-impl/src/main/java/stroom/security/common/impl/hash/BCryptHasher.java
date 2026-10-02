/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.common.impl.hash;

import stroom.security.api.HashFunction;
import stroom.security.shared.HashAlgorithm;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

class BCryptHasher implements HashFunction {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BCryptHasher.class);

    private static final int MAX_LENGTH_BYTES = 72;

    @Override
    public String generateSalt() {
        return BCrypt.gensalt();
    }

    @Override
    public String hash(final String value, final String salt) {
        Objects.requireNonNull(value, "Value cannot be null");

        // Bcrypt can only handle 72 bytes of input. JBcrypt (that we used before spring-security-crypto)
        // would just ignore the rest of the bytes, but Spring throws an exception if the input is
        // too long. To preserve backwards compatibility, we truncate the input to 72 bytes.
        // This is not an issue for API keys as we enforce uniqueness on the hash in the DB table,
        // so we will never have two API keys with the same hash.
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        if (valueBytes.length > MAX_LENGTH_BYTES) {
            valueBytes = Arrays.copyOfRange(valueBytes, 0, MAX_LENGTH_BYTES);
        }

        return BCrypt.hashpw(
                valueBytes,
                Objects.requireNonNullElseGet(salt, BCrypt::gensalt));
    }

    @Override
    public boolean verify(final String value, final String hash) {
        // Salt is baked into hash, so we don't need to pass it in
        return verify(value, hash, null);
    }

    @Override
    public boolean verify(final String value,
                          final String hash,
                          final String ignoredSalt) {
        if (value == null) {
            return false;
        } else {
            // Salt is encoded in the hash, so ignore the passed salt
            final boolean isValid = BCrypt.checkpw(value, hash);
            LOGGER.debug("verify() - hash: '{}', value: '{}', isValid: {}", hash, value, isValid);
            return isValid;
        }
    }

    @Override
    public HashAlgorithm getType() {
        return HashAlgorithm.BCRYPT;
    }
}
