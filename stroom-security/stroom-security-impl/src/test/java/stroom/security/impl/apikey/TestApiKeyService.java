package stroom.security.impl.apikey;

import stroom.cache.impl.CacheManagerImpl;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.apikey.ApiKeyService.DuplicateApiKeyException;
import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.CreateHashedApiKeyResponse;
import stroom.security.shared.HashAlgorithm;
import stroom.security.shared.HashedApiKey;
import stroom.test.common.TestUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.SimpleUserName;
import stroom.util.shared.UserName;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
    void create_success() throws DuplicateApiKeyException {
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
                true,
                HashAlgorithm.SHA3_256);

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
    void create_noExpireTime() throws DuplicateApiKeyException {
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
                true,
                HashAlgorithm.SHA3_256);

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
    void create_expireTimeTooBig() throws DuplicateApiKeyException {
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
                true,
                HashAlgorithm.SHA3_256);

        Assertions.assertThatThrownBy(() ->
                        apiKeyService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("is after");
    }

    @Test
    void create_hashClash() throws DuplicateApiKeyException {
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
                true,
                HashAlgorithm.SHA3_256);

        final HashedApiKey hashedApiKey = HashedApiKey.builder()
                .build();

        final AtomicReference<String> hashRef = new AtomicReference<>();
        final AtomicInteger iteration = new AtomicInteger();
        Mockito.doAnswer(
                        invocation -> {
                            if (iteration.incrementAndGet() <= 3) {
                                throw new DuplicateApiKeyException("dup hash", new RuntimeException("foo"));
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
    void create_prefixClash() throws DuplicateApiKeyException {
        final UserName owner = SimpleUserName.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        final String name = "key-" + UUID.randomUUID().toString();
        CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                owner,
                Instant.now().plus(10, ChronoUnit.DAYS).toEpochMilli(),
                name,
                "some comments",
                true,
                HashAlgorithm.SHA3_256);

        final HashedApiKey hashedApiKey = HashedApiKey.builder()
                .build();

        final AtomicReference<String> hashRef = new AtomicReference<>();
        final AtomicInteger iteration = new AtomicInteger();
        Mockito.doAnswer(
                        invocation -> {
                            if (iteration.incrementAndGet() <= 3) {
                                throw new DuplicateApiKeyException("dup prefix", new RuntimeException("foo"));
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

        List<HashedApiKey> apiKeys = List.of(
                HashedApiKey.builder()
                        .withOwner(owner)
                        .withApiKeyHash(hash)
                        .build());

        Mockito.when(mockApiKeyDao.fetchValidApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);
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
    void fetchVerifiedIdentity_success_oneKey() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);

        final UserName owner = SimpleUserName.builder()
                .uuid("myUuid")
                .subjectId("mySubjectId")
                .displayName("myDisplayName")
                .build();

        List<HashedApiKey> apiKeys = List.of(
                HashedApiKey.builder()
                        .withOwner(owner)
                        .withApiKeyHash(hash)
                        .build());

        Mockito.when(mockApiKeyDao.fetchValidApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);

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
    void fetchVerifiedIdentity_success_multipleKeys() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);

        final UserName owner1 = SimpleUserName.builder()
                .uuid("myUuid1")
                .subjectId("mySubjectId1")
                .displayName("myDisplayName1")
                .build();
        final UserName owner2 = SimpleUserName.builder()
                .uuid("myUuid2")
                .subjectId("mySubjectId2")
                .displayName("myDisplayName2")
                .build();
        final UserName owner3 = SimpleUserName.builder()
                .uuid("myUuid3")
                .subjectId("mySubjectId3")
                .displayName("myDisplayName3")
                .build();

        List<HashedApiKey> apiKeys = List.of(
                HashedApiKey.builder()
                        .withOwner(owner1)
                        .withApiKeyHash("another hash")
                        .withHashAlgorithm(HashAlgorithm.BCRYPT)
                        .build(),
                HashedApiKey.builder()
                        .withOwner(owner2)
                        .withApiKeyHash("and another hash")
                        .withHashAlgorithm(HashAlgorithm.ARGON_2)
                        .build(),
                HashedApiKey.builder()
                        .withOwner(owner3)
                        .withApiKeyHash(hash)
                        .withHashAlgorithm(HashAlgorithm.SHA3_256)
                        .build());

        Mockito.when(mockApiKeyDao.fetchValidApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(apiKeyStr);

        assertThat(opUserIdentity)
                .isNotEmpty();
        final UserIdentity userIdentity = opUserIdentity.get();
        assertThat(userIdentity.getSubjectId())
                .isEqualTo(owner3.getSubjectId());
        assertThat(userIdentity.getDisplayName())
                .isEqualTo(owner3.getDisplayName());
    }

    @Test
    void fetchVerifiedIdentity_noValid_empty() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final List<HashedApiKey> apiKeys = Collections.emptyList();

        Mockito.when(mockApiKeyDao.fetchValidApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(apiKeyStr);

        assertThat(opUserIdentity)
                .isEmpty();
    }

    @Test
    void fetchVerifiedIdentity_noValid_multipleKeys() {
        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();
        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr);

        final UserName owner1 = SimpleUserName.builder()
                .uuid("myUuid1")
                .subjectId("mySubjectId1")
                .displayName("myDisplayName1")
                .build();
        final UserName owner2 = SimpleUserName.builder()
                .uuid("myUuid2")
                .subjectId("mySubjectId2")
                .displayName("myDisplayName2")
                .build();
        final UserName owner3 = SimpleUserName.builder()
                .uuid("myUuid3")
                .subjectId("mySubjectId3")
                .displayName("myDisplayName3")
                .build();

        List<HashedApiKey> apiKeys = List.of(
                HashedApiKey.builder()
                        .withOwner(owner1)
                        .withApiKeyHash("another hash")
                        .withHashAlgorithm(HashAlgorithm.BCRYPT)
                        .build(),
                HashedApiKey.builder()
                        .withOwner(owner2)
                        .withApiKeyHash("and another hash")
                        .withHashAlgorithm(HashAlgorithm.ARGON_2)
                        .build(),
                HashedApiKey.builder()
                        .withOwner(owner3)
                        .withApiKeyHash("and yet another hash")
                        .withHashAlgorithm(HashAlgorithm.SHA3_256)
                        .build());

        Mockito.when(mockApiKeyDao.fetchValidApiKeysByPrefix(Mockito.anyString()))
                .thenReturn(apiKeys);

        final Optional<UserIdentity> opUserIdentity = apiKeyService.fetchVerifiedIdentity(apiKeyStr);

        assertThat(opUserIdentity)
                .isEmpty();
    }

    @TestFactory
    Stream<DynamicTest> testHashAlgorithms() {
        final var builder = TestUtil.buildDynamicTestStream()
                .withInputType(HashAlgorithm.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final HashAlgorithm hashAlgorithm = testCase.getInput();
                    long millis = 0;
                    long nanos = 0;
                    int cnt = 10;
                    for (int i = 0; i < cnt; i++) {
                        final String apiKeyStr = apiKeyGenerator.generateRandomApiKey();

                        final DurationTimer timer = DurationTimer.start();
                        final String hash = apiKeyService.computeApiKeyHash(apiKeyStr, hashAlgorithm);
                        final Duration duration = timer.get();
                        millis += duration.toMillis();
                        nanos += duration.toNanos();
                        LOGGER.info("Generated {} hash {} (len: {})", hashAlgorithm, hash, hash.length());
                        final boolean isValid = apiKeyService.verifyApiKeyHash(apiKeyStr, hash, hashAlgorithm);

                        assertThat(isValid)
                                .isTrue();
                    }
                    LOGGER.info("Generated {} {} hashes at {} ms ({} ns) per hash",
                            cnt,
                            hashAlgorithm,
                            ModelStringUtil.formatCsv(millis / cnt),
                            ModelStringUtil.formatCsv(nanos / cnt));
                    return true;
                })
                .withSimpleEqualityAssertion();

        for (final HashAlgorithm hashAlgorithm : HashAlgorithm.values()) {
            builder.addCase(hashAlgorithm, true);
        }

        return builder.build();
    }

    @Disabled // manual only, to see how many hash clashes we get for 10mil api keys (answer: 0 ish)
    @Test
    void testHashClash() {
        final int iterations = 10_000_000;
        final Set<String> hashes = new ConcurrentSkipListSet<>();

        final ThreadLocal<ApiKeyGenerator> apiKeyGeneratorThreadLocal = ThreadLocal.withInitial(ApiKeyGenerator::new);
        final LongAdder clashCount = new LongAdder();

        IntStream.range(0, iterations)
                .parallel()
                .mapToObj(i -> {
                    final ApiKeyGenerator apiKeyGenerator = apiKeyGeneratorThreadLocal.get();
                    return apiKeyGenerator.generateRandomApiKey();
                })
                .forEach(apiKey -> {
                    final String hash = apiKeyService.computeApiKeyHash(apiKey, HashAlgorithm.SHA3_256);
                    if (hashes.contains(hash)) {
                        clashCount.increment();
                    }
                    hashes.add(hash);
                });

        LOGGER.info("clashCount: {}, hashes: {}", clashCount, hashes.size());
    }

    @Disabled // manual only, to see how many prefix clashes we get for 10mil api keys (answer: 46 ish)
    @Test
    void testPrefixClash() {
        final int iterations = 10_000_000;
        final Set<String> prefixes = new ConcurrentSkipListSet<>();

        final ThreadLocal<ApiKeyGenerator> apiKeyGeneratorThreadLocal = ThreadLocal.withInitial(ApiKeyGenerator::new);
        final LongAdder clashCount = new LongAdder();

        IntStream.range(0, iterations)
                .parallel()
                .forEach(i -> {
                    final ApiKeyGenerator apiKeyGenerator = apiKeyGeneratorThreadLocal.get();
                    final String apiKey = apiKeyGenerator.generateRandomApiKey();
                    final String prefix = ApiKeyGenerator.extractPrefixPart(apiKey);
                    if (prefixes.contains(prefix)) {
                        clashCount.increment();
                    }
                    prefixes.add(prefix);
                });

        LOGGER.info("clashCount: {}, prefixes: {}", clashCount, prefixes.size());
    }
}
