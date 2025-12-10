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

import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestApiKeyGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestApiKeyGenerator.class);

    private final ApiKeyGenerator apiKeyGenerator = new ApiKeyGenerator();

    @TestFactory
    Stream<DynamicTest> testVerifyApiKeyFormatAndHash() {
        final String validKey = apiKeyGenerator.generateRandomApiKey();
        // Will fail regex
        final String inValidKey1 = validKey.replace(ApiKeyGenerator.API_KEY_STATIC_PREFIX, "sak_?");

        final String[] parts = validKey.split(ApiKeyGenerator.API_KEY_SEPARATOR);
        // Reverse the hash part so it is bad
        parts[1] = new StringBuilder(parts[1]).reverse().toString();
        final String inValidKey2 = String.join(ApiKeyGenerator.API_KEY_SEPARATOR, parts);

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(apiKeyGenerator::isApiKey)
                .withSimpleEqualityAssertion()
                .addCase(null, false)
                .addCase("", false)
                .addCase(" ", false)
                .addCase("foo", false)
                .addCase(inValidKey1, false)
                .addCase(inValidKey2, false)
                .addCase("foo" + validKey, false)
                .addCase(validKey, true)
                .addCase(" " + validKey, true)
                .addCase(validKey + " ", true)
                .addCase(" " + validKey + " ", true)
                .build();
    }

    @Test
    void testGenerateApiKey() {
        final List<String> apiKeys = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final String apiKey = apiKeyGenerator.generateRandomApiKey();
//            LOGGER.info("apiKey: '{}'", apiKey);

            assertThat(apiKey)
                    .startsWith(ApiKeyGenerator.API_KEY_TYPE + ApiKeyGenerator.API_KEY_SEPARATOR);

            assertThat(apiKeyGenerator.isApiKey(apiKey))
                    .isTrue();
            apiKeys.add(apiKey);
        }

        LOGGER.info("keys:\n{}", String.join("\n", apiKeys));
    }

    @Disabled // manual only, to see how many prefix clashes we get for 1mil api keys
    @Test
    void testPrefixClash() {
        final int iterations = 1_000_000;
        final Map<String, String> prefixToApiKeyMap = new ConcurrentHashMap<>(iterations);
        final Pattern splitPattern = Pattern.compile(ApiKeyGenerator.API_KEY_SEPARATOR);
        final ThreadLocal<ApiKeyGenerator> apiKeyGeneratorThreadLocal = ThreadLocal.withInitial(ApiKeyGenerator::new);
        final LongAdder clashCount = new LongAdder();

        IntStream.range(0, iterations)
                .parallel()
                .forEach(i -> {
                    final ApiKeyGenerator apiKeyGenerator = apiKeyGeneratorThreadLocal.get();
                    final String apiKey = apiKeyGenerator.generateRandomApiKey();
                    final String[] parts = splitPattern.split(apiKey);
                    final String prefixHash = parts[1];
                    if (prefixToApiKeyMap.containsKey(prefixHash)) {
                        clashCount.increment();
                    }
                    prefixToApiKeyMap.put(prefixHash, apiKey);
                });
        LOGGER.info("clashCount: {}", clashCount);
    }

    @Test
    void testLength() {
        for (int i = 0; i < 1_000; i++) {
            final String apiKey = apiKeyGenerator.generateRandomApiKey();
            assertThat(apiKey)
                    .hasSize(ApiKeyGenerator.API_KEY_TOTAL_LENGTH);
        }
    }

    @Test
    void testPrefixesMatch_true() {
        final String apiKey1 = apiKeyGenerator.generateRandomApiKey();
        final String prefix = ApiKeyGenerator.extractPrefixPart(apiKey1);
        final String apiKey2 = prefix + "foo";

        assertThat(ApiKeyGenerator.prefixesMatch(apiKey1, apiKey2))
                .isTrue();
    }

    @Test
    void testPrefixesMatch_true2() {
        final String apiKey1 = apiKeyGenerator.generateRandomApiKey();
        final String prefix = ApiKeyGenerator.extractPrefixPart(apiKey1);

        assertThat(ApiKeyGenerator.prefixesMatch(apiKey1, prefix))
                .isTrue();
    }

    @Test
    void testPrefixesMatch_false() {
        final String apiKey1 = apiKeyGenerator.generateRandomApiKey();
        final String apiKey2 = apiKeyGenerator.generateRandomApiKey();

        assertThat(ApiKeyGenerator.prefixesMatch(apiKey1, apiKey2))
                .isFalse();
    }
}
