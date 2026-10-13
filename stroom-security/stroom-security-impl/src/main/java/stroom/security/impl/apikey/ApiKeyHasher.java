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

package stroom.security.impl.apikey;


import stroom.security.shared.HashAlgorithm;

import java.util.Objects;

/**
 * These hashers were written before {@link stroom.security.api.HashFunction} and differ
 * slightly (even though they both share the same, so they can stay here just for api key use.
 */
interface ApiKeyHasher {

    String hash(String apiKeyStr);

    default boolean verify(final String apiKeyStr, final String hash) {
        final String computedHash = hash(Objects.requireNonNull(apiKeyStr));
        return Objects.equals(Objects.requireNonNull(hash), computedHash);
    }

    HashAlgorithm getType();
}
