package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.ApiKey;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.SimpleUserName;
import stroom.util.shared.UserName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestApiKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestApiKeyService.class);

    private final SecurityContext securityContext = new MockSecurityContext();
    @Mock
    private ApiKeyDao mockApiKeyDao;

    ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(mockApiKeyDao, securityContext);
    }

    @Test
    void fetchVerifiedIdentity_success() {
        final String salt = "mySalt";
        final String apiKeyStr = apiKeyService.generateRandomApiKey();
        final String saltedApiKeyHash = apiKeyService.computeApiKeyHash(apiKeyStr, salt);

        final UserName owner = SimpleUserName.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        List<ApiKey> validApiKeys = List.of(
                ApiKey.builder()
                        .withOwner(owner)
                        .withApiKeySalt(salt)
                        .withApiKeyHash(saltedApiKeyHash)
                        .build());

        Mockito.when(mockApiKeyDao.fetchValidApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(validApiKeys);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(apiKeyStr);

        assertThat(opUserIdentity)
                .isNotEmpty();
        final UserIdentity userIdentity = opUserIdentity.get();
        assertThat(userIdentity.getSubjectId())
                .isEqualTo(owner.getSubjectId());
        assertThat(userIdentity.getDisplayName())
                .isEqualTo(owner.getDisplayName());
    }

    @Test
    void fetchVerifiedIdentity_multipleKeys_success() {
        final String salt1 = "mySalt1";
        final String apiKeyStr1 = apiKeyService.generateRandomApiKey();
        final String saltedApiKeyHash1 = apiKeyService.computeApiKeyHash(apiKeyStr1, salt1);

        final UserName owner1 = SimpleUserName.builder()
                .uuid("myUuid1")
                .subjectId("mySubjectId1")
                .displayName("myDisplayName1")
                .build();

        final String salt2 = "mySalt2";
        final String apiKeyStr2 = apiKeyService.generateRandomApiKey();
        final String saltedApiKeyHash2 = apiKeyService.computeApiKeyHash(apiKeyStr2, salt2);

        final UserName owner2 = SimpleUserName.builder()
                .uuid("myUuid2")
                .subjectId("mySubjectId2")
                .displayName("myDisplayName2")
                .build();

        List<ApiKey> validApiKeys = List.of(
                ApiKey.builder()
                        .withOwner(owner1)
                        .withApiKeySalt(salt1)
                        .withApiKeyHash(saltedApiKeyHash1)
                        .build(),
                ApiKey.builder()
                        .withOwner(owner2)
                        .withApiKeySalt(salt2)
                        .withApiKeyHash(saltedApiKeyHash2)
                        .build());

        Mockito.when(mockApiKeyDao.fetchValidApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(validApiKeys);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(apiKeyStr2);

        assertThat(opUserIdentity)
                .isNotEmpty();
        final UserIdentity userIdentity = opUserIdentity.get();
        assertThat(userIdentity.getSubjectId())
                .isEqualTo(owner2.getSubjectId());
        assertThat(userIdentity.getDisplayName())
                .isEqualTo(owner2.getDisplayName());
    }

    @Test
    void fetchVerifiedIdentity_multipleKeys_hashMismatch() {
        final String salt1 = "mySalt1";
        final String apiKeyStr1 = apiKeyService.generateRandomApiKey();
        final String saltedApiKeyHash1 = apiKeyService.computeApiKeyHash(apiKeyStr1, salt1);

        final UserName owner1 = SimpleUserName.builder()
                .uuid("myUuid1")
                .subjectId("mySubjectId1")
                .displayName("myDisplayName1")
                .build();

        final String salt2 = "mySalt2";
        final String apiKeyStr2 = apiKeyService.generateRandomApiKey();
        final String saltedApiKeyHash2 = apiKeyService.computeApiKeyHash(apiKeyStr2, salt2);

        final UserName owner2 = SimpleUserName.builder()
                .uuid("myUuid2")
                .subjectId("mySubjectId2")
                .displayName("myDisplayName2")
                .build();

        List<ApiKey> validApiKeys = List.of(
                ApiKey.builder()
                        .withOwner(owner1)
                        .withApiKeySalt(salt1)
                        .withApiKeyHash(saltedApiKeyHash1)
                        .build(),
                ApiKey.builder()
                        .withOwner(owner2)
                        .withApiKeySalt(salt2)
                        .withApiKeyHash("bad hash")
                        .build());

        Mockito.when(mockApiKeyDao.fetchValidApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(validApiKeys);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(apiKeyStr2);

        assertThat(opUserIdentity)
                .isEmpty();
    }

    @Test
    void fetchVerifiedIdentity_noValid() {
        final String apiKeyStr = apiKeyService.generateRandomApiKey();

        final UserName owner = SimpleUserName.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        List<ApiKey> validApiKeys = Collections.emptyList();

        Mockito.when(mockApiKeyDao.fetchValidApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(validApiKeys);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(apiKeyStr);

        assertThat(opUserIdentity)
                .isEmpty();
    }

    @TestFactory
    Stream<DynamicTest> testVerifyApiKeyFormatAndHash() {
        final String validKey = apiKeyService.generateRandomApiKey();
        // Will fail regex
        final String inValidKey1 = validKey.replace("sak_", "sak_?");

        final String[] parts = validKey.split("_");
        // Reverse the hash part so it is bad
        parts[1] = new StringBuilder(parts[1]).reverse().toString();
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

            assertThat(apiKey)
                    .startsWith(ApiKeyService.API_KEY_TYPE + ApiKeyService.API_KEY_SEPARATOR);

            assertThat(apiKeyService.isApiKey(apiKey))
                    .isTrue();
        }
    }

    @Test
    void testLength() {
        for (int i = 0; i < 1_000; i++) {
            final String apiKey = apiKeyService.generateRandomApiKey();
            assertThat(apiKey)
                    .hasSize(ApiKeyService.API_KEY_TOTAL_LENGTH);
        }
    }
}
