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

package stroom.security.common.impl;

import stroom.security.api.HashFunction;
import stroom.security.api.HashFunctionFactory;
import stroom.security.shared.HashAlgorithm;
import stroom.util.collections.CollectionUtil;
import stroom.util.collections.CollectionUtil.DuplicateMode;
import stroom.util.string.Base58;
import stroom.util.string.StringUtil;

import jakarta.inject.Singleton;
import org.apache.commons.codec.digest.DigestUtils;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.Argon2Parameters.Builder;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
public class HashFunctionFactoryImpl implements HashFunctionFactory {

    private static final List<HashFunction> HASH_FUNCTIONS = List.of(
            new ShaThree256Hasher(),
            new ShaTwo256Hasher(),
            new BCryptHasher(),
            new Argon2Hasher(),
            new ShaTwo512Hasher());

    private static final Map<HashAlgorithm, HashFunction> HASH_FUNCTION_MAP = CollectionUtil.enumMapBy(
            HashAlgorithm.class,
            HashFunction::getType,
            DuplicateMode.THROW,
            HASH_FUNCTIONS);

    @Override
    public HashFunction getHashFunction(final HashAlgorithm hashAlgorithm) {
        final HashFunction hashFunction = HASH_FUNCTION_MAP.get(Objects.requireNonNull(hashAlgorithm));
        if (hashFunction == null) {
            throw new IllegalArgumentException("No HashFunction for " + hashAlgorithm);
        }
        return hashFunction;
    }


    // --------------------------------------------------------------------------------


    private abstract static class AbstractHashFunction implements HashFunction {

        private final SecureRandom secureRandom = new SecureRandom();

        @Override
        public String generateSalt() {
            return StringUtil.createRandomCode(secureRandom, 64);
        }

        protected String getSaltedValue(final String value, final String salt) {
            return salt != null
                    ? salt + value
                    : value;
        }
    }


    // --------------------------------------------------------------------------------


    private static class ShaThree256Hasher extends AbstractHashFunction {

        @Override
        public String hash(final String value, final String salt) {
            final String saltedVal = getSaltedValue(value, salt);
            return Base58.encode(DigestUtils.sha3_256(saltedVal));
        }

        @Override
        public HashAlgorithm getType() {
            return HashAlgorithm.SHA3_256;
        }
    }


    // --------------------------------------------------------------------------------


    private static class ShaTwo256Hasher extends AbstractHashFunction {

        @Override
        public String hash(final String value, final String salt) {
            final String saltedVal = getSaltedValue(value, salt);
            return Base58.encode(DigestUtils.sha256(saltedVal));
        }

        @Override
        public HashAlgorithm getType() {
            return HashAlgorithm.SHA2_256;
        }
    }


    // --------------------------------------------------------------------------------


    private static class ShaTwo512Hasher extends AbstractHashFunction {

        @Override
        public String hash(final String value, final String salt) {
            final String saltedVal = getSaltedValue(value, salt);
            return Base58.encode(DigestUtils.sha512(saltedVal));
        }

        @Override
        public HashAlgorithm getType() {
            return HashAlgorithm.SHA2_512;
        }
    }


    // --------------------------------------------------------------------------------


    private static class BCryptHasher implements HashFunction {

        @Override
        public String generateSalt() {
            return BCrypt.gensalt();
        }

        @Override
        public String hash(final String value, final String salt) {
            return BCrypt.hashpw(
                    Objects.requireNonNull(value),
                    Objects.requireNonNullElseGet(salt, BCrypt::gensalt));
        }

        @Override
        public boolean verify(final String value,
                              final String hash,
                              final String ignoredSalt) {
            if (value == null) {
                return false;
            } else {
                // Salt is encoded in the hash, so ignore the passed salt
                return BCrypt.checkpw(value, hash);
            }
        }

        @Override
        public HashAlgorithm getType() {
            return HashAlgorithm.BCRYPT;
        }
    }


    // --------------------------------------------------------------------------------


    private static class Argon2Hasher extends AbstractHashFunction {

        private static final Argon2Parameters NO_SALT_PARAMS = buildParameters(null);

        // WARNING!!!
        // Do not change any of these otherwise it will break hash verification of existing
        // keys. If you want to tune it, make a new ApiKeyHasher impl with a new getType()
        // 48, 2, 65_536, 1 => ~90ms per hash
        private static final int HASH_LENGTH = 48;
        private static final int ITERATIONS = 2;
        private static final int MEMORY_KB = 65_536;
        private static final int PARALLELISM = 1;

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
            final String saltedVal = salt != null
                    ? salt + value
                    : value;
            final Argon2BytesGenerator generate = new Argon2BytesGenerator();
            generate.init(buildParameters(salt));
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
}
