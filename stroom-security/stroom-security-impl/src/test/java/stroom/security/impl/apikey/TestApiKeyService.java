package stroom.security.impl.apikey;

import stroom.cache.impl.CacheManagerImpl;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.apikey.ApiKeyGenerator.ApiKeyParts;
import stroom.security.impl.apikey.ApiKeyService.DuplicateHashException;
import stroom.security.impl.apikey.ApiKeyService.DuplicatePrefixException;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.CreateHashedApiKeyResponse;
import stroom.security.shared.HashedApiKey;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.SimpleUserName;
import stroom.util.shared.UserName;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;

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
    void create_success() throws DuplicateHashException, DuplicatePrefixException {
        final UserName owner = SimpleUserName.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                owner,
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli(),
                "key1",
                "some comments",
                true);

        final HashedApiKey hashedApiKey = HashedApiKey.builder()
                .build();

        final AtomicReference<String> hashRef = new AtomicReference<>();
        Mockito.doAnswer(
                        invocation -> {
                            final CreateHashedApiKeyRequest request2 = invocation.getArgument(0);
                            final HashedApiKeyParts parts = invocation.getArgument(1);
                            hashRef.set(parts.apiKeyHash());
                            return hashedApiKey;
                        })
                .when(mockApiKeyDao)
                .create(Mockito.any(), Mockito.any());

        final CreateHashedApiKeyResponse response = apiKeyService.create(request);

        assertThat(response.getHashedApiKey())
                .isEqualTo(hashedApiKey);

        final String apiKey = response.getApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKey);

        // Make sure hash passed to dao matched the hash of the returned key
        assertThat(hash)
                .isEqualTo(hashRef.get());
    }

    @Test
    void create_noExpireTime() throws DuplicateHashException, DuplicatePrefixException {
        final UserName owner = SimpleUserName.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                owner,
                null,
                "key1",
                "some comments",
                true);

        final HashedApiKey hashedApiKey = HashedApiKey.builder()
                .build();


        final AtomicReference<String> hashRef = new AtomicReference<>();
        final AtomicReference<Long> expireTimeRef = new AtomicReference<>();
        Mockito.doAnswer(
                        invocation -> {
                            final CreateHashedApiKeyRequest request2 = invocation.getArgument(0);
                            final HashedApiKeyParts parts = invocation.getArgument(1);
                            hashRef.set(parts.apiKeyHash());
                            expireTimeRef.set(request2.getExpireTimeMs());
                            return hashedApiKey;
                        })
                .when(mockApiKeyDao)
                .create(Mockito.any(), Mockito.any());

        final CreateHashedApiKeyResponse response = apiKeyService.create(request);

        assertThat(response.getHashedApiKey())
                .isEqualTo(hashedApiKey);
        final Duration maxExpireAge = new AuthenticationConfig().getMaxApiKeyExpiryAge().getDuration();
        assertThat(expireTimeRef.get())
                .isCloseTo(Instant.now().plus(maxExpireAge).toEpochMilli(), Percentage.withPercentage(5));

        final String apiKey = response.getApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKey);

        // Make sure hash passed to dao matched the hash of the returned key
        assertThat(hash)
                .isEqualTo(hashRef.get());
    }

    @Test
    void create_expireTimeTooBig() throws DuplicateHashException, DuplicatePrefixException {
        final UserName owner = SimpleUserName.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();
        final Instant now = Instant.now();
        final Duration maxExpireAge = new AuthenticationConfig().getMaxApiKeyExpiryAge().getDuration();
        final Instant maxExpireTime = now.plus(maxExpireAge);
        // Over the limit
        final Instant expireTime = maxExpireTime.plus(Duration.ofDays(10));

        CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                owner,
                expireTime.toEpochMilli(),
                "key1",
                "some comments",
                true);

        final HashedApiKey hashedApiKey = HashedApiKey.builder()
                .build();

        Assertions.assertThatThrownBy(() ->
                        apiKeyService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("is after");
    }

    @Test
    void create_hashClash() throws DuplicateHashException, DuplicatePrefixException {
        final UserName owner = SimpleUserName.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                owner,
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli(),
                "key1",
                "some comments",
                true);

        final HashedApiKey hashedApiKey = HashedApiKey.builder()
                .build();

        final AtomicReference<String> hashRef = new AtomicReference<>();
        final AtomicInteger iteration = new AtomicInteger();
        Mockito.doAnswer(
                        invocation -> {
                            if (iteration.incrementAndGet() <= 3) {
                                throw new DuplicateHashException("dup hash", new RuntimeException("foo"));
                            }

                            final CreateHashedApiKeyRequest request2 = invocation.getArgument(0);
                            final HashedApiKeyParts parts = invocation.getArgument(1);
                            hashRef.set(parts.apiKeyHash());
                            return hashedApiKey;
                        })
                .when(mockApiKeyDao)
                .create(Mockito.any(), Mockito.any());

        final CreateHashedApiKeyResponse response = apiKeyService.create(request);

        assertThat(response.getHashedApiKey())
                .isEqualTo(hashedApiKey);

        final String apiKey = response.getApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKey);

        // Make sure hash passed to dao matched the hash of the returned key
        assertThat(hash)
                .isEqualTo(hashRef.get());
    }

    @Test
    void create_prefixClash() throws DuplicateHashException, DuplicatePrefixException {
        final UserName owner = SimpleUserName.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                owner,
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli(),
                "key1",
                "some comments",
                true);

        final HashedApiKey hashedApiKey = HashedApiKey.builder()
                .build();

        final AtomicReference<String> hashRef = new AtomicReference<>();
        final AtomicInteger iteration = new AtomicInteger();
        Mockito.doAnswer(
                        invocation -> {
                            if (iteration.incrementAndGet() <= 3) {
                                throw new DuplicatePrefixException("dup prefix", new RuntimeException("foo"));
                            }

                            final CreateHashedApiKeyRequest request2 = invocation.getArgument(0);
                            final HashedApiKeyParts parts = invocation.getArgument(1);
                            hashRef.set(parts.apiKeyHash());
                            return hashedApiKey;
                        })
                .when(mockApiKeyDao)
                .create(Mockito.any(), Mockito.any());

        final CreateHashedApiKeyResponse response = apiKeyService.create(request);

        assertThat(response.getHashedApiKey())
                .isEqualTo(hashedApiKey);

        final String apiKey = response.getApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKey);

        // Make sure hash passed to dao matched the hash of the returned key
        assertThat(hash)
                .isEqualTo(hashRef.get());
    }

    @Test
    void fetchVerifiedIdentityFromRequest_success() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);
        final HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);

        final UserName owner = SimpleUserName.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        Optional<HashedApiKey> optValidApiKey = Optional.of(
                HashedApiKey.builder()
                        .withOwner(owner)
                        .withApiKeyHash(hash)
                        .build());

        Mockito.when(mockApiKeyDao.fetchValidApiKeyByHash(Mockito.anyString()))
                .thenReturn(optValidApiKey);
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
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);

        final UserName owner = SimpleUserName.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        Optional<HashedApiKey> optValidApiKey = Optional.of(
                HashedApiKey.builder()
                        .withOwner(owner)
                        .withApiKeyHash(hash)
                        .build());

        Mockito.when(mockApiKeyDao.fetchValidApiKeyByHash(Mockito.anyString()))
                .thenReturn(optValidApiKey);

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
    void fetchVerifiedIdentity_noValid() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final Optional<HashedApiKey> optApiKey = Optional.empty();

        Mockito.when(mockApiKeyDao.fetchValidApiKeyByHash(Mockito.anyString()))
                .thenReturn(optApiKey);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(apiKeyStr);

        assertThat(opUserIdentity)
                .isEmpty();
    }

    @Disabled // manual only, to see how many prefix/hash clashes we get for 1mil api keys (answer: <10 ish)
    @Test
    void testHashAndPrefixClash() {
        final int iterations = 1_000_000;
        final Set<String> prefixes = new ConcurrentSkipListSet<>();
        final Set<String> hashes = new ConcurrentSkipListSet<>();

        final ThreadLocal<ApiKeyGenerator> apiKeyGeneratorThreadLocal = ThreadLocal.withInitial(ApiKeyGenerator::new);
        final LongAdder clashCount = new LongAdder();

        IntStream.range(0, iterations)
                .parallel()
                .forEach(i -> {
                    final ApiKeyGenerator apiKeyGenerator = apiKeyGeneratorThreadLocal.get();
                    final String apiKey = apiKeyGenerator.generateRandomApiKey();
                    final String hash = apiKeyService.computeApiKeyHash(apiKey);
                    final ApiKeyParts apiKeyParts = ApiKeyParts.fromApiKey(apiKey);
                    final String prefix = apiKeyParts.asPrefix();
                    if (prefixes.contains(prefix) || hashes.contains(hash)) {
                        clashCount.increment();
                    }
                    prefixes.add(prefix);
                    hashes.add(hash);
                });

        LOGGER.info("clashCount: {}, prefixes: {}, hashes: {}", clashCount, prefixes.size(), hashes.size());
    }
}
