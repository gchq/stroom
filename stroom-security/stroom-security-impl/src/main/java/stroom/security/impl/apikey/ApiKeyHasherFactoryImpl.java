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


import stroom.security.api.HashFunction;
import stroom.security.api.HashFunctionFactory;
import stroom.security.shared.HashAlgorithm;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class ApiKeyHasherFactoryImpl implements ApiKeyHasherFactory {

    private final HashFunctionFactory hashFunctionFactory;
    private final Map<HashAlgorithm, ApiKeyHasher> apiKeyHasherMap;

    @Inject
    public ApiKeyHasherFactoryImpl(final HashFunctionFactory hashFunctionFactory) {
        this.hashFunctionFactory = hashFunctionFactory;
        this.apiKeyHasherMap = Arrays.stream(HashAlgorithm.values())
                .map(hashFunctionFactory::getHashFunction)
                .map(ApiKeyHasherAdapter::new)
                .collect(Collectors.toMap(ApiKeyHasher::getType, Function.identity()));
    }

    /// Get a ApiKeyHasher for the given HashAlgorithm
    @Override
    public ApiKeyHasher getApiKeyHasher(final HashAlgorithm hashAlgorithm) {
        final ApiKeyHasher apiKeyHasher = apiKeyHasherMap.get(
                Objects.requireNonNull(hashAlgorithm, "hashAlgorithm is required"));
        Objects.requireNonNull(apiKeyHasher, () -> "No ApiKeyHasher implementation for algorithm " + hashAlgorithm);
        return apiKeyHasher;
    }


    // --------------------------------------------------------------------------------


    /// Wraps a {@link HashFunction} to simplify the interface as we don't care about salt for API keys
    /// due to their entropy.
    private static final class ApiKeyHasherAdapter implements ApiKeyHasher {

        private final HashFunction hashFunction;

        private ApiKeyHasherAdapter(final HashFunction hashFunction) {
            this.hashFunction = Objects.requireNonNull(hashFunction);
        }

        @Override
        public String hash(final String apiKeyStr) {
            return hashFunction.hash(Objects.requireNonNull(apiKeyStr, "apiKeyStr is required").trim());
        }

        @Override
        public boolean verify(final String apiKeyStr, final String hash) {
            return hashFunction.verify(
                    Objects.requireNonNull(apiKeyStr, "apiKeyStr is required").trim(),
                    Objects.requireNonNull(hash).trim());
        }

        @Override
        public HashAlgorithm getType() {
            return hashFunction.getType();
        }
    }
}
