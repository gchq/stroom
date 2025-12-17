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

import java.util.Objects;

interface DataFeedKeyHasher {

    /**
     * Generate the hash of a datafeed key.
     *
     * @param dataFeedKey The datafeed key to generate a hash for
     * @return The hash and the salt used.
     */
    HashOutput hash(String dataFeedKey);

    /**
     * Verify a dataFeedKey against its hash, and if provided include its salt.
     *
     * @param dataFeedKey The datafeed key
     * @param hash        The hash to verify against.
     * @param salt        An optional salt to include in the verification
     * @return True if verification is successful
     */
    default boolean verify(final String dataFeedKey, final String hash, final String salt) {
        final HashOutput hashOutput = hash(Objects.requireNonNull(dataFeedKey));
        return Objects.equals(Objects.requireNonNull(hash), hashOutput.hash);
    }

    /**
     * @return The enum representing this hash algorithm.
     */
    DataFeedKeyHashAlgorithm getAlgorithm();


    // --------------------------------------------------------------------------------


    record HashOutput(String hash, String salt) {

    }
}
