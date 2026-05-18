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
import stroom.util.string.StringUtil;

import jakarta.inject.Singleton;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.Argon2Parameters.Builder;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Objects;

@Singleton // For thread safe SecureRandom
class Argon2DataFeedKeyHasher implements DataFeedKeyHasher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Argon2DataFeedKeyHasher.class);

    // WARNING!!!
    // Do not change any of these otherwise it will break hash verification of existing
    // keys. If you want to tune it, make a new DataFeedKeyHasher impl with a new getType()
    // 48, 2, 65_536, 1 => ~90ms per hash
    private static final int HASH_LENGTH = 48;
    private static final int ITERATIONS = 2;
    private static final int MEMORY_KB = 65_536;
    private static final int PARALLELISM = 1;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateSalt() {
        final String salt = StringUtil.createRandomCode(
                secureRandom,
                32,
                StringUtil.ALLOWED_CHARS_BASE_58_STYLE);
        LOGGER.debug("generateSalt() - salt: '{}'", salt);
        return salt;
    }

    public HashOutput hash(final String dataFeedKey, final String salt) {
        Objects.requireNonNull(dataFeedKey);
        final Builder builder = new Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ITERATIONS)
                .withMemoryAsKB(MEMORY_KB)
                .withParallelism(PARALLELISM);
        if (NullSafe.isNonEmptyString(salt)) {
            builder.withSalt(salt.getBytes(StandardCharsets.UTF_8));
        }
        final Argon2Parameters params = builder.build();

        final Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        final byte[] result = new byte[HASH_LENGTH];
        generator.generateBytes(
                dataFeedKey.trim().getBytes(StandardCharsets.UTF_8),
                result,
                0,
                result.length);

        // Base58 is a bit less nasty than base64 and widely supported in other languages
        // due to use in bitcoin.
        final String hash = Hex.encodeHexString(result);
        LOGGER.debug("hash() - salt: '{}', hash: '{}', dataFeedKey: '{}'", salt, hash, dataFeedKey);
        return new HashOutput(hash, salt);
    }

    @Override
    public boolean verify(final String dataFeedKey, final String hash, final String salt) {
        Objects.requireNonNull(dataFeedKey);
        Objects.requireNonNull(hash);
        final HashOutput hashOutput = hash(dataFeedKey, salt);
        final boolean isValid = Objects.equals(hash, hashOutput.hash());
        LOGGER.debug("verify() - salt: '{}', hash: '{}', dataFeedKey: '{}', isValid: {}",
                salt, hash, dataFeedKey, isValid);
        return isValid;
    }

    @Override
    public DataFeedKeyHashAlgorithm getAlgorithm() {
        return DataFeedKeyHashAlgorithm.ARGON2;
    }
}
