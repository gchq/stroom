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

package stroom.security.api;

import stroom.security.shared.HashAlgorithm;

import java.util.Objects;

public interface HashFunction {

    /**
     * @return A random salt
     */
    String generateSalt();

    /**
     * Generate a hash of value without using a salt.
     * Some implementations require a salt, in which case a salt may
     * be randomly generated and encoded in the hash.
     * Unless the hash function has its own method of encoding, the hash will
     * be base58 encoded.
     *
     * @return The hash
     */
    default String hash(final String value) {
        return hash(value, null);
    }

    /**
     * Generate a hash of value using the provided salt.
     * Unless the hash function has its own method of encoding, the hash will
     * be base58 encoded.
     *
     * @return The hash
     */
    String hash(String value, String salt);

    /**
     * Verify a value against a hash without using a salt (unless one has been
     * encoded in the hash).
     *
     * @return True if value hashes to the same hash as hash.
     */
    default boolean verify(final String value, final String hash) {
        final String computedHash = hash(Objects.requireNonNull(value), null);
        return Objects.equals(Objects.requireNonNull(hash), computedHash);
    }

    /**
     * Verify a value against a hash using the provided salt (unless one has been
     * encoded in the hash).
     *
     * @return True if value hashes to the same hash as hash.
     */
    default boolean verify(final String value, final String hash, final String salt) {
        final String computedHash = hash(
                Objects.requireNonNull(value),
                Objects.requireNonNull(salt));
        return Objects.equals(Objects.requireNonNull(hash), computedHash);
    }

    /**
     * @return The type of this hash function that uniquely identifies it when multiple
     * hash functions are in use.
     */
    HashAlgorithm getType();
}
