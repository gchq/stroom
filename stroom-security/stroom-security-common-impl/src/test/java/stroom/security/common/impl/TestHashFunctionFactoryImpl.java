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
import stroom.security.shared.HashAlgorithm;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestHashFunctionFactoryImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestHashFunctionFactoryImpl.class);

    @Test
    void test() {
        final HashFunctionFactoryImpl hashFunctionFactory = new HashFunctionFactoryImpl();
        for (final HashAlgorithm hashAlgorithm : HashAlgorithm.values()) {

            testHashAlgorithm(hashFunctionFactory, hashAlgorithm);
        }
    }

    private void testHashAlgorithm(final HashFunctionFactoryImpl hashFunctionFactory,
                                   final HashAlgorithm hashAlgorithm) {

        final HashFunction hashFunction = hashFunctionFactory.getHashFunction(hashAlgorithm);
        final String salt = hashFunction.generateSalt();

        final String hash1 = hashFunction.hash("foo", salt);
        final String hash2 = hashFunction.hash("foo");
        final String hash3 = hashFunction.hash("foo", salt);

        LOGGER.debug("Testing hashAlgorithm {}, salt: {}, hash: {}", hashAlgorithm, salt, hash1);

        assertThat(hash2)
                .isNotEqualTo(hash1);
        assertThat(hash3)
                .isEqualTo(hash1);

        assertThat(hashFunction.verify("foo", hash1))
                .isFalse();
        assertThat(hashFunction.verify("foo", hash1, salt))
                .isTrue();
        // Salt is encoded in the hash with bcrypt, so bad salt is ignored
        if (hashAlgorithm != HashAlgorithm.BCRYPT) {
            assertThat(hashFunction.verify("foo", hash1, "bad salt"))
                    .isFalse();
        }
        assertThat(hashFunction.verify("fooX", hash1, salt))
                .isFalse();
    }
}
