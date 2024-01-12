package stroom.security.impl.apikey;

import stroom.cache.impl.CacheManagerImpl;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.ApiKey;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.SimpleUserName;
import stroom.util.shared.UserName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestApiKeyService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestApiKeyService.class);

    private final SecurityContext securityContext = new MockSecurityContext();
    @Mock
    private ApiKeyDao mockApiKeyDao;

    ApiKeyGenerator apiKeyGenerator = new ApiKeyGenerator();
    ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(
                mockApiKeyDao,
                securityContext,
                apiKeyGenerator,
                new CacheManagerImpl(),
                AuthenticationConfig::new);
    }

    @Test
    void fetchVerifiedIdentityFromRequest_success() {
        final String salt = "mySalt";
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String saltedApiKeyHash = apiKeyService.computeApiKeyHash(apiKeyStr, salt);
        final HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);

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
        Mockito.when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(apiKeyStr);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(mockRequest);

        assertThat(opUserIdentity)
                .isNotEmpty();
        final UserIdentity userIdentity = opUserIdentity.get();
        assertThat(userIdentity.getSubjectId())
                .isEqualTo(owner.getSubjectId());
        assertThat(userIdentity.getDisplayName())
                .isEqualTo(owner.getDisplayName());
    }

    @Test
    void fetchVerifiedIdentityFromRequest_notApiKey() {
        final String badApiKeyStr = "XXX_" + apiKeyGenerator.generateRandomApiKey();
        final HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);

        // Return something that is not an API
        Mockito.when(mockRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn(badApiKeyStr);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(mockRequest);

        assertThat(opUserIdentity)
                .isEmpty();
    }

    @Test
    void fetchVerifiedIdentity_success() {
        final String salt = "mySalt";
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
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
        final String apiKeyStr1 = apiKeyGenerator.generateRandomApiKey();
        final String saltedApiKeyHash1 = apiKeyService.computeApiKeyHash(apiKeyStr1, salt1);

        final UserName owner1 = SimpleUserName.builder()
                .uuid("myUuid1")
                .subjectId("mySubjectId1")
                .displayName("myDisplayName1")
                .build();

        final String salt2 = "mySalt2";
        final String apiKeyStr2 = apiKeyGenerator.generateRandomApiKey();
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
        final String apiKeyStr1 = apiKeyGenerator.generateRandomApiKey();
        final String saltedApiKeyHash1 = apiKeyService.computeApiKeyHash(apiKeyStr1, salt1);

        final UserName owner1 = SimpleUserName.builder()
                .uuid("myUuid1")
                .subjectId("mySubjectId1")
                .displayName("myDisplayName1")
                .build();

        final String salt2 = "mySalt2";
        final String apiKeyStr2 = apiKeyGenerator.generateRandomApiKey();
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
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();

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
}
