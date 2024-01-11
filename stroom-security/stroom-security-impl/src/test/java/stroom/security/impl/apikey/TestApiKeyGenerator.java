package stroom.security.impl.apikey;

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
        final String inValidKey1 = validKey.replace("sak_", "sak_?");

        final String[] parts = validKey.split("_");
        // Reverse the hash part so it is bad
        parts[1] = new StringBuilder(parts[1]).reverse().toString();
        final String inValidKey2 = String.join("_", parts);

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

    @Disabled // manual only
    @Test
    void testPrefixClash() {
        final int iterations = 1_000_000;
        final Map<String, String> prefixToApiKeyMap = new ConcurrentHashMap<>(iterations);
        final Pattern splitPattern = Pattern.compile("_");
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
}
