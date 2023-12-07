package stroom.security.impl;

import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
class TestApiKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestApiKeyService.class);

    @Mock
    private ApiKeyDao mockApiKeyDao;

    ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(mockApiKeyDao);
    }

    @Test
    void verifyHashPart_false() {




    }

    @TestFactory
    Stream<DynamicTest> testVerifyApiKeyFormatAndHash() {
        final String validKey = apiKeyService.generateRandomApiKey();
        // Will fail regex
        final String inValidKey1 = validKey.replace("sak_", "sak_?");

        final String[] parts = validKey.split("_");
        // Reverse the hash part so it is bad
        parts[2] = new StringBuilder(parts[2]).reverse().toString();
        final String inValidKey2 = String.join("_", parts);

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(boolean.class)
                .withSingleArgTestFunction(apiKeyService::isApiKey)
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
        for (int i = 0; i < 10; i++) {
            final String apiKey = apiKeyService.generateRandomApiKey();
            LOGGER.info("apiKey: '{}'", apiKey);

            Assertions.assertThat(apiKey)
                    .startsWith("sak_");

            Assertions.assertThat(apiKeyService.isApiKey(apiKey))
                    .isTrue();
        }
    }

    @Test
    void testLength() {
        for (int i = 0; i < 1_000; i++) {
            final String apiKey = apiKeyService.generateRandomApiKey();
            Assertions.assertThat(apiKey)
                    .hasSize(ApiKeyService.API_KEY_TOTAL_LENGTH);
        }
    }
}
