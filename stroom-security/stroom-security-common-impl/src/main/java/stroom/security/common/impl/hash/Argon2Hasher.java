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


import stroom.security.shared.HashAlgorithm;
import stroom.util.string.Base58;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.Argon2Parameters.Builder;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Objects;

class Argon2Hasher extends AbstractHashFunction {

    private static final Argon2Parameters NO_SALT_PARAMS = buildParameters(null);

    // WARNING!!!
    // Do not change any of these otherwise it will break hash verification of existing
    // keys. If you want to tune it, make a new ApiKeyHasher impl with a new getType()
    // 48, 2, 65_536, 1 => ~90ms per hash
    private static final int HASH_LENGTH = 48;
    private static final int ITERATIONS = 2;
    private static final int MEMORY_KB = 65_536;
    private static final int PARALLELISM = 1;

    public Argon2Hasher() {
    }

    public Argon2Hasher(final SecureRandom secureRandom) {
        super(secureRandom);
    }

    private static Argon2Parameters buildParameters(final String salt) {
        final Builder builder = new Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ITERATIONS)
                .withMemoryAsKB(MEMORY_KB)
                .withParallelism(PARALLELISM);

        if (salt != null) {
            builder.withSalt(salt.getBytes(StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    private static Argon2Parameters getParameters(final String salt) {
        if (salt == null) {
            return NO_SALT_PARAMS;
        } else {
            return buildParameters(salt);
        }
    }

    @Override
    public String hash(final String value, final String salt) {
        Objects.requireNonNull(value);
        final String saltedVal = getSaltedValue(value, salt);
        final Argon2BytesGenerator generate = new Argon2BytesGenerator();
        generate.init(getParameters(salt));
        final byte[] result = new byte[HASH_LENGTH];
        generate.generateBytes(
                saltedVal.getBytes(StandardCharsets.UTF_8),
                result,
                0,
                result.length);

        // Base58 is a bit less nasty than base64 and widely supported in other languages
        // due to use in bitcoin.
        return Base58.encode(result);
    }

    @Override
    public HashAlgorithm getType() {
        return HashAlgorithm.ARGON_2;
    }
}
